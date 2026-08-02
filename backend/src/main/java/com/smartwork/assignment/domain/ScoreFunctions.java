package com.smartwork.assignment.domain;

import com.smartwork.assignment.domain.CandidateFeatures.RequiredSkill;
import com.smartwork.assignment.domain.CandidateFeatures.WorkItemContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The seven scoring components as pure functions (docs/08 §4.1-§4.7).
 *
 * <p>Every one of these takes plain values and returns an int in 0-100. No repository, no
 * entity, no Spring context - which is what lets {@code SkillScoreTest} and
 * {@code CapacityScorePiecewiseTest} assert exact boundary values instead of approximating
 * them through a service.
 */
public final class ScoreFunctions {

    private ScoreFunctions() {
    }

    // ------------------------------------------------------------------
    // 4.1 Skill
    // ------------------------------------------------------------------

    static final int RECENCY_BONUS_90_DAYS = 20;
    static final int RECENCY_BONUS_365_DAYS = 10;
    private static final BigDecimal PROFICIENCY_SCALE = new BigDecimal("80");
    private static final int MANDATORY_WEIGHT = 2;
    private static final int OPTIONAL_WEIGHT = 1;

    /**
     * Coverage and depth, mandatory skills weighted double.
     *
     * <p>Exceeding the required proficiency helps but is capped at 100: a principal
     * engineer is not four times better than the required mid-level for a mid-level task,
     * and an uncapped ratio would let one deep skill mask a missing one.
     */
    public static int skillScore(CandidateFeatures candidate, List<RequiredSkill> requiredSkills) {
        if (requiredSkills == null || requiredSkills.isEmpty()) {
            // Nothing required means nothing to differentiate on. Neutral, not zero:
            // zero would push every candidate for an unskilled task below the
            // auto-assign floor and route trivial work to a human for no reason.
            return 50;
        }

        BigDecimal weightedTotal = BigDecimal.ZERO;
        int weightSum = 0;

        for (RequiredSkill required : requiredSkills) {
            int weight = required.mandatory() ? MANDATORY_WEIGHT : OPTIONAL_WEIGHT;
            weightSum += weight;

            Integer proficiency = candidate.proficiencyBySkill().get(required.skillCode());
            if (proficiency == null || proficiency <= 0) {
                continue; // contributes 0
            }

            int minProficiency = Math.max(1, required.minProficiency());
            BigDecimal depth = BigDecimal.valueOf(proficiency)
                    .divide(BigDecimal.valueOf(minProficiency), 4, RoundingMode.HALF_UP)
                    .multiply(PROFICIENCY_SCALE);

            int contribution = Math.min(100, depth.intValue() + recencyBonus(candidate, required.skillCode()));
            weightedTotal = weightedTotal.add(BigDecimal.valueOf((long) contribution * weight));
        }

        if (weightSum == 0) {
            return 0;
        }
        return clamp(weightedTotal.divide(BigDecimal.valueOf(weightSum), 0, RoundingMode.HALF_UP).intValue());
    }

    /** A skill last used three years ago is not the same asset as one used last month. */
    static int recencyBonus(CandidateFeatures candidate, String skillCode) {
        Integer days = candidate.daysSinceLastUseBySkill().get(skillCode);
        if (days == null) {
            return 0;
        }
        if (days <= 90) {
            return RECENCY_BONUS_90_DAYS;
        }
        if (days <= 365) {
            return RECENCY_BONUS_365_DAYS;
        }
        return 0;
    }

    // ------------------------------------------------------------------
    // 4.2 Capacity
    // ------------------------------------------------------------------

    /**
     * Piecewise, not linear. The penalty accelerates past 0.85 because that is where
     * predictability collapses - the difference between 0.5 and 0.6 loaded is noise, the
     * difference between 0.85 and 0.95 is whether anything ships on the promised date.
     */
    public static int capacityScore(BigDecimal workloadRatio) {
        if (workloadRatio == null) {
            return 50;
        }
        double w = workloadRatio.doubleValue();
        double score;
        if (w <= 0.50) {
            score = 100;
        } else if (w <= 0.85) {
            score = 100 - (w - 0.50) * 120;      // 100 -> 58
        } else if (w <= 1.00) {
            score = 58 - (w - 0.85) * 200;       // 58 -> 28
        } else {
            score = 28 - (w - 1.00) * 140;
        }
        return clamp((int) Math.round(score));
    }

    // ------------------------------------------------------------------
    // 4.3 Team
    // ------------------------------------------------------------------

    public static int teamScore(CandidateFeatures candidate) {
        if (candidate.memberOfRecommendedTeam()) {
            return 100;
        }
        if (candidate.memberOfRecommendedDepartment()) {
            return 70;
        }
        return candidate.holdsAllMandatorySkills() ? 40 : 15;
    }

    // ------------------------------------------------------------------
    // 4.4 Experience
    // ------------------------------------------------------------------

    private static final double EXPERIENCE_SCALE = 25.0;
    private static final double QUALITY_PENALTY = 0.15;

    /**
     * Logarithmic: the tenth similar task adds far less than the second.
     *
     * <p>{@code qualityFactor} uses the reopen rate on <em>this kind of work</em>. It is a
     * process signal, not a performance grade, and is never surfaced per person in any
     * report - see docs/08 §4.4.
     */
    public static int experienceScore(int similarCompleted, int reopenedSimilar) {
        if (similarCompleted <= 0) {
            return 0;
        }
        double qualityFactor = 1.0 - QUALITY_PENALTY * ((double) reopenedSimilar / Math.max(1, similarCompleted));
        double raw = EXPERIENCE_SCALE * Math.log(1 + similarCompleted) * qualityFactor;
        return clamp((int) Math.round(Math.min(100.0, raw)));
    }

    // ------------------------------------------------------------------
    // 4.5 Component
    // ------------------------------------------------------------------

    /**
     * Falls back to the team score when the item names no components - typical for service
     * work - so this term never becomes dead weight that drags every service ticket down.
     */
    public static int componentScore(CandidateFeatures candidate, List<String> affectedComponents) {
        if (affectedComponents == null || affectedComponents.isEmpty()) {
            return teamScore(candidate);
        }
        int worked = candidate.affectedComponentsWorkedLast180Days();
        if (worked >= 2) {
            return 100;
        }
        if (worked == 1) {
            return 75;
        }
        return candidate.workedInSameProject() ? 50 : 20;
    }

    // ------------------------------------------------------------------
    // 4.6 Deadline
    // ------------------------------------------------------------------

    /** No due date scores 75 - neutral-positive, so undated work does not distort ranking. */
    public static final int NO_DUE_DATE_SCORE = 75;

    public static int deadlineScore(
            boolean hasDueDate, BigDecimal availableHoursBeforeDue, BigDecimal estimatedHours) {

        if (!hasDueDate || availableHoursBeforeDue == null) {
            return NO_DUE_DATE_SCORE;
        }
        double estimated = estimatedHours == null ? 0.0 : estimatedHours.doubleValue();
        double available = availableHoursBeforeDue.doubleValue();
        double slack = (available - estimated) / Math.max(1.0, estimated);

        double score;
        if (slack >= 1.0) {
            score = 100;
        } else if (slack >= 0) {
            score = 60 + 40 * slack;
        } else {
            score = 60 + 60 * slack;
        }
        return clamp((int) Math.round(score));
    }

    // ------------------------------------------------------------------
    // 4.7 Fairness
    // ------------------------------------------------------------------

    private static final int FAIRNESS_PENALTY_PER_ITEM = 25;

    /**
     * The anti-hotspot term. Without it, naive skill matching sends everything to the
     * strongest person until they are the bottleneck for the whole team. At 5% weight it
     * breaks ties without ever overriding competence.
     */
    public static int fairnessScore(int assignedLast14Days, int poolMedianAssigned) {
        if (assignedLast14Days <= poolMedianAssigned) {
            return 100;
        }
        return clamp(100 - FAIRNESS_PENALTY_PER_ITEM * (assignedLast14Days - poolMedianAssigned));
    }

    /** Median of the eligible pool's recent assignment counts. */
    public static int medianAssigned(List<Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return 0;
        }
        List<Integer> sorted = counts.stream().sorted().toList();
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        }
        return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2;
    }

    // ------------------------------------------------------------------

    static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    /** Convenience: every component for one candidate, in one call. */
    public static ComponentScores allComponents(
            CandidateFeatures candidate, WorkItemContext item, int poolMedianAssigned) {

        return new ComponentScores(
                skillScore(candidate, item.requiredSkills()),
                capacityScore(candidate.workloadRatio()),
                teamScore(candidate),
                experienceScore(candidate.similarCompletedLast180Days(), candidate.reopenedSimilarLast180Days()),
                componentScore(candidate, item.affectedComponents()),
                deadlineScore(item.hasDueDate(), candidate.availableHoursBeforeDue(), item.estimatedHours()),
                fairnessScore(candidate.assignedLast14Days(), poolMedianAssigned));
    }

    public record ComponentScores(
            int skill, int capacity, int team, int experience, int component, int deadline, int fairness) {

        public int valueOf(ScoreComponent which) {
            return switch (which) {
                case SKILL -> skill;
                case CAPACITY -> capacity;
                case TEAM -> team;
                case EXPERIENCE -> experience;
                case COMPONENT -> component;
                case DEADLINE -> deadline;
                case FAIRNESS -> fairness;
            };
        }
    }
}
