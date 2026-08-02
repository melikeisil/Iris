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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Import(PostgresContainerConfig.class)
@DisplayName("Role-based access to the admin surface")
class AdminAuthorizationIT {

    @Autowired private TestRestTemplate http;
    @Autowired private TestFixtures fixtures;

    private String employeeToken;
    private String adminToken;

    @BeforeEach
    void seedUsers() {
        UUID organizationId = fixtures.organization("ADMINTEST");
        UUID departmentId = fixtures.department(organizationId, "IT").id();

        String employeeEmail = "emp-" + UUID.randomUUID() + "@acme.io";
        String adminEmail = "adm-" + UUID.randomUUID() + "@acme.io";
        fixtures.user(organizationId, employeeEmail, Set.of(SystemRole.EMPLOYEE), departmentId);
        fixtures.user(organizationId, adminEmail, Set.of(SystemRole.ADMIN), departmentId);

        employeeToken = tokenFor(employeeEmail);
        adminToken = tokenFor(adminEmail);
    }

    @Test
    void anEmployeeCannotReachTheAdminSurface() {
        ResponseEntity<JsonNode> response = http.exchange(
                "/api/v1/admin/users", HttpMethod.GET, bearer(employeeToken), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code").asText()).isEqualTo("FORBIDDEN");
    }

    @Test
    void anAdminCanListUsers() {
        ResponseEntity<JsonNode> response = http.exchange(
                "/api/v1/admin/users", HttpMethod.GET, bearer(adminToken), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("content")).isNotNull();
        assertThat(response.getBody().get("page").get("totalElements").asInt()).isGreaterThan(0);
    }

    @Test
    void anEmployeeCannotReachTheManagerOrTeamLeadSurfaces() {
        assertThat(http.exchange("/api/v1/manager/dashboard", HttpMethod.GET,
                bearer(employeeToken), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(http.exchange("/api/v1/team-lead/dashboard", HttpMethod.GET,
                bearer(employeeToken), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminEndpointsRejectAnonymousCallers() {
        ResponseEntity<JsonNode> response = http.getForEntity("/api/v1/admin/users", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code").asText()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void validationFailuresReportTheOffendingField() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String invalid = """
                {"code":"","name":"No code supplied"}
                """;

        ResponseEntity<JsonNode> response = http.exchange("/api/v1/admin/departments",
                HttpMethod.POST, new HttpEntity<>(invalid, headers), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().get("errors")).anyMatch(e -> e.get("field").asText().equals("code"));
    }

    private String tokenFor(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + TestFixtures.PASSWORD + "\"}";
        return http.postForEntity("/api/v1/auth/login", new HttpEntity<>(body, headers), JsonNode.class)
                .getBody().get("accessToken").asText();
    }

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }
}
