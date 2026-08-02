package com.smartwork.assignment.domain;

import com.smartwork.workitem.domain.WorkItemType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Fallback effort estimates when {@code estimated_hours} is missing (docs/08 §6).
 *
 * <p>The point of a fallback table is that an unestimated item is not weightless. Treating
 * a missing estimate as zero hours is how a person ends up looking available while holding
 * eleven unestimated tasks.
 */
public final class EffortEstimates {

    /** Story points to hours, when a story carries points but no hour estimate. */
    public static final BigDecimal DEFAULT_HOURS_PER_POINT = new BigDecimal("2.5");

    private static final Map<WorkItemType, BigDecimal> FALLBACK_HOURS = Map.ofEntries(
            Map.entry(WorkItemType.SUBTASK, new BigDecimal("2")),
            Map.entry(WorkItemType.TASK, new BigDecimal("4")),
            Map.entry(WorkItemType.BUG, new BigDecimal("6")),
            Map.entry(WorkItemType.TEST, new BigDecimal("3")),
            Map.entry(WorkItemType.DOCUMENTATION, new BigDecimal("3")),
            Map.entry(WorkItemType.STORY, new BigDecimal("8")),
            Map.entry(WorkItemType.RESEARCH, new BigDecimal("8")),
            Map.entry(WorkItemType.TECHNICAL_DEBT, new BigDecimal("6")),

            // Not in the documented table, added here with a reason:
            // a ticket is triage-and-coordinate effort, an incident is heavier.
            Map.entry(WorkItemType.REQUEST, new BigDecimal("2")),
            Map.entry(WorkItemType.INCIDENT, new BigDecimal("4")),

            // Containers contribute nothing of their own. Their children are counted
            // individually, so giving an epic a fallback estimate would double-count the
            // same work - the single most likely way for a workload number to be wrong.
            Map.entry(WorkItemType.EPIC, BigDecimal.ZERO),
            Map.entry(WorkItemType.FEATURE, BigDecimal.ZERO));

    private EffortEstimates() {
    }

    /**
     * Resolves the effort an item represents, in order of preference:
     * explicit hours, then story points converted, then the type fallback.
     */
    public static BigDecimal resolve(
            WorkItemType type, BigDecimal explicitHours, Integer storyPoints, BigDecimal hoursPerPoint) {

        if (explicitHours != null && explicitHours.signum() > 0) {
            return explicitHours;
        }
        if (storyPoints != null && storyPoints > 0 && type.supportsStoryPoints()) {
            BigDecimal perPoint = hoursPerPoint != null ? hoursPerPoint : DEFAULT_HOURS_PER_POINT;
            return BigDecimal.valueOf(storyPoints).multiply(perPoint).setScale(2, RoundingMode.HALF_UP);
        }
        return FALLBACK_HOURS.getOrDefault(type, new BigDecimal("4"));
    }

    public static BigDecimal fallbackFor(WorkItemType type) {
        return FALLBACK_HOURS.getOrDefault(type, new BigDecimal("4"));
    }
}
