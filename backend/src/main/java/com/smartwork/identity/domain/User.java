package com.smartwork.identity.domain;

import com.smartwork.common.domain.TenantEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A person in the organization.
 *
 * <p>{@code departmentId} and role grants are plain UUIDs rather than JPA associations to
 * another module's entities: cross-module entity graphs would defeat the module
 * boundaries that {@code ModularityTest} enforces, and would make extracting a module
 * later an unwinding exercise.
 */
@Entity
@Table(name = "users")
public class User extends TenantEntity {

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "employee_level", nullable = false, length = 16)
    private EmployeeLevel employeeLevel = EmployeeLevel.MID;

    @Column(name = "weekly_capacity_hours", nullable = false, precision = 5, scale = 2)
    private BigDecimal weeklyCapacityHours = new BigDecimal("40.00");

    @Column(name = "organization_wide", nullable = false)
    private boolean organizationWide;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<UserRole> roles = new LinkedHashSet<>();

    protected User() {
    }

    public User(UUID organizationId, String email, String passwordHash, String fullName) {
        super(organizationId);
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.fullName = fullName;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public void grant(Role role, UUID grantedBy) {
        boolean alreadyHeld = roles.stream().anyMatch(userRole -> userRole.getRole().equals(role));
        if (!alreadyHeld) {
            roles.add(new UserRole(this, role, grantedBy));
        }
    }

    public void revoke(SystemRole roleCode) {
        roles.removeIf(userRole -> userRole.getRole().getCode() == roleCode);
    }

    public Set<SystemRole> roleCodes() {
        return roles.stream().map(userRole -> userRole.getRole().getCode())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean hasRole(SystemRole role) {
        return roleCodes().contains(role);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Soft delete: audit rows and past assignments must keep referring to a real person. */
    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.active = false;
    }

    public void recordLogin(Instant now) {
        this.lastLoginAt = now;
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
    }

    public void updateProfile(String fullName, EmployeeLevel level, BigDecimal weeklyCapacityHours,
                              UUID departmentId, boolean organizationWide) {
        this.fullName = fullName;
        this.employeeLevel = level;
        this.weeklyCapacityHours = weeklyCapacityHours;
        this.departmentId = departmentId;
        this.organizationWide = organizationWide;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public EmployeeLevel getEmployeeLevel() {
        return employeeLevel;
    }

    public BigDecimal getWeeklyCapacityHours() {
        return weeklyCapacityHours;
    }

    public boolean isOrganizationWide() {
        return organizationWide;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersion() {
        return version;
    }

    public Set<UserRole> getRoles() {
        return Set.copyOf(roles);
    }
}
