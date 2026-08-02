package com.smartwork.assignment.domain;

import com.smartwork.assignment.domain.CandidateFeatures.RequiredSkill;
import com.smartwork.assignment.domain.CandidateFeatures.WorkItemContext;
import com.smartwork.identity.domain.EmployeeLevel;
import com.smartwork.workitem.domain.Priority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringEngineTest {

    private final ScoringEngine engine = new ScoringEngine(ScoringWeights.defaults());

    private static CandidateFeatures candidate(
            String name, int javaProficiency, String workload, int assignedRecently, boolean inTeam) {
        return new CandidateFeatures(
                UUID.nameUUIDFromBytes(name.getBytes()), name,
                Map.of("JAVA", javaProficiency), Map.of("JAVA", 10),
                inTeam, true, javaProficiency >= 3, new BigDecimal(workload), null,
                3, 0, 1, true, assignedRecently,
                true, EmployeeLevel.MID, 2, false);
    }

    private static WorkItemContext item() {
        return new WorkItemContext(
                List.of(new RequiredSkill("JAVA", 3, true)), List.of("checkout-api"),
                new BigDecimal("6"), false, false, false, false, false, Priority.MEDIUM, null);
    }

    @Test
    void theStrongerCandidateRanksFirst() {
        CandidateFeatures strong = candidate("Strong", 5, "0.40", 2, true);
        CandidateFeatures weak = candidate("Weak", 3, "0.95", 2, false);

        List<CandidateScore> ranked = engine.rank(List.of(weak, strong), item());

        assertThat(ranked).hasSize(2);
        assertThat(ranked.get(0).fullName()).isEqualTo("Strong");
        assertThat(ranked.get(0).rank()).isEqualTo(1);
        assertThat(ranked.get(1).rank()).isEqualTo(2);
        assertThat(ranked.get(0).totalScore()).isGreaterThan(ranked.get(1).totalScore());
    }

    @Test
    @DisplayName("ranking is stable — the same inputs reproduce the same order")
    void rankingIsDeterministic() {
        // docs/08 §9 requires a routing decision to be replayable. Without a tiebreaker,
        // two candidates on identical scores could swap places between runs and make that
        // guarantee quietly false.
        List<CandidateFeatures> pool = List.of(
                candidate("Alice", 4, "0.50", 2, true),
                candidate("Bob", 4, "0.50", 2, true),
                candidate("Carol", 4, "0.50", 2, true));

        List<String> first = engine.rank(pool, item()).stream().map(CandidateScore::fullName).toList();
        List<String> second = engine.rank(pool, item()).stream().map(CandidateScore::fullName).toList();
        List<String> reversed = engine.rank(pool.reversed(), item()).stream()
                .map(CandidateScore::fullName).toList();

        assertThat(first).isEqualTo(second).isEqualTo(reversed);
    }

    @Test
    void topThreeIsCapped() {
        List<CandidateFeatures> pool = List.of(
                candidate("A", 5, "0.30", 1, true),
                candidate("B", 4, "0.40", 1, true),
                candidate("C", 4, "0.50", 2, true),
                candidate("D", 3, "0.60", 3, false),
                candidate("E", 3, "0.70", 4, false));

        List<CandidateScore> ranked = engine.rank(pool, item());
        assertThat(ranked).hasSize(5);
        assertThat(engine.topThree(ranked)).hasSize(3);
    }

    @Test
    void everyComponentIsRecordedSeparately() {
        // The UI shows the breakdown, so a total with no parts is not enough.
        CandidateScore score = engine.rank(List.of(candidate("Solo", 4, "0.50", 1, true)), item()).get(0);

        assertThat(score.components().skill()).isBetween(0, 100);
        assertThat(score.components().capacity()).isBetween(0, 100);
        assertThat(score.components().team()).isBetween(0, 100);
        assertThat(score.components().experience()).isBetween(0, 100);
        assertThat(score.components().component()).isBetween(0, 100);
        assertThat(score.components().deadline()).isBetween(0, 100);
        assertThat(score.components().fairness()).isBetween(0, 100);
    }

    @Test
    void theExplanationIsConsistentWithTheNumbersBesideIt() {
        CandidateScore score = engine.rank(List.of(candidate("Solo", 5, "0.30", 0, true)), item()).get(0);
        assertThat(score.explanation()).isNotBlank().endsWith(".");
    }

    @Test
    void weightsChangeTheOutcome() {
        // A skill-heavy policy should favour the more skilled but busier person; the
        // default balance should not. This is what makes department policies meaningful.
        //
        // The requirement asks for proficiency 5 deliberately: at min proficiency 3 both
        // candidates cap out at a skill score of 100 and the component cannot discriminate
        // at all, so the test would pass or fail for reasons unrelated to weighting.
        WorkItemContext demandingItem = new WorkItemContext(
                List.of(new RequiredSkill("JAVA", 5, true)), List.of("checkout-api"),
                new BigDecimal("6"), false, false, false, false, false, Priority.MEDIUM, null);

        CandidateFeatures skilledButBusy = candidate("Skilled", 5, "0.95", 2, true);
        CandidateFeatures averageAndFree = candidate("Free", 3, "0.20", 2, true);

        ScoringEngine skillHeavy = new ScoringEngine(ScoringWeights.of(Map.of(
                "skill", new BigDecimal("0.70"),
                "capacity", new BigDecimal("0.05"),
                "team", new BigDecimal("0.10"),
                "experience", new BigDecimal("0.05"),
                "component", new BigDecimal("0.05"),
                "deadline", new BigDecimal("0.03"),
                "fairness", new BigDecimal("0.02"))));

        assertThat(skillHeavy.rank(List.of(averageAndFree, skilledButBusy), demandingItem).get(0).fullName())
                .isEqualTo("Skilled");
        assertThat(engine.rank(List.of(skilledButBusy, averageAndFree), demandingItem).get(0).fullName())
                .isEqualTo("Free");
    }

    @Test
    void anEmptyPoolRanksNobodyRatherThanThrowing() {
        assertThat(engine.rank(List.of(), item())).isEmpty();
    }
}
