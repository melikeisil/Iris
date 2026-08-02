package com.smartwork.assignment.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the scorer is allowed to know about a candidate.
 *
 * <p>This record is the enforcement boundary for docs/08 §4.8. The scorer cannot consider
 * a protected characteristic because there is no field here carrying one, and no field
 * here carrying one because there is no column in {@code users} to populate it from.
 * Three layers, all pointing the same way.
 *
 * <p>Assembled by {@code CandidateFeatureAssembler}; the scoring functions themselves are
 * pure and take only this.
 */
public record CandidateFeatures(
        UUID userId,
        String fullName,

        // --- skill ---
        /** Candidate proficiency 1-5 per skill code. Absent key means "does not hold it". */
        Map<String, Integer> proficiencyBySkill,
        /** Days since last use per skill code; absent means never recorded. */
        Map<String, Integer> daysSinceLastUseBySkill,

        // --- team ---
        boolean memberOfRecommendedTeam,
        boolean memberOfRecommendedDepartment,
        boolean holdsAllMandatorySkills,

        // --- capacity ---
        BigDecimal workloadRatio,
        BigDecimal availableHoursBeforeDue,

        // --- experience ---
        int similarCompletedLast180Days,
        int reopenedSimilarLast180Days,

        // --- component ---
        int affectedComponentsWorkedLast180Days,
        boolean workedInSameProject,

        // --- fairness ---
        int assignedLast14Days,

        // --- eligibility inputs (not scored) ---
        boolean active,
        com.smartwork.identity.domain.EmployeeLevel employeeLevel,
        int activeWorkItemCount,
        boolean isReporterOfThisItem) {

    /** The skill requirement side of the skill score. */
    public record RequiredSkill(String skillCode, int minProficiency, boolean mandatory) {
    }

    /** Item-side facts shared by every candidate in one ranking run. */
    public record WorkItemContext(
            List<RequiredSkill> requiredSkills,
            List<String> affectedComponents,
            BigDecimal estimatedHours,
            boolean hasDueDate,
            boolean requiresIndependentReview,
            boolean productionImpacting,
            boolean budgetSensitive,
            boolean securitySensitive,
            com.smartwork.workitem.domain.Priority priority,
            String categoryCode) {
    }
}
