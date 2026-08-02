package com.smartwork.assignment.domain;

import com.smartwork.identity.domain.EmployeeLevel;
import com.smartwork.workitem.domain.Priority;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Employee level restrictions (docs/08 §7), as a value object with built-in defaults.
 *
 * <p>Two things worth stating explicitly, because both are easy to get wrong:
 *
 * <p><b>This is a deterministic backend rule, never a prompt instruction.</b> An LLM told
 * "do not assign interns to production work" will comply most of the time, and "most of
 * the time" is not an authorization model.
 *
 * <p><b>It is enforced in two places on purpose.</b> Here, during candidate filtering, so a
 * restricted person never appears as a recommendation; and again in the
 * {@code LEVEL_RESTRICTION} transition guard, so a Team Lead manually assigning a
 * restricted task is stopped too. Filtering alone would be advisory.
 */
public record LevelRestrictions(
        EmployeeLevel level,
        boolean allowProductionAccess,
        boolean allowFinancialApproval,
        boolean allowSecuritySensitive,
        Priority maxPriority,
        Integer maxActiveWorkItems,
        BigDecimal maxEstimatedHours,
        boolean requireMentor,
        Set<String> allowedCategories) {

    /** Defaults from docs/08 §7, used when an organization has no row for the level. */
    public static LevelRestrictions defaultsFor(EmployeeLevel level) {
        return switch (level) {
            case INTERN -> new LevelRestrictions(
                    level, false, false, false, Priority.HIGH, 3, new BigDecimal("8"), true, Set.of());
            case JUNIOR -> new LevelRestrictions(
                    level, false, true, true, Priority.HIGH, 6, new BigDecimal("16"), false, Set.of());
            case MID -> new LevelRestrictions(
                    level, true, false, true, Priority.CRITICAL, null, null, false, Set.of());
            case SENIOR, LEAD -> new LevelRestrictions(
                    level, true, true, true, Priority.CRITICAL, null, null, false, Set.of());
        };
    }

    /**
     * Evaluates every restriction against one item. Returns the first violation, or empty
     * when the candidate is permitted.
     *
     * <p>Ordered most-severe-first so the reason surfaced to a reviewer is the one that
     * matters, not merely the one that happened to be checked first.
     */
    public java.util.Optional<ExclusionReason> violationFor(
            CandidateFeatures.WorkItemContext item, int currentActiveCount, boolean hasSeniorReviewer) {

        if (item.productionImpacting() && !allowProductionAccess) {
            return java.util.Optional.of(ExclusionReason.LEVEL_RESTRICTION_PRODUCTION_ACCESS);
        }
        if (item.securitySensitive() && !allowSecuritySensitive) {
            return java.util.Optional.of(ExclusionReason.LEVEL_RESTRICTION_SECURITY);
        }
        if (item.budgetSensitive() && !allowFinancialApproval) {
            return java.util.Optional.of(ExclusionReason.LEVEL_RESTRICTION_FINANCIAL);
        }
        // Strictly above the ceiling. An INTERN capped at HIGH may take HIGH work; the
        // restriction exists to keep CRITICAL off their plate, not to demote them a rung.
        if (item.priority() != null && maxPriority != null
                && item.priority().ordinal() > maxPriority.ordinal()) {
            return java.util.Optional.of(ExclusionReason.LEVEL_RESTRICTION_PRIORITY);
        }
        if (maxActiveWorkItems != null && currentActiveCount >= maxActiveWorkItems) {
            return java.util.Optional.of(ExclusionReason.LEVEL_RESTRICTION_MAX_ACTIVE);
        }
        if (maxEstimatedHours != null && item.estimatedHours() != null
                && item.estimatedHours().compareTo(maxEstimatedHours) > 0) {
            return java.util.Optional.of(ExclusionReason.LEVEL_RESTRICTION_MAX_HOURS);
        }
        if (requireMentor && !hasSeniorReviewer) {
            return java.util.Optional.of(ExclusionReason.LEVEL_RESTRICTION_MENTOR_REQUIRED);
        }
        if (!allowedCategories.isEmpty() && item.categoryCode() != null
                && !allowedCategories.contains(item.categoryCode())) {
            return java.util.Optional.of(ExclusionReason.LEVEL_RESTRICTION_CATEGORY);
        }
        return java.util.Optional.empty();
    }

    /** Parses the CSV allowlist column; empty or null means "all categories permitted". */
    public static Set<String> parseCategories(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static List<EmployeeLevel> allLevels() {
        return Arrays.asList(EmployeeLevel.values());
    }
}
