package com.smartwork.auth.application;

import com.smartwork.auth.domain.RefreshToken;
import com.smartwork.auth.infrastructure.RefreshTokenRepository;
import com.smartwork.auth.infrastructure.SecurityProperties;
import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Rotating, single-use refresh tokens with reuse detection.
 *
 * <p>The token itself is a 256-bit random value; only its SHA-256 hash is stored, so a
 * database leak does not yield usable tokens. Every refresh consumes the presented token
 * and issues a new one in the same family. Presenting a token that has already been
 * consumed means either a replay or a stolen token racing the legitimate client - both
 * warrant killing the family, which turns silent theft into an audited event.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository tokens;
    private final SecurityProperties properties;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository tokens, SecurityProperties properties, Clock clock) {
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public IssuedRefreshToken issueNewFamily(UUID userId) {
        return issue(userId, UUID.randomUUID());
    }

    /**
     * Consumes {@code presentedToken} and issues its successor.
     *
     * @throws ApiException REFRESH_TOKEN_REUSED when the presented token was already
     *         consumed - the whole family is revoked before throwing.
     */
    @Transactional
    public RotationResult rotate(String presentedToken) {
        String hash = hash(presentedToken);
        Instant now = clock.instant();

        RefreshToken existing = tokens.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED,
                        "The refresh token is not valid."));

        if (existing.getConsumedAt() != null) {
            int revoked = tokens.revokeFamily(existing.getFamilyId(), now, "REUSE_DETECTED");
            log.warn("Refresh token reuse detected for user {}; revoked {} tokens in family {}",
                    existing.getUserId(), revoked, existing.getFamilyId());
            throw new ApiException(ErrorCode.REFRESH_TOKEN_REUSED,
                    "This refresh token has already been used. All sessions have been ended; sign in again.");
        }

        if (!existing.isUsable(now)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED,
                    "The refresh token has expired or been revoked.");
        }

        existing.consume(now);
        IssuedRefreshToken successor = issue(existing.getUserId(), existing.getFamilyId());
        return new RotationResult(existing.getUserId(), successor);
    }

    @Transactional
    public void revokeFamilyOf(String presentedToken) {
        tokens.findByTokenHash(hash(presentedToken)).ifPresent(token ->
                tokens.revokeFamily(token.getFamilyId(), clock.instant(), "LOGOUT"));
    }

    @Transactional
    public void revokeAllForUser(UUID userId, String reason) {
        tokens.revokeAllForUser(userId, clock.instant(), reason);
    }

    /** Housekeeping for expired rows. Wired to a schedule in a later phase. */
    @Transactional
    public int purgeExpired() {
        return tokens.deleteExpiredBefore(clock.instant());
    }

    private IssuedRefreshToken issue(UUID userId, UUID familyId) {
        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.jwt().refreshTokenTtl());
        tokens.save(new RefreshToken(userId, familyId, hash(token), now, expiresAt));

        return new IssuedRefreshToken(token, expiresAt);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the JDK specification", impossible);
        }
    }

    public record IssuedRefreshToken(String token, Instant expiresAt) {
    }

    public record RotationResult(UUID userId, IssuedRefreshToken refreshToken) {
    }
}
