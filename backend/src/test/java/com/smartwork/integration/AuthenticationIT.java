package com.smartwork.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartwork.identity.domain.SystemRole;
import com.smartwork.support.IntegrationTest;
import com.smartwork.support.PostgresContainerConfig;
import com.smartwork.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Import(PostgresContainerConfig.class)
@DisplayName("Authentication: login, refresh rotation, reuse detection")
class AuthenticationIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private TestFixtures fixtures;

    private String email;

    @BeforeEach
    void seedUser() {
        UUID organizationId = fixtures.organization("AUTHTEST");
        UUID departmentId = fixtures.department(organizationId, "ENGINEERING").id();
        email = "auth-" + UUID.randomUUID() + "@acme.io";
        fixtures.user(organizationId, email, Set.of(SystemRole.EMPLOYEE), departmentId);
    }

    @Test
    void loginReturnsATokenPairAndThePrincipal() {
        ResponseEntity<JsonNode> response = login(email, TestFixtures.PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("refreshToken").asText()).isNotBlank();
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("user").get("email").asText()).isEqualTo(email);
        assertThat(body.get("user").get("roles")).anyMatch(role -> role.asText().equals("EMPLOYEE"));
    }

    @Test
    void wrongPasswordIsRejectedWithAStableErrorCode() {
        ResponseEntity<JsonNode> response = login(email, "TheWrongPassword123!");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void unknownEmailReturnsTheSameErrorAsAWrongPassword() {
        ResponseEntity<JsonNode> response = login("nobody-" + UUID.randomUUID() + "@acme.io", "whatever12345");

        // Identical code and status: the response must not reveal whether the account exists.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void meRequiresAnAccessToken() {
        ResponseEntity<JsonNode> anonymous = http.getForEntity("/api/v1/auth/me", JsonNode.class);

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.getBody().get("code").asText()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void meReturnsScopeAndCapabilities() {
        String accessToken = login(email, TestFixtures.PASSWORD).getBody().get("accessToken").asText();

        ResponseEntity<JsonNode> response = http.exchange(
                "/api/v1/auth/me", HttpMethod.GET, bearer(accessToken), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("email").asText()).isEqualTo(email);
        assertThat(body.get("department").get("code").asText()).isEqualTo("ENGINEERING");
        assertThat(body.get("capabilities")).anyMatch(c -> c.asText().equals("work_item.create"));
        // An EMPLOYEE must not receive team-lead or admin affordances.
        assertThat(body.get("capabilities")).noneMatch(c -> c.asText().startsWith("admin."));
        assertThat(body.get("capabilities")).noneMatch(c -> c.asText().startsWith("team."));
    }

    @Test
    void refreshRotatesTheTokenAndIssuesANewAccessToken() {
        JsonNode first = login(email, TestFixtures.PASSWORD).getBody();
        String originalRefresh = first.get("refreshToken").asText();

        ResponseEntity<JsonNode> refreshed = refresh(originalRefresh);

        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshed.getBody().get("refreshToken").asText())
                .as("refresh tokens are single-use, so the successor must differ")
                .isNotEqualTo(originalRefresh);
        assertThat(refreshed.getBody().get("accessToken").asText()).isNotBlank();
    }

    @Test
    void reusingAConsumedRefreshTokenRevokesTheWholeFamily() {
        String originalRefresh = login(email, TestFixtures.PASSWORD).getBody().get("refreshToken").asText();
        String successor = refresh(originalRefresh).getBody().get("refreshToken").asText();

        // Replay the already-consumed token: either a replay attack or a stolen token
        // racing the legitimate client. Both must kill the family.
        ResponseEntity<JsonNode> reuse = refresh(originalRefresh);

        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reuse.getBody().get("code").asText()).isEqualTo("REFRESH_TOKEN_REUSED");

        // The legitimate successor must now be dead too - that is the point of family revocation.
        assertThat(refresh(successor).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRevokesTheRefreshTokenFamily() {
        String refreshToken = login(email, TestFixtures.PASSWORD).getBody().get("refreshToken").asText();

        ResponseEntity<Void> logout = http.postForEntity("/api/v1/auth/logout",
                json("{\"refreshToken\":\"" + refreshToken + "\"}"), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(refresh(refreshToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aMalformedTokenIsRejectedAsProblemJson() {
        ResponseEntity<JsonNode> response = http.exchange(
                "/api/v1/auth/me", HttpMethod.GET, bearer("not.a.jwt"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(response.getBody().has("correlationId")).isTrue();
    }

    @Test
    void everyResponseCarriesACorrelationId() {
        ResponseEntity<JsonNode> response = login(email, TestFixtures.PASSWORD);

        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isNotBlank();
    }

    // ---- helpers ----

    private ResponseEntity<JsonNode> login(String userEmail, String password) {
        return http.postForEntity("/api/v1/auth/login",
                json("{\"email\":\"" + userEmail + "\",\"password\":\"" + password + "\"}"),
                JsonNode.class);
    }

    private ResponseEntity<JsonNode> refresh(String refreshToken) {
        return http.postForEntity("/api/v1/auth/refresh",
                json("{\"refreshToken\":\"" + refreshToken + "\"}"), JsonNode.class);
    }

    private HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Void> bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }
}
