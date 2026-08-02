package com.smartwork.auth.infrastructure;

import com.smartwork.common.domain.Ids;
import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import com.smartwork.common.security.AuthPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Issues and verifies access tokens.
 *
 * <p>HS512 with a symmetric key is appropriate while one service both signs and verifies.
 * ADR-021 records the move to RS256 once the MCP adapter or another service needs to
 * verify tokens independently.
 */
@Service
public class JwtService {

    private static final String CLAIM_ORGANIZATION = "org";
    private static final String CLAIM_DEPARTMENT = "dept";
    private static final String CLAIM_TEAMS = "teams";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_LEVEL = "lvl";
    private static final String CLAIM_ORG_WIDE = "orgWide";

    private final SecretKey key;
    private final SecurityProperties properties;
    private final Clock clock;

    public JwtService(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;

        // Fail here, with a message that says what to do, rather than letting the JWT
        // library raise an opaque key-length error at bean construction.
        String secret = properties.jwt().secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set. Generate one with: openssl rand -base64 64");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 64) {
            throw new IllegalStateException(
                    "JWT_SECRET is %d bytes; HS512 requires at least 64.".formatted(keyBytes.length));
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public IssuedToken issueAccessToken(AuthPrincipal principal) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.jwt().accessTokenTtl());

        String token = Jwts.builder()
                .id(Ids.newId().toString())
                .issuer(properties.jwt().issuer())
                .subject(principal.userId().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_ORGANIZATION, principal.organizationId().toString())
                .claim(CLAIM_DEPARTMENT, principal.departmentId() == null
                        ? null : principal.departmentId().toString())
                .claim(CLAIM_TEAMS, principal.teamIds().stream().map(UUID::toString).toList())
                .claim(CLAIM_ROLES, List.copyOf(principal.roles()))
                .claim(CLAIM_LEVEL, principal.employeeLevel())
                .claim(CLAIM_ORG_WIDE, principal.organizationWide())
                .signWith(key)
                .compact();

        return new IssuedToken(token, expiresAt, properties.jwt().accessTokenTtl().toSeconds());
    }

    /**
     * @throws ApiException with {@code TOKEN_EXPIRED} or {@code UNAUTHENTICATED}. The two
     *         are distinguished because a client should silently refresh on the first and
     *         re-authenticate on the second.
     */
    public AuthPrincipal verify(String token) {
        try {
            io.jsonwebtoken.Clock jwtClock = () -> Date.from(clock.instant());
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.jwt().issuer())
                    .clock(jwtClock)
                    .clockSkewSeconds(properties.jwt().clockSkew().toSeconds())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new AuthPrincipal(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get(CLAIM_ORGANIZATION, String.class)),
                    uuidOrNull(claims.get(CLAIM_DEPARTMENT, String.class)),
                    toUuidSet(claims.get(CLAIM_TEAMS, List.class)),
                    toStringSet(claims.get(CLAIM_ROLES, List.class)),
                    claims.get(CLAIM_LEVEL, String.class),
                    Boolean.TRUE.equals(claims.get(CLAIM_ORG_WIDE, Boolean.class)));

        } catch (ExpiredJwtException expired) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, "The access token has expired.");
        } catch (JwtException | IllegalArgumentException invalid) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "The access token is not valid.");
        }
    }

    private static UUID uuidOrNull(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    @SuppressWarnings("unchecked")
    private static Set<UUID> toUuidSet(List<?> raw) {
        if (raw == null) {
            return Set.of();
        }
        return ((List<String>) raw).stream().map(UUID::fromString)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> toStringSet(List<?> raw) {
        if (raw == null) {
            return Set.of();
        }
        return new LinkedHashSet<>((List<String>) raw);
    }

    public record IssuedToken(String token, Instant expiresAt, long expiresInSeconds) {
    }
}
