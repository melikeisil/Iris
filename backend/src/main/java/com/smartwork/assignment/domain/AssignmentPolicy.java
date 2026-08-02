package com.smartwork.assignment.domain;

import com.smartwork.common.domain.TenantEntity;
import com.smartwork.workitem.domain.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Routing thresholds and score weights for one scope.
 *
 * <p>Phase 5 reads {@code weightOverrides}; the threshold columns are consumed by the
 * policy engine in Phase 6. They live together because they are edited together and
 * versioned together — replaying a historical routing decision needs the thresholds *and*
 * the weights that were in force at the time, and splitting them across two tables would
 * make that replay a join with two independent version histories.
 */
@Entity
@Table(name = "assignment_policies")
public class AssignmentPolicy extends TenantEntity {

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "priority", nullable = false)
    private short priority = 100;

    @Column(name = "min_auto_assign_confidence", nullable = false, precision = 3, scale = 2)
    private BigDecimal minAutoAssignConfidence = new BigDecimal("0.85");

    @Column(name = "max_auto_assign_workload", nullable = false, precision = 3, scale = 2)
    private BigDecimal maxAutoAssignWorkload = new BigDecimal("0.80");

    @Column(name = "max_auto_assign_hours", nullable = false)
    private short maxAutoAssignHours = 8;

    @Enumerated(EnumType.STRING)
    @Column(name = "auto_assign_max_risk", nullable = false, length = 16)
    private RiskLevel autoAssignMaxRisk = RiskLevel.LOW;

    @Column(name = "min_auto_assign_score", nullable = false)
    private short minAutoAssignScore = 60;

    @Column(name = "min_score_gap", nullable = false)
    private short minScoreGap = 5;

    @Column(name = "allow_cross_department_auto", nullable = false)
    private boolean allowCrossDepartmentAuto;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weight_overrides")
    private Map<String, BigDecimal> weightOverrides;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "route_rules")
    private Map<String, Object> routeRules;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AssignmentPolicy() {
    }

    public AssignmentPolicy(UUID organizationId, String name) {
        super(organizationId);
        this.name = name;
    }

    /**
     * Specificity for the resolution ladder in docs/08 §8: higher wins.
     * team (2) beats department (1) beats organization (0).
     */
    public int specificity() {
        if (teamId != null) {
            return 2;
        }
        return departmentId != null ? 1 : 0;
    }

    /** Throws if the stored overrides do not sum to 1.0 — validated on read as well as on save. */
    public ScoringWeights resolveWeights() {
        return ScoringWeights.of(weightOverrides);
    }

    public boolean appliesTo(UUID candidateDepartmentId, UUID candidateTeamId) {
        if (teamId != null) {
            return teamId.equals(candidateTeamId);
        }
        if (departmentId != null) {
            return departmentId.equals(candidateDepartmentId);
        }
        return true;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public void setTeamId(UUID teamId) {
        this.teamId = teamId;
    }

    public String getName() {
        return name;
    }

    public short getPriority() {
        return priority;
    }

    public BigDecimal getMinAutoAssignConfidence() {
        return minAutoAssignConfidence;
    }

    public BigDecimal getMaxAutoAssignWorkload() {
        return maxAutoAssignWorkload;
    }

    public short getMaxAutoAssignHours() {
        return maxAutoAssignHours;
    }

    public RiskLevel getAutoAssignMaxRisk() {
        return autoAssignMaxRisk;
    }

    public short getMinAutoAssignScore() {
        return minAutoAssignScore;
    }

    public short getMinScoreGap() {
        return minScoreGap;
    }

    public boolean isAllowCrossDepartmentAuto() {
        return allowCrossDepartmentAuto;
    }

    public Map<String, BigDecimal> getWeightOverrides() {
        return weightOverrides;
    }

    public void setWeightOverrides(Map<String, BigDecimal> weightOverrides) {
        // Validate eagerly: a policy that cannot produce usable weights must not be saved.
        ScoringWeights.of(weightOverrides);
        this.weightOverrides = weightOverrides;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Exposed so a routing decision can pin the exact revision it was made under.
     * Replaying against "the policy in force at the time" needs the row <em>and</em> its
     * version, since the row is mutable — see ADR-037.
     */
    public long getVersion() {
        return version;
    }
}
