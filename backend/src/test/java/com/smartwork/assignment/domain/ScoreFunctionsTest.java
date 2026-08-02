package com.smartwork.assignment.domain;

import com.smartwork.assignment.domain.CandidateFeatures.RequiredSkill;
import com.smartwork.identity.domain.EmployeeLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scoring components from docs/08 §4, tested at their boundary values.
 *
 * <p>These are pure functions, so every case here is exact rather than approximate — no
 * database, no Spring context, no fixture drift.
 */
class ScoreFunctionsTest {

    private static CandidateFeatures candidate(
            Map<String, Integer> proficiency, Map<String, Integer> recency) {
        return new CandidateFeatures(
                UUID.randomUUID(), "Test Person", proficiency, recency,
                false, false, false, null, null, 0, 0, 0, false, 0,
                true, EmployeeLevel.MID, 0, false);
    }

    @Nested
    @DisplayName("skill score (§4.1)")
    class SkillScore {

        @Test
        void exactMatchWithRecentUseScoresHigh() {
            // proficiency 3 / min 3 = 1.0 * 80 = 80, plus 20 recency = 100
            CandidateFeatures person = candidate(Map.of("JAVA", 3), Map.of("JAVA", 10));
            int score = ScoreFunctions.skillScore(person, List.of(new RequiredSkill("JAVA", 3, true)));
            assertThat(score).isEqualTo(100);
        }

        @Test
        void overQualifiedIsCappedAtOneHundred() {
            // 5/2 * 80 = 200, capped. A principal engineer is not 2.5x better at a
            // mid-level task, and an uncapped ratio would let one deep skill hide a gap.
            CandidateFeatures person = candidate(Map.of("JAVA", 5), Map.of("JAVA", 10));
            int score = ScoreFunctions.skillScore(person, List.of(new RequiredSkill("JAVA", 2, true)));
            assertThat(score).isEqualTo(100);
        }

        @Test
        void underQualifiedScoresProportionally() {
            // 2/4 * 80 = 40, plus 20 recency = 60
            CandidateFeatures person = candidate(Map.of("JAVA", 2), Map.of("JAVA", 30));
            int score = ScoreFunctions.skillScore(person, List.of(new RequiredSkill("JAVA", 4, true)));
            assertThat(score).isEqualTo(60);
        }

        @Test
        void missingSkillContributesZero() {
            CandidateFeatures person = candidate(Map.of("PYTHON", 5), Map.of("PYTHON", 5));
            int score = ScoreFunctions.skillScore(person, List.of(new RequiredSkill("JAVA", 3, true)));
            assertThat(score).isZero();
        }

        @ParameterizedTest(name = "last used {0} days ago gives a bonus of {1}")
        @CsvSource({"0, 20", "90, 20", "91, 10", "365, 10", "366, 0", "1000, 0"})
        void recencyBonusBoundaries(int daysAgo, int expectedBonus) {
            CandidateFeatures person = candidate(Map.of("JAVA", 1), Map.of("JAVA", daysAgo));
            assertThat(ScoreFunctions.recencyBonus(person, "JAVA")).isEqualTo(expectedBonus);
        }

        @Test
        void neverUsedSkillGetsNoRecencyBonus() {
            CandidateFeatures person = candidate(Map.of("JAVA", 3), Map.of());
            // 3/3 * 80 = 80, no bonus
            int score = ScoreFunctions.skillScore(person, List.of(new RequiredSkill("JAVA", 3, true)));
            assertThat(score).isEqualTo(80);
        }

        @Test
        void mandatorySkillsAreWeightedDouble() {
            // Holds the optional skill perfectly, lacks the mandatory one entirely.
            // Weights: mandatory 2 (contributing 0), optional 1 (contributing 100).
            // Weighted mean = 100/3 = 33.
            CandidateFeatures person = candidate(Map.of("REACT", 3), Map.of("REACT", 5));
            int score = ScoreFunctions.skillScore(person, List.of(
                    new RequiredSkill("JAVA", 3, true),
                    new RequiredSkill("REACT", 3, false)));
            assertThat(score).isEqualTo(33);
        }

        @Test
        void noRequiredSkillsIsNeutralNotZero() {
            // Zero would push every candidate for an unskilled task below the auto-assign
            // floor and send trivial work to a human for no reason.
            CandidateFeatures person = candidate(Map.of(), Map.of());
            assertThat(ScoreFunctions.skillScore(person, List.of())).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("capacity score (§4.2) — piecewise, penalty accelerates past 0.85")
    class CapacityScore {

        @ParameterizedTest(name = "workload {0} scores {1}")
        @CsvSource({
                "0.00, 100",
                "0.50, 100",   // top of the flat segment
                "0.85, 58",    // end of the gentle slope
                "1.00, 28",    // end of the steep slope
                "1.20, 0",     // 28 - 0.20*140 = 0
                "2.00, 0"      // clamped, never negative
        })
        void boundaryValues(String ratio, int expected) {
            assertThat(ScoreFunctions.capacityScore(new BigDecimal(ratio))).isEqualTo(expected);
        }

        @Test
        void thePenaltyIsSteeperAbove085ThanBelowIt() {
            int dropBelow = ScoreFunctions.capacityScore(new BigDecimal("0.60"))
                    - ScoreFunctions.capacityScore(new BigDecimal("0.75"));
            int dropAbove = ScoreFunctions.capacityScore(new BigDecimal("0.85"))
                    - ScoreFunctions.capacityScore(new BigDecimal("1.00"));
            assertThat(dropAbove).isGreaterThan(dropBelow);
        }

        @Test
        void unknownWorkloadIsNeutral() {
            assertThat(ScoreFunctions.capacityScore(null)).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("experience score (§4.4) — logarithmic, quality-adjusted")
    class ExperienceScore {

        @Test
        void noSimilarWorkScoresZero() {
            assertThat(ScoreFunctions.experienceScore(0, 0)).isZero();
        }

        @Test
        void diminishingReturns() {
            int first = ScoreFunctions.experienceScore(1, 0);
            int second = ScoreFunctions.experienceScore(2, 0);
            int tenth = ScoreFunctions.experienceScore(10, 0);
            int eleventh = ScoreFunctions.experienceScore(11, 0);

            assertThat(second - first).isGreaterThan(eleventh - tenth);
        }

        @Test
        void reopensReduceTheScore() {
            assertThat(ScoreFunctions.experienceScore(10, 5))
                    .isLessThan(ScoreFunctions.experienceScore(10, 0));
        }

        @Test
        void isCappedAtOneHundred() {
            assertThat(ScoreFunctions.experienceScore(10_000, 0)).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("deadline score (§4.6)")
    class DeadlineScore {

        @Test
        void noDueDateIsNeutralPositive() {
            assertThat(ScoreFunctions.deadlineScore(false, null, new BigDecimal("8")))
                    .isEqualTo(ScoreFunctions.NO_DUE_DATE_SCORE);
        }

        @Test
        void comfortableSlackScoresFull() {
            // 20h available for an 8h task: slack = 1.5 >= 1.0
            assertThat(ScoreFunctions.deadlineScore(true, new BigDecimal("20"), new BigDecimal("8")))
                    .isEqualTo(100);
        }

        @Test
        void exactlyEnoughTimeScoresSixty() {
            // 8h available for 8h: slack = 0 -> 60
            assertThat(ScoreFunctions.deadlineScore(true, new BigDecimal("8"), new BigDecimal("8")))
                    .isEqualTo(60);
        }

        @Test
        void noTimeAtAllScoresZero() {
            // 0h available for 8h: slack = -1 -> 60 - 60 = 0
            assertThat(ScoreFunctions.deadlineScore(true, BigDecimal.ZERO, new BigDecimal("8")))
                    .isZero();
        }
    }

    @Nested
    @DisplayName("fairness score (§4.7) — the anti-hotspot term")
    class FairnessScore {

        @Test
        void atOrBelowTheMedianScoresFull() {
            assertThat(ScoreFunctions.fairnessScore(2, 3)).isEqualTo(100);
            assertThat(ScoreFunctions.fairnessScore(3, 3)).isEqualTo(100);
        }

        @Test
        void aboveTheMedianIsPenalised() {
            assertThat(ScoreFunctions.fairnessScore(4, 3)).isEqualTo(75);
            assertThat(ScoreFunctions.fairnessScore(7, 3)).isZero();
        }

        @Test
        void theBusiestEquallySkilledPersonRanksBelowTheQuieterOne() {
            // The classic failure mode of naive skill matching: the strongest person
            // absorbs everything until they are the team's bottleneck.
            Map<String, Integer> skills = Map.of("JAVA", 5);
            Map<String, Integer> recent = Map.of("JAVA", 5);

            CandidateFeatures busy = new CandidateFeatures(
                    UUID.randomUUID(), "Busy", skills, recent, true, true, true,
                    new BigDecimal("0.60"), null, 5, 0, 2, true, 9,
                    true, EmployeeLevel.MID, 5, false);
            CandidateFeatures quiet = new CandidateFeatures(
                    UUID.randomUUID(), "Quiet", skills, recent, true, true, true,
                    new BigDecimal("0.60"), null, 5, 0, 2, true, 1,
                    true, EmployeeLevel.MID, 1, false);

            int median = ScoreFunctions.medianAssigned(List.of(9, 1));
            assertThat(ScoreFunctions.fairnessScore(quiet.assignedLast14Days(), median))
                    .isGreaterThan(ScoreFunctions.fairnessScore(busy.assignedLast14Days(), median));
        }

        @Test
        void medianOfAnEvenPool() {
            assertThat(ScoreFunctions.medianAssigned(List.of(1, 2, 3, 4))).isEqualTo(2);
        }

        @Test
        void medianOfAnOddPool() {
            assertThat(ScoreFunctions.medianAssigned(List.of(5, 1, 3))).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("team and component scores (§4.3, §4.5)")
    class TeamAndComponent {

        @Test
        void teamScoreLadder() {
            assertThat(ScoreFunctions.teamScore(withTeam(true, true, true))).isEqualTo(100);
            assertThat(ScoreFunctions.teamScore(withTeam(false, true, true))).isEqualTo(70);
            assertThat(ScoreFunctions.teamScore(withTeam(false, false, true))).isEqualTo(40);
            assertThat(ScoreFunctions.teamScore(withTeam(false, false, false))).isEqualTo(15);
        }

        @Test
        void componentScoreFallsBackToTeamScoreWhenNoComponentsAreNamed() {
            // Otherwise this term would silently drag down every service ticket, which
            // never names a component.
            CandidateFeatures person = withTeam(true, true, true);
            assertThat(ScoreFunctions.componentScore(person, List.of()))
                    .isEqualTo(ScoreFunctions.teamScore(person));
        }

        private CandidateFeatures withTeam(boolean inTeam, boolean inDepartment, boolean hasSkills) {
            return new CandidateFeatures(
                    UUID.randomUUID(), "Test", Map.of(), Map.of(),
                    inTeam, inDepartment, hasSkills, null, null, 0, 0, 0, false, 0,
                    true, EmployeeLevel.MID, 0, false);
        }
    }
}
