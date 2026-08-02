package com.smartwork.auth.infrastructure;

import com.smartwork.common.error.ApiException;
import com.smartwork.common.security.AuthPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Access token issue and verify")
class JwtServiceTest {

    private static final String SECRET =
            "unit-test-secret-with-enough-entropy-for-hs512-abcdefghijklmnopqrstuvwxyz0123";

    private final Instant now = Instant.parse("2026-08-01T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final JwtService jwtService = new JwtService(properties(SECRET), clock);

    private final AuthPrincipal principal = new AuthPrincipal(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            Set.of(UUID.randomUUID()), Set.of("EMPLOYEE", "TEAM_LEAD"), "SENIOR", false);

    @Test
    void roundTripsEveryClaimNeededForAuthorization() {
        String token = jwtService.issueAccessToken(principal).token();
        AuthPrincipal verified = jwtService.verify(token);

        assertThat(verified.userId()).isEqualTo(principal.userId());
        assertThat(verified.organizationId()).isEqualTo(principal.organizationId());
        assertThat(verified.departmentId()).isEqualTo(principal.departmentId());
        assertThat(verified.teamIds()).isEqualTo(principal.teamIds());
        assertThat(verified.roles()).containsExactlyInAnyOrderElementsOf(principal.roles());
        assertThat(verified.employeeLevel()).isEqualTo("SENIOR");
    }

    @Test
    void carriesNoPersonalDataBeyondTheUserId() {
        // A leaked access token should not also leak an identity.
        String token = jwtService.issueAccessToken(principal).token();
        String payload = new String(java.util.Base64.getUrlDecoder()
                .decode(token.split("\\.")[1]));

        assertThat(payload).doesNotContainIgnoringCase("email");
        assertThat(payload).doesNotContainIgnoringCase("name");
        assertThat(payload).doesNotContain("@");
    }

    @Test
    void rejectsATokenSignedWithAnotherKey() {
        String foreign = new JwtService(
                properties("a-completely-different-secret-of-sufficient-length-0123456789abcdefgh"),
                clock).issueAccessToken(principal).token();

        assertThatThrownBy(() -> jwtService.verify(foreign))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode().name()).isEqualTo("UNAUTHENTICATED"));
    }

    @Test
    void reportsExpiryDistinctlyFromInvalidity() {
        String token = jwtService.issueAccessToken(principal).token();

        // A client should silently refresh on TOKEN_EXPIRED but re-authenticate on
        // UNAUTHENTICATED, so the two must not be collapsed.
        JwtService later = new JwtService(properties(SECRET),
                Clock.fixed(now.plus(Duration.ofHours(2)), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.verify(token))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode().name()).isEqualTo("TOKEN_EXPIRED"));
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> jwtService.verify("not-a-token")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> jwtService.verify("")).isInstanceOf(ApiException.class);
    }

    private static SecurityProperties properties(String secret) {
        return new SecurityProperties(
                new SecurityProperties.Jwt(secret, "smartwork-ai",
                        Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofSeconds(30)),
                new SecurityProperties.Cors(java.util.List.of("http://localhost:5173")));
    }
}
