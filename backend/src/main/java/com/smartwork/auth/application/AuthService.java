package com.smartwork.auth.application;

import com.smartwork.auth.infrastructure.JwtService;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import com.smartwork.identity.application.UserDirectory;
import com.smartwork.team.application.TeamDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserDirectory users;
    private final TeamDirectory teams;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;

    public AuthService(UserDirectory users, TeamDirectory teams, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokens) {
        this.users = users;
        this.teams = teams;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
    }

    @Transactional
    public AuthResult login(String email, String rawPassword) {
        var account = users.findForAuthentication(email);

        if (account.isEmpty()) {
            // Burn a comparable amount of work so a missing account and a wrong password
            // are indistinguishable by response time. Argon2 dominates login latency, so
            // skipping it here would make user enumeration trivial with a stopwatch.
            passwordEncoder.encode(rawPassword);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect.");
        }

        UserDirectory.AuthenticationView user = account.get();
        if (!passwordEncoder.matches(rawPassword, user.passwordHash())) {
            log.info("Failed login for user {}", user.userId());
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect.");
        }
        if (!user.active()) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "This account is disabled.");
        }

        users.recordSuccessfulLogin(user.userId());

        AuthPrincipal principal = toPrincipal(user);
        JwtService.IssuedToken access = jwtService.issueAccessToken(principal);
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokens.issueNewFamily(user.userId());

        log.info("Login succeeded for user {} in organization {}", user.userId(), user.organizationId());
        return new AuthResult(access, refresh, principal, user.fullName(), user.email());
    }

    /**
     * Exchanges a refresh token for a new pair. The principal is rebuilt from current
     * data, not from the old token, so a role or team change takes effect within one
     * access-token lifetime rather than at the next login.
     */
    @Transactional
    public AuthResult refresh(String refreshToken) {
        RefreshTokenService.RotationResult rotation = refreshTokens.rotate(refreshToken);

        UserDirectory.AuthenticationView user = users.findForAuthentication(rotation.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "The account no longer exists."));

        if (!user.active()) {
            refreshTokens.revokeAllForUser(user.userId(), "ACCOUNT_DISABLED");
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "This account is disabled.");
        }

        AuthPrincipal principal = toPrincipal(user);
        JwtService.IssuedToken access = jwtService.issueAccessToken(principal);
        return new AuthResult(access, rotation.refreshToken(), principal, user.fullName(), user.email());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.revokeFamilyOf(refreshToken);
    }

    private AuthPrincipal toPrincipal(UserDirectory.AuthenticationView user) {
        Set<UUID> teamIds = teams.teamIdsOf(user.organizationId(), user.userId());
        return new AuthPrincipal(
                user.userId(), user.organizationId(), user.departmentId(), teamIds,
                user.roles(), user.employeeLevel(), user.organizationWide());
    }

    public record AuthResult(JwtService.IssuedToken accessToken,
                             RefreshTokenService.IssuedRefreshToken refreshToken,
                             AuthPrincipal principal,
                             String fullName,
                             String email) {
    }
}
