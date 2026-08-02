package com.smartwork.policy.domain;

import com.smartwork.workitem.domain.RiskLevel;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The configurable half of the auto-assign gate (docs/08 §5.1), snapshotted from the
 * {@code assignment_policies} row that was in force.
 *
 * <p>A value object rather than the entity: the engine must be replayable against a
 * historical policy version, and passing a mutable managed entity into a pure function
 * would make "the thresholds at decision time" mean "the thresholds now".
 *
 * <p>{@code policyId} and {@code policyVersion} are carried so the decision can record
 * exactly which row and revision produced it. Null policyId means the built-in defaults.
 */
public record PolicyThresholds(
        UUID policyId,
        Long policyVersion,
        BigDecimal minAutoAssignConfidence,
        BigDecimal maxAutoAssignWorkload,
        int maxAutoAssignHours,
        RiskLevel autoAssignMaxRisk,
        int minAutoAssignScore,
        int minScoreGap,
        boolean allowCrossDepartmentAuto) {

    /**
     * docs/08 §5.1 defaults. Conditions 13 and 14 — the score gap and the score floor —
     * are additions beyond the brief: without the gap, auto-assign fires on coin-flips;
     * without the floor, it fires on the best of a bad pool.
     */
    public static PolicyThresholds defaults() {
        return new PolicyThresholds(
                null, null,
                new BigDecimal("0.85"),
                new BigDecimal("0.80"),
                8,
                RiskLevel.LOW,
                60,
                5,
                false);
    }

    /** Confidence at or above the bar. Null confidence is treated as failing. */
    public boolean confidenceSufficient(BigDecimal confidence) {
        return confidence != null && confidence.compareTo(minAutoAssignConfidence) >= 0;
    }

    public boolean workloadAcceptable(BigDecimal workloadRatio) {
        return workloadRatio != null && workloadRatio.compareTo(maxAutoAssignWorkload) <= 0;
    }

    public boolean riskAcceptable(RiskLevel risk) {
        return risk != null && risk.ordinal() <= autoAssignMaxRisk.ordinal();
    }

    public boolean sizeAcceptable(BigDecimal estimatedHours) {
        return estimatedHours == null
                || estimatedHours.compareTo(BigDecimal.valueOf(maxAutoAssignHours)) <= 0;
    }
}
