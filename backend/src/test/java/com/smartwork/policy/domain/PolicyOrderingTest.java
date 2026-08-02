package com.smartwork.policy.domain;

import com.smartwork.workitem.domain.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ordering guarantee from docs/08 §5: routes are evaluated top-down and the first
 * match wins, so no combination of conditions can produce a laxer outcome than the
 * strictest applicable trigger.
 *
 * <p>This is the property that makes the engine safe to extend. Adding a trigger to a
 * lenient route can never weaken a strict one, because the strict one was already tested
 * and returned. The alternative — scoring triggers and picking a winner — fails exactly
 * here, and fails silently.
 */
class PolicyOrderingTest {

    private final PolicyEngine engine = PolicyEngine.withDefaults();

    @Test
    @DisplayName("a CRITICAL, low-confidence item reaches a manager, never a team lead")
    void criticalRiskAndLowConfidenceRoutesToManagerApproval() {
        // Both a MANAGER_APPROVAL trigger (critical risk) and a TEAM_LEAD_REVIEW trigger
        // (confidence below threshold) fire. The strict one must win.
        RoutingInputs inputs = RoutingInputs.autoAssignable()
                .riskLevel(RiskLevel.CRITICAL)
                .confidence(new BigDecimal("0.65"))
                .build();

        RoutingDecision decision = engine.decide(inputs);
        assertThat(decision.route()).isEqualTo(ApprovalRoute.MANAGER_APPROVAL);
        assertThat(decision.hasReason(ReasonCode.RISK_CRITICAL)).isTrue();
    }

    @Test
    @DisplayName("a suspected injection outranks every other signal, including a clean analysis")
    void injectionSuspectedWinsOverEverything() {
        // Deliberate: if the analysis may be adversarial, every signal it produced —
        // risk, sensitivity, confidence — is untrustworthy as a routing input.
        RoutingInputs inputs = RoutingInputs.autoAssignable()
                .injectionSuspected(true)
                .riskLevel(RiskLevel.CRITICAL)
                .budgetImpact(true)
                .build();

        RoutingDecision decision = engine.decide(inputs);
        assertThat(decision.route()).isEqualTo(ApprovalRoute.MANUAL_EXCEPTION_REVIEW);
        assertThat(decision.hasReason(ReasonCode.INJECTION_SUSPECTED)).isTrue();
    }

    @Test
    void aFailedAnalysisRoutesToManualExceptionReview() {
        RoutingInputs inputs = RoutingInputs.autoAssignable().analysisFailed(true).build();
        assertThat(engine.decide(inputs).route()).isEqualTo(ApprovalRoute.MANUAL_EXCEPTION_REVIEW);
    }

    @Test
    @DisplayName("missing information goes back to the requester before anyone reviews it")
    void requiresMoreInformationOutranksReviewRoutes() {
        // Sending an under-specified item to a Team Lead just makes them ask the same
        // question the engine already knows needs asking.
        RoutingInputs inputs = RoutingInputs.autoAssignable()
                .requiresMoreInformation(true)
                .confidence(new BigDecimal("0.65"))
                .departmentCount(3)
                .build();

        assertThat(engine.decide(inputs).route()).isEqualTo(ApprovalRoute.REQUEST_MORE_INFORMATION);
    }

    @Test
    void managerApprovalOutranksProcessOwnerReview() {
        RoutingInputs inputs = RoutingInputs.autoAssignable()
                .budgetImpact(true)     // manager trigger
                .departmentCount(3)     // process owner trigger
                .build();

        assertThat(engine.decide(inputs).route()).isEqualTo(ApprovalRoute.MANAGER_APPROVAL);
    }

    @Test
    void processOwnerReviewOutranksTeamLeadReview() {
        RoutingInputs inputs = RoutingInputs.autoAssignable()
                .departmentCount(2)                          // process owner trigger
                .confidence(new BigDecimal("0.70"))          // team lead trigger
                .build();

        assertThat(engine.decide(inputs).route()).isEqualTo(ApprovalRoute.PROCESS_OWNER_REVIEW);
    }

    @Test
    @DisplayName("the declared enum order matches the severity ordering the engine relies on")
    void enumOrderEncodesSeverity() {
        // Reordering the constants would silently change which route wins a tie. Asserting
        // it here means that edit fails the build instead.
        List<ApprovalRoute> expected = List.of(
                ApprovalRoute.MANUAL_EXCEPTION_REVIEW,
                ApprovalRoute.REQUEST_MORE_INFORMATION,
                ApprovalRoute.MANAGER_APPROVAL,
                ApprovalRoute.PROCESS_OWNER_REVIEW,
                ApprovalRoute.TEAM_LEAD_REVIEW,
                ApprovalRoute.AUTO_ASSIGN);

        assertThat(List.of(ApprovalRoute.values())).containsExactlyElementsOf(expected);

        for (int i = 1; i < expected.size(); i++) {
            assertThat(expected.get(i - 1).isStricterThan(expected.get(i)))
                    .as("%s must be stricter than %s", expected.get(i - 1), expected.get(i))
                    .isTrue();
        }
    }

    @Test
    void onlyAutoAssignAvoidsAHuman() {
        for (ApprovalRoute route : ApprovalRoute.values()) {
            assertThat(route.requiresHuman())
                    .as("%s", route)
                    .isEqualTo(route != ApprovalRoute.AUTO_ASSIGN);
        }
    }

    @Test
    @DisplayName("escalation always ends at the manager, and the manager cannot escalate further")
    void escalationTargets() {
        assertThat(ApprovalRoute.TEAM_LEAD_REVIEW.escalationTarget())
                .contains(ApprovalRoute.MANAGER_APPROVAL);
        assertThat(ApprovalRoute.PROCESS_OWNER_REVIEW.escalationTarget())
                .contains(ApprovalRoute.MANAGER_APPROVAL);
        // An escalation loop would be worse than a stop.
        assertThat(ApprovalRoute.MANAGER_APPROVAL.escalationTarget()).isEmpty();
        assertThat(ApprovalRoute.AUTO_ASSIGN.escalationTarget()).isEmpty();
    }
}
