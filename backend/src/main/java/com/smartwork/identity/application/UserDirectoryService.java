package com.smartwork.identity.application;

import com.smartwork.identity.domain.User;
import com.smartwork.identity.infrastructure.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserDirectoryService implements UserDirectory {

    private final UserRepository users;
    private final Clock clock;

    public UserDirectoryService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    public Optional<AuthenticationView> findForAuthentication(String email) {
        return users.findActiveByEmail(User.normalizeEmail(email)).map(UserDirectoryService::toAuthenticationView);
    }

    @Override
    public Optional<AuthenticationView> findForAuthentication(UUID userId) {
        return users.findById(userId)
                .filter(user -> !user.isDeleted())
                .map(UserDirectoryService::toAuthenticationView);
    }

    private static AuthenticationView toAuthenticationView(User user) {
        return new AuthenticationView(
                user.getId(),
                user.getOrganizationId(),
                user.getDepartmentId(),
                user.getEmail(),
                user.getFullName(),
                user.getPasswordHash(),
                roleNames(user),
                user.getEmployeeLevel().name(),
                user.isOrganizationWide(),
                user.isActive());
    }

    @Override
    public Optional<UserSummary> findSummary(UUID organizationId, UUID userId) {
        return users.findByIdInOrganization(userId, organizationId).map(UserDirectoryService::toSummary);
    }

    @Override
    public Set<UUID> filterExisting(UUID organizationId, Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return users.findAllInOrganization(organizationId, List.copyOf(userIds)).stream()
                .map(User::getId)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void recordSuccessfulLogin(UUID userId) {
        users.findById(userId).ifPresent(user -> user.recordLogin(clock.instant()));
    }

    static UserSummary toSummary(User user) {
        return new UserSummary(
                user.getId(),
                user.getOrganizationId(),
                user.getDepartmentId(),
                user.getEmail(),
                user.getFullName(),
                user.getEmployeeLevel().name(),
                roleNames(user),
                user.isOrganizationWide(),
                user.isActive());
    }

    private static Set<String> roleNames(User user) {
        return user.roleCodes().stream().map(Enum::name).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
