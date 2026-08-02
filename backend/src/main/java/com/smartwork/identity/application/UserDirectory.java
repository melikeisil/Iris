package com.smartwork.identity.application;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The read contract other modules use to look people up.
 *
 * <p>Deliberately narrow: it returns view records, never the {@code User} entity, so no
 * other module can mutate a person or accidentally hold a detached JPA graph.
 */
public interface UserDirectory {

    /** Includes the password hash, so this must only ever be called by the auth module. */
    Optional<AuthenticationView> findForAuthentication(String email);

    /**
     * Unscoped lookup by ID, for refresh-token exchange only: a refresh token proves
     * identity but does not carry an organization, so the tenant cannot be supplied.
     * Every other read requires an organization.
     */
    Optional<AuthenticationView> findForAuthentication(UUID userId);

    Optional<UserSummary> findSummary(UUID organizationId, UUID userId);

    Set<UUID> filterExisting(UUID organizationId, Set<UUID> userIds);

    void recordSuccessfulLogin(UUID userId);

    record AuthenticationView(
            UUID userId,
            UUID organizationId,
            UUID departmentId,
            String email,
            String fullName,
            String passwordHash,
            Set<String> roles,
            String employeeLevel,
            boolean organizationWide,
            boolean active) {
    }

    record UserSummary(
            UUID userId,
            UUID organizationId,
            UUID departmentId,
            String email,
            String fullName,
            String employeeLevel,
            Set<String> roles,
            boolean organizationWide,
            boolean active) {
    }
}
