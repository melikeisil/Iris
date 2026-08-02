package com.smartwork.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The authenticated caller, reconstructed from the access token without a database read.
 *
 * <p>Lives in {@code common} rather than {@code auth} because every module's controllers
 * and access evaluators need it; putting it in auth would make auth an internal
 * dependency of the entire system.
 *
 * <p>Carries only what authorization needs. No PII beyond the user ID: access tokens pass
 * through clients and proxies, and a leaked token should not also leak an identity.
 */
public record AuthPrincipal(
        UUID userId,
        UUID organizationId,
        UUID departmentId,
        Set<UUID> teamIds,
        Set<String> roles,
        String employeeLevel,
        boolean organizationWide) {

    public Collection<? extends GrantedAuthority> authorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean isManager() {
        return hasRole("MANAGER");
    }

    public boolean isMemberOf(UUID teamId) {
        return teamIds.contains(teamId);
    }

    /** Departments this caller may see. Organization-wide managers are handled separately. */
    public List<UUID> visibleDepartments() {
        return departmentId == null ? List.of() : List.of(departmentId);
    }
}
