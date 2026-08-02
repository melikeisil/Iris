package com.smartwork.policy.domain;

import com.smartwork.workitem.domain.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * docs/08 §9: "AutoAssignAllConditionsTest — 14 tests, each flipping exactly one condition
 * and asserting the route degrades."
 *
 * <p>Every test starts from {@link RoutingInputs#autoAssignable()}, a baseline where all
 * fourteen conditions hold, and changes one field. That structure is the whole value of
 * the suite: if a test fails, the cause is the single field it touched, and if the
 * baseline itself ever stops auto-assigning, {@link #theBaselineAutoAssigns()} fails first
 * and tells you the other fourteen results are meaningless.
 */
class AutoAssignConditionsTest {

    private final PolicyEngine engine = PolicyEngine.withDefaults();

    private ApprovalRoute routeOf(RoutingInputs inputs) {
        return engine.decide(inputs).route();
    }

    @Test
    @DisplayName("baseline: all fourteen conditions hold, so no human is involved")
    void theBaselineAutoAssigns() {
        RoutingDecision decision = engine.decide(RoutingInputs.autoAssignable().build());

        assertThat(decision.route()).isEqualTo(ApprovalRoute.AUTO_ASSIGN);
        assertThat(decision.reasonCodes()).containsExactly(ReasonCode.ALL_AUTO_ASSIGN_CONDITIONS_MET);
        assertThat(decision.explanation()).contains("Assigned automatically");
    }

    // ---- 1. risk ----

    @Test
    void condition01_riskAboveTheCeilingBlocksAutoAssign() {
        RoutingInputs medium = RoutingInputs.autoAssignable().riskLevel(RiskLevel.MEDIUM).build();
        assertThat(routeOf(medium)).isEqualTo(ApprovalRoute.TEAM_LEAD_REVIEW);

        RoutingInputs critical = RoutingInputs.autoAssignable().riskLevel(RiskLevel.CRITICAL).build();
        assertThat(routeOf(critical)).isEqualTo(ApprovalRoute.MANAGER_APPROVAL);
    }

    // ---- 2. confidence ----

    @Test
    void condition02_confidenceBelowTheThresholdBlocksAutoAssign() {
        RoutingInputs inputs = RoutingInputs.autoAssignable()
                .confidence(new BigDecimal("0.80")).build();

        RoutingDecision decision = engine.decide(inputs);
        assertThat(decision.route()).isEqualTo(ApprovalRoute.TEAM_LEAD_REVIEW);
        assertThat(decision.hasReason(ReasonCode.CONFIDENCE_BELOW_AUTO_THRESHOLD)).isTrue();
    }

    @Test
    @DisplayName("confidence exactly at the threshold is sufficient — the bar is inclusive")
    void condition02_boundaryIsInclusive() {
        RoutingInputs atThreshold = RoutingInputs.autoAssignable()
                .confidence(new BigDecimal("0.85")).build();
        assertThat(routeOf(atThreshold)).isEqualTo(ApprovalRoute.AUTO_ASSIGN);
    }

    // ---- 3. top candidate workload ----

    @Test
    void condition03_anOverloadedTopCandidateBlocksAutoAssign() {
        RoutingInputs inputs = RoutingInputs.autoAssignable()
                .topCandidateWorkload(new BigDecimal("0.95")).build();

        RoutingDecision decision = engine.decide(inputs);
        assertThat(decision.route()).isEqualTo(ApprovalRoute.TEAM_LEAD_REVIEW);
        assertThat(decision.hasReason(ReasonCode.TOP_CANDIDATE_WORKLOAD_HIGH)).isTrue();
    }

    // ---- 4. one department, one team ----

    @Test
    void condition04_multipleDepartmentsBlockAutoAssign() {
        RoutingInputs inputs = RoutingInputs.autoAssignable().departmentCount(3).build();
        // Cross-department work is a Process Owner's call, not a Team Lead's.
        assertThat(routeOf(inputs)).isEqualTo(ApprovalRoute.PROCESS_OWNER_REVIEW);
    }

    @Test
    void condition04_multipleTeamsBlockAutoAssign() {
        RoutingInputs inputs = RoutingInputs.autoAssignable().teamCount(2).build();
        assertThat(routeOf(inputs)).isEqualTo(ApprovalRoute.PROCESS_OWNER_REVIEW);
    }

    // ---- 5-8. the four sensitivity flags ----

    @Test
    void condition05_securityImpactBlocksAutoAssign() {
        RoutingInputs inputs = RoutingInputs.autoAssignable().securityImpact(true).build();
        RoutingDecision decision = engine.decide(inputs);
        assertThat(decision.route()).isEqualTo(ApprovalRoute.MANAGER_APPROVAL);
        assertThat(decision.hasReason(ReasonCode.SECURITY_IMPACT)).isTrue();
    }

    @Test
    void condition06_productionImpactBlocksAutoAssign() {
        RoutingInputs inputs = RoutingInputs.autoAssignable().productionImpact(true).build();
        RoutingDecision decision = engine.decide(inputs);
        assertThat(decision.route()).isEqualTo(ApprovalRoute.MANAGER_APPROVAL);
        assertThat(decision.hasReason(ReasonCode.PRODUCTION_IMPACT)).isTrue();
    }

    @Test
    @DisplayName("budget impact stops the one onboarding task that touches money")
    void condition07_budgetImpactBlocksAutoAssign() {
        // This is Demo 1 step 7: eight tasks assign themselves, the equipment purchase
        // stops for a human. The whole product in one assertion.
        RoutingInputs inputs = RoutingInputs.autoAssignable().budgetImpact(true).build();
        RoutingDecision decision = engine.decide(inputs);
        assertThat(decision.route()).isEqualTo(ApprovalRoute.MANAGER_APPROVAL);
        assertThat(decision.hasReason(ReasonCode.BUDGET_IMPACT)).isTrue();
    }

    @Test
    void condition08_complianceImpactBlocksAutoAssign() {
        RoutingInputs inputs = RoutingInputs.autoAssignable().complianceImpact(true).build();
        RoutingDecision decision = engine.decide(inputs);
        assertThat(decision.route()).isEqualTo(ApprovalRoute.MANAGER_APPROVAL);
        assertThat(decision.hasReason(ReasonCode.COMPLIANCE_IMPACT)).isTrue();
    }

    // ---- 9. policy exception ----

    @Test
    void condition09_anActivePolicyExceptionBlocksAutoAssign() {
        RoutingInputs inputs = RoutingInputs.autoAssignable().activePolicyException(true).build();
        RoutingDecision decision = engine.decide(inputs);
        assertThat(decision.route()).isEqualTo(ApprovalRoute.MANAGER_APPROVAL);
        assertThat(decision.hasReason(ReasonCode.ACTIVE_POLICY_EXCEPTION)).isTrue();
    }

    // ---- 10. an eligible candidate exists ----

    @Test
    @DisplayName("an empty pool routes to a team lead — never silently unassigned")
    void condition10_noEligibleCandidateBlocksAutoAssign() {
        RoutingInputs inputs = RoutingInputs.autoAssignable()
                .noEligibleCandidate(true).topScore(null).build();

        RoutingDecision decision = engine.decide(inputs);
        assertThat(decision.route()).isEqualTo(ApprovalRoute.TEAM_LEAD_REVIEW);
        assertThat(decision.hasReason(ReasonCode.NO_ELIGIBLE_CANDIDATE)).isTrue();
    }

    // ---- 11. size and leafness ----

    @Test
    void condition11_anOversizedItemBlocksAutoAssign() {
        RoutingInputs inputs = RoutingInputs.autoAssignable()
                .estimatedHours(new BigDecimal("16")).build();

        RoutingDecision decision = engine.decide(inputs);
        assertThat(decision.route()).isEqualTo(ApprovalRoute.TEAM_LEAD_REVIEW);
        assertThat(decision.hasReason(ReasonCode.OVERSIZED_FOR_AUTO_ASSIGN)).isTrue();
    }

    @Test
    @DisplayName("a parent item is never auto-assigned — its children are the real work")
    void condition11_aNonLeafItemBlocksAutoAssign() {
        RoutingInputs inputs = RoutingInputs.autoAssignable().leafItem(false).build();

        RoutingDecision decision = engine.decide(inputs);
        assertThat(decision.route()).isEqualTo(ApprovalRoute.TEAM_LEAD_REVIEW);
        assertThat(decision.hasReason(ReasonCode.OVERSIZED_FOR_AUTO_ASSIGN)).isTrue();
    }

    // ---- 12. dependencies ----

    @Test
    void condition12_anUnresolvedDependencyBlocksAutoAssign() {
        RoutingInputs inputs = RoutingInputs.autoAssignable().hasUnresolvedDependency(true).build();

        RoutingDecision decision = engine.decide(inputs);
        assertThat(decision.route()).isEqualTo(ApprovalRoute.TEAM_LEAD_REVIEW);
        assertThat(decision.hasReason(ReasonCode.UNRESOLVED_DEPENDENCY)).isTrue();
    }

    // ---- 13. an unambiguous winner ----

    @Test
    @DisplayName("a 91 vs 89 near-tie routes to a team lead rather than flipping a coin")
    void condition13_closeCandidateScoresBlockAutoAssign() {
        RoutingInputs inputs = RoutingInputs.autoAssignable()
                .topScore(91).scoreGap(2).build();

        RoutingDecision decision = engine.decide(inputs);
        assertThat(decision.route()).isEqualTo(ApprovalRoute.TEAM_LEAD_REVIEW);
        assertThat(decision.hasReason(ReasonCode.CANDIDATE_SCORES_CLOSE)).isTrue();
    }

    @Test
    void condition13_aGapExactlyAtTheMinimumIsSufficient() {
        RoutingInputs inputs = RoutingInputs.autoAssignable().scoreGap(5).build();
        assertThat(routeOf(inputs)).isEqualTo(ApprovalRoute.AUTO_ASSIGN);
    }

    // ---- 14. the winner is actually good ----

    @Test
    @DisplayName("the best of a bad pool is still a bad pool")
    void condition14_aTopScoreBelowTheFloorBlocksAutoAssign() {
        RoutingInputs inputs = RoutingInputs.autoAssignable().topScore(45).build();

        RoutingDecision decision = engine.decide(inputs);
        assertThat(decision.route()).isEqualTo(ApprovalRoute.TEAM_LEAD_REVIEW);
        assertThat(decision.hasReason(ReasonCode.TOP_SCORE_BELOW_THRESHOLD)).isTrue();
    }

    @Test
    void condition14_aScoreExactlyAtTheFloorIsSufficient() {
        RoutingInputs inputs = RoutingInputs.autoAssignable().topScore(60).build();
        assertThat(routeOf(inputs)).isEqualTo(ApprovalRoute.AUTO_ASSIGN);
    }

    // ---- cross-department pool ----

    @Test
    @DisplayName("a candidate found only by org-wide skill search cannot auto-assign by default")
    void crossDepartmentPoolBlocksAutoAssignUnlessPolicyAllowsIt() {
        RoutingInputs inputs = RoutingInputs.autoAssignable().crossDepartmentPool(true).build();

        RoutingDecision decision = engine.decide(inputs);
        assertThat(decision.route()).isEqualTo(ApprovalRoute.TEAM_LEAD_REVIEW);
        assertThat(decision.hasReason(ReasonCode.CROSS_DEPARTMENT_POOL)).isTrue();

        PolicyThresholds permissive = new PolicyThresholds(
                null, null, new BigDecimal("0.85"), new BigDecimal("0.80"), 8,
                RiskLevel.LOW, 60, 5, true);
        assertThat(new PolicyEngine(permissive).decide(inputs).route())
                .isEqualTo(ApprovalRoute.AUTO_ASSIGN);
    }

    @Test
    @DisplayName("blockers are reported individually so the reviewer knows what to fix")
    void multipleBlockersAreAllReported() {
        RoutingInputs inputs = RoutingInputs.autoAssignable()
                .topScore(45)
                .scoreGap(1)
                .hasUnresolvedDependency(true)
                .build();

        RoutingDecision decision = engine.decide(inputs);
        assertThat(decision.reasonCodes()).contains(
                ReasonCode.TOP_SCORE_BELOW_THRESHOLD,
                ReasonCode.CANDIDATE_SCORES_CLOSE,
                ReasonCode.UNRESOLVED_DEPENDENCY);
        assertThat(decision.explanation()).contains("Specifically:");
    }
}
