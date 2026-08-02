package com.smartwork.assignment.domain;

import com.smartwork.common.domain.TenantEntity;
import com.smartwork.identity.domain.EmployeeLevel;
import com.smartwork.workitem.domain.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.UUID;

/** Persisted, per-organization override of {@link LevelRestrictions}. */
@Entity
@Table(name = "employee_level_policies")
public class EmployeeLevelPolicy extends TenantEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "employee_level", nullable = false, updatable = false, length = 16)
    private EmployeeLevel employeeLevel;

    @Column(name = "allow_production_access", nullable = false)
    private boolean allowProductionAccess = true;

    @Column(name = "allow_financial_approval", nullable = false)
    private boolean allowFinancialApproval = true;

    @Column(name = "allow_security_sensitive", nullable = false)
    private boolean allowSecuritySensitive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "max_priority", nullable = false, length = 16)
    private Priority maxPriority = Priority.CRITICAL;

    @Column(name = "max_active_work_items")
    private Integer maxActiveWorkItems;

    @Column(name = "max_estimated_hours", precision = 6, scale = 2)
    private BigDecimal maxEstimatedHours;

    @Column(name = "require_mentor", nullable = false)
    private boolean requireMentor;

    @Column(name = "allowed_categories", length = 512)
    private String allowedCategories;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected EmployeeLevelPolicy() {
    }

    public EmployeeLevelPolicy(UUID organizationId, EmployeeLevel employeeLevel) {
        super(organizationId);
        this.employeeLevel = employeeLevel;
    }

    /** Converts the row into the value object the pure filter consumes. */
    public LevelRestrictions toRestrictions() {
        return new LevelRestrictions(
                employeeLevel,
                allowProductionAccess,
                allowFinancialApproval,
                allowSecuritySensitive,
                maxPriority,
                maxActiveWorkItems,
                maxEstimatedHours,
                requireMentor,
                LevelRestrictions.parseCategories(allowedCategories));
    }

    public EmployeeLevel getEmployeeLevel() {
        return employeeLevel;
    }

    public void apply(LevelRestrictions restrictions) {
        this.allowProductionAccess = restrictions.allowProductionAccess();
        this.allowFinancialApproval = restrictions.allowFinancialApproval();
        this.allowSecuritySensitive = restrictions.allowSecuritySensitive();
        this.maxPriority = restrictions.maxPriority();
        this.maxActiveWorkItems = restrictions.maxActiveWorkItems();
        this.maxEstimatedHours = restrictions.maxEstimatedHours();
        this.requireMentor = restrictions.requireMentor();
        this.allowedCategories = restrictions.allowedCategories().isEmpty()
                ? null
                : String.join(",", restrictions.allowedCategories());
    }
}
