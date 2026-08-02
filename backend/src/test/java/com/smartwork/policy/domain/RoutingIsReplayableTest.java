package com.smartwork.policy.domain;

import com.smartwork.workitem.domain.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * docs/08 §9: "RoutingIsReplayableTest — the same inputs and policy version reproduce the
 * identical decision and reason codes."
 *
 * <p>This is the property that makes "why did this need approval?" answerable six months
 * later. It only holds because the engine is a pure function: replay is calling it again.
 * Any hidden input — a clock read, a database lookup, a random tiebreak — would break it
 * quietly, which is why those are structurally absent rather than merely avoided.
 */
class RoutingIsReplayableTest {

    private static final UUID POLICY_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

    private static PolicyThresholds pinnedPolicy() {
        return new PolicyThresholds(
                POLICY_ID, 7L,
                new BigDecimal("0.90"), new BigDecimal("0.70"), 8,
                RiskLevel.LOW, 65, 8, false);
    }

    private static RoutingInputs borderlineCase() {
        return RoutingInputs.autoAssignable()
                .confidence(new BigDecimal("0.72"))
                .topScore(70)
                .scoreGap(4)
                .affectedComponentCount(3)
                .build();
    }

    @Test
    @DisplayName("the same inputs and policy reproduce the identical decision")
    void identicalInputsProduceIdenticalDecisions() {
        PolicyEngine engine = new PolicyEngine(pinnedPolicy());

        RoutingDecision first = engine.decide(borderlineCase());
        RoutingDecision second = engine.decide(borderlineCase());

        assertThat(second).isEqualTo(first);
        assertThat(second.reasonCodes()).isEqualTo(first.reasonCodes());
        assertThat(second.explanation()).isEqualTo(first.explanation());
    }

    @Test
    @DisplayName("a fresh engine over the same policy replays the decision exactly")
    void aSeparateEngineInstanceReplaysTheSameDecision() {
        RoutingDecision original = new PolicyEngine(pinnedPolicy()).decide(borderlineCase());
        RoutingDecision replayed = new PolicyEngine(pinnedPolicy()).decide(borderlineCase());

        assertThat(replayed).isEqualTo(original);
    }

    @Test
    @DisplayName("the decision carries the policy identity needed to replay it")
    void theDecisionRecordsWhichPolicyProducedIt() {
        RoutingDecision decision = new PolicyEngine(pinnedPolicy()).decide(borderlineCase());

        // Without these three, a replay would silently use today's thresholds against a
        // year-old decision and conclude the engine had changed its mind.
        assertThat(decision.policyId()).isEqualTo(POLICY_ID);
        assertThat(decision.policyVersion()).isEqualTo(7L);
        assertThat(decision.engineVersion()).isEqualTo(PolicyEngine.VERSION);
    }

    @Test
    @DisplayName("a different policy version can legitimately produce a different answer")
    void changingThePolicyChangesTheOutcomeAndSaysSo() {
        // The same borderline item under a stricter and a laxer policy. The point is not
        // that they differ, but that each decision records which policy produced it, so
        // the difference is explainable rather than mysterious.
        RoutingInputs inputs = RoutingInputs.autoAssignable()
                .confidence(new BigDecimal("0.87"))
                .build();

        PolicyThresholds lenient = new PolicyThresholds(
                POLICY_ID, 1L, new BigDecimal("0.85"), new BigDecimal("0.80"), 8,
                RiskLevel.LOW, 60, 5, false);
        PolicyThresholds strict = new PolicyThresholds(
                POLICY_ID, 2L, new BigDecimal("0.90"), new BigDecimal("0.70"), 8,
                RiskLevel.LOW, 65, 8, false);

        RoutingDecision underLenient = new PolicyEngine(lenient).decide(inputs);
        RoutingDecision underStrict = new PolicyEngine(strict).decide(inputs);

        assertThat(underLenient.route()).isEqualTo(ApprovalRoute.AUTO_ASSIGN);
        assertThat(underStrict.route()).isEqualTo(ApprovalRoute.TEAM_LEAD_REVIEW);
        assertThat(underLenient.policyVersion()).isEqualTo(1L);
        assertThat(underStrict.policyVersion()).isEqualTo(2L);
    }

    @Test
    void reasonCodesRoundTripThroughTheirCsvForm() {
        RoutingDecision decision = new PolicyEngine(pinnedPolicy()).decide(borderlineCase());

        String csv = decision.reasonCodesCsv();
        List<ReasonCode> parsed = java.util.Arrays.stream(csv.split(","))
                .map(ReasonCode::valueOf)
                .toList();

        assertThat(parsed).isEqualTo(decision.reasonCodes());
    }

    @Test
    @DisplayName("reason codes are a closed set, so route analytics is a group-by")
    void reasonCodesAreStableIdentifiers() {
        // Free text would make the explanation readable and the analytics impossible.
        assertThat(ReasonCode.allCodes())
                .doesNotHaveDuplicates()
                .allSatisfy(code -> assertThat(code).matches("[A-Z][A-Z0-9_]*"));

        for (ReasonCode code : ReasonCode.values()) {
            assertThat(code.phrase()).as("%s needs human phrasing", code).isNotBlank();
        }
    }

    @Test
    void theExplanationIsDerivedFromTheCodesSoTheyCannotDisagree() {
        RoutingDecision decision = new PolicyEngine(pinnedPolicy()).decide(borderlineCase());

        assertThat(decision.reasonCodes()).isNotEmpty();
        for (ReasonCode code : decision.reasonCodes()) {
            assertThat(decision.explanation())
                    .as("explanation must mention %s", code)
                    .contains(code.phrase());
        }
    }
}
