package com.smartwork.assignment.domain;

import com.smartwork.assignment.domain.CandidateFeatures.RequiredSkill;
import com.smartwork.assignment.domain.CandidateFeatures.WorkItemContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Hard filters, applied before scoring (docs/08 §3).
 *
 * <p>Pure. The output deliberately keeps the excluded candidates and their reasons rather
 * than returning only survivors — see {@link ExclusionReason} for why that matters.
 */
public final class EligibilityFilter {

    /** Above this ratio a person is not merely busy, they are a scheduling risk. */
    public static final BigDecimal SEVERE_OVERLOAD_RATIO = new BigDecimal("1.2");

    private final Function<com.smartwork.identity.domain.EmployeeLevel, LevelRestrictions> restrictionsLookup;

    public EligibilityFilter(
            Function<com.smartwork.identity.domain.EmployeeLevel, LevelRestrictions> restrictionsLookup) {
        this.restrictionsLookup = restrictionsLookup;
    }

    /** Built-in defaults, for unit tests and organizations with no configured policies. */
    public static EligibilityFilter withDefaults() {
        return new EligibilityFilter(LevelRestrictions::defaultsFor);
    }

    public record Outcome(
            List<CandidateFeatures> eligible,
            Map<CandidateFeatures, ExclusionReason> excluded) {

        public boolean isEmpty() {
            return eligible.isEmpty();
        }
    }

    public Outcome apply(List<CandidateFeatures> pool, WorkItemContext item, boolean hasSeniorReviewer) {
        List<CandidateFeatures> eligible = new ArrayList<>();
        Map<CandidateFeatures, ExclusionReason> excluded = new LinkedHashMap<>();

        for (CandidateFeatures candidate : pool) {
            Optional<ExclusionReason> reason = evaluate(candidate, item, hasSeniorReviewer);
            if (reason.isPresent()) {
                excluded.put(candidate, reason.get());
            } else {
                eligible.add(candidate);
            }
        }
        return new Outcome(eligible, excluded);
    }

    Optional<ExclusionReason> evaluate(
            CandidateFeatures candidate, WorkItemContext item, boolean hasSeniorReviewer) {

        if (!candidate.active()) {
            return Optional.of(ExclusionReason.INACTIVE_USER);
        }
        if (!holdsAllMandatorySkills(candidate, item.requiredSkills())) {
            return Optional.of(ExclusionReason.MISSING_MANDATORY_SKILL);
        }
        if (candidate.workloadRatio() != null
                && candidate.workloadRatio().compareTo(SEVERE_OVERLOAD_RATIO) > 0) {
            return Optional.of(ExclusionReason.SEVERELY_OVERLOADED);
        }
        if (candidate.isReporterOfThisItem() && item.requiresIndependentReview()) {
            return Optional.of(ExclusionReason.SELF_ASSIGNMENT_CONFLICT);
        }

        LevelRestrictions restrictions = restrictionsLookup.apply(candidate.employeeLevel());
        if (restrictions != null) {
            Optional<ExclusionReason> violation =
                    restrictions.violationFor(item, candidate.activeWorkItemCount(), hasSeniorReviewer);
            if (violation.isPresent()) {
                return violation;
            }
        }
        return Optional.empty();
    }

    static boolean holdsAllMandatorySkills(CandidateFeatures candidate, List<RequiredSkill> requiredSkills) {
        if (requiredSkills == null || requiredSkills.isEmpty()) {
            return true;
        }
        for (RequiredSkill required : requiredSkills) {
            if (!required.mandatory()) {
                continue;
            }
            Integer proficiency = candidate.proficiencyBySkill().get(required.skillCode());
            if (proficiency == null || proficiency < required.minProficiency()) {
                return false;
            }
        }
        return true;
    }
}
