package com.smartwork.identity.application;

import com.smartwork.common.error.ApiException;
import com.smartwork.identity.domain.EmployeeLevel;
import com.smartwork.identity.domain.Role;
import com.smartwork.identity.domain.SystemRole;
import com.smartwork.identity.domain.User;
import com.smartwork.identity.infrastructure.RoleRepository;
import com.smartwork.identity.infrastructure.UserRepository;
import com.smartwork.organization.application.DepartmentDirectory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;

/** Administrative user management. Operational assignment lives elsewhere by design. */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final DepartmentDirectory departments;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(UserRepository users, RoleRepository roles, DepartmentDirectory departments,
                       PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.departments = departments;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public Page<UserDirectory.UserSummary> search(UUID organizationId, UUID departmentId,
                                                  boolean activeOnly, String query, Pageable pageable) {
        return users.search(organizationId, departmentId, activeOnly,
                        query == null || query.isBlank() ? null : query.trim(), pageable)
                .map(UserDirectoryService::toSummary);
    }

    public UserDirectory.UserSummary get(UUID organizationId, UUID userId) {
        return users.findByIdInOrganization(userId, organizationId)
                .map(UserDirectoryService::toSummary)
                .orElseThrow(() -> ApiException.notFound("User", userId));
    }

    @Transactional
    public UserDirectory.UserSummary create(UUID organizationId, CreateUserCommand command) {
        String email = User.normalizeEmail(command.email());
        if (users.existsByOrganizationIdAndEmail(organizationId, email)) {
            throw ApiException.duplicate("A user with email '" + email + "' already exists.");
        }
        if (command.departmentId() != null) {
            departments.require(organizationId, command.departmentId());
        }

        User user = new User(organizationId, email,
                passwordEncoder.encode(command.password()), command.fullName());
        user.updateProfile(command.fullName(), command.employeeLevel(), command.weeklyCapacityHours(),
                command.departmentId(), command.organizationWide());

        // Everyone is an EMPLOYEE. Additional roles are additive, never a replacement.
        Set<SystemRole> requested = command.roles().isEmpty()
                ? Set.of(SystemRole.EMPLOYEE)
                : command.roles();
        grantAll(user, requested, command.grantedBy());
        if (!user.hasRole(SystemRole.EMPLOYEE) && !user.hasRole(SystemRole.ADMIN)) {
            grantAll(user, Set.of(SystemRole.EMPLOYEE), command.grantedBy());
        }

        return UserDirectoryService.toSummary(users.save(user));
    }

    @Transactional
    public UserDirectory.UserSummary update(UUID organizationId, UUID userId, UpdateUserCommand command) {
        User user = requireUser(organizationId, userId);
        if (command.departmentId() != null) {
            departments.require(organizationId, command.departmentId());
        }
        user.updateProfile(command.fullName(), command.employeeLevel(), command.weeklyCapacityHours(),
                command.departmentId(), command.organizationWide());
        user.setActive(command.active());
        return UserDirectoryService.toSummary(user);
    }

    @Transactional
    public UserDirectory.UserSummary replaceRoles(UUID organizationId, UUID userId,
                                                  Set<SystemRole> newRoles, UUID grantedBy) {
        User user = requireUser(organizationId, userId);
        user.roleCodes().forEach(user::revoke);
        grantAll(user, newRoles.isEmpty() ? Set.of(SystemRole.EMPLOYEE) : newRoles, grantedBy);
        return UserDirectoryService.toSummary(user);
    }

    @Transactional
    public void resetPassword(UUID organizationId, UUID userId, String newPassword) {
        requireUser(organizationId, userId).changePassword(passwordEncoder.encode(newPassword));
    }

    /** Soft delete: audit rows and past assignments must keep resolving to a real person. */
    @Transactional
    public void deactivate(UUID organizationId, UUID userId) {
        requireUser(organizationId, userId).softDelete(clock.instant());
    }

    private void grantAll(User user, Set<SystemRole> roleCodes, UUID grantedBy) {
        for (SystemRole code : roleCodes) {
            Role role = roles.findByCode(code)
                    .orElseThrow(() -> ApiException.notFound("Role", code.name()));
            user.grant(role, grantedBy);
        }
    }

    private User requireUser(UUID organizationId, UUID userId) {
        return users.findByIdInOrganization(userId, organizationId)
                .orElseThrow(() -> ApiException.notFound("User", userId));
    }

    public record CreateUserCommand(String email, String password, String fullName,
                                    EmployeeLevel employeeLevel, BigDecimal weeklyCapacityHours,
                                    UUID departmentId, boolean organizationWide,
                                    Set<SystemRole> roles, UUID grantedBy) {
    }

    public record UpdateUserCommand(String fullName, EmployeeLevel employeeLevel,
                                    BigDecimal weeklyCapacityHours, UUID departmentId,
                                    boolean organizationWide, boolean active) {
    }
}
