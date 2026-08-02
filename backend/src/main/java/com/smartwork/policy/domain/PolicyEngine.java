package com.smartwork.policy.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The routing decision (docs/08 §5). Pure: inputs in, decision out, no I/O.
 *
 * <p><b>Evaluated top-down, first match wins.</b> That ordering is the safety property of
 * the whole design. Because the strictest route is tested first, no combination of
 * conditions can produce a laxer outcome than the strictest applicable trigger — a
 * CRITICAL-risk item cannot be talked down to a Team Lead by also being low-confidence.
 * The alternative, scoring the triggers and picking a winner, has the opposite failure
 * mode: a rule added later can dilute a rule added earlier, and nobody notices until it
 * matters.
 *
 * <p>The engine answers "what kind of scrutiny does this need". It has no idea who any of
 * the people are — turning a route into a queue is {@link ApproverResolver}'s job. Keeping
 * them apart means reorganising a department cannot change a routing outcome, which is
 * what makes a historical decision meaningfully replayable.
 */
public final class PolicyEngine {

    /**
     * Bumped when a <em>rule</em> changes, not when a threshold does. Thresholds live in
     * {@link PolicyThresholds} and are versioned by the database row; this versions the
     * logic itself, so a replay under changed code is detectable rather than silently
     * producing a different answer to the same question.
     */
    public static final int VERSION = 1;

    /** docs/08 §5.4: "estimatedTotalHours > 80 (major resource)". */
    static final BigDecimal MAJOR_RESOURCE_HOURS = new BigDecimal("80");

    static final BigDecimal TEAM_LEAD_CONFIDENCE_FLOOR = new BigDecimal("0.60");
    static final BigDecimal DECOMPOSITION_CONFIDENCE_FLOOR = new BigDecimal("0.75");
    static final BigDecimal CLASSIFICATION_CONFIDENCE_FLOOR = new BigDecimal("0.80");
    static final BigDecimal SCOPE_CONFIDENCE_FLOOR = new BigDecimal("0.60");
    static final int MULTI_COMPONENT_THRESHOLD = 2;

    private final PolicyThresholds thresholds;

    public PolicyEngine(PolicyThresholds thresholds) {
        this.thresholds = thresholds;
    }

    public static PolicyEngine withDefaults() {
        return new PolicyEngine(PolicyThresholds.defaults());
    }

    public RoutingDecision decide(RoutingInputs in) {
        List<ReasonCode> reasons;

        reasons = manualExceptionTriggers(in);
        if (!reasons.isEmpty()) {
            return decision(ApprovalRoute.MANUAL_EXCEPTION_REVIEW, reasons);
        }

        reasons = moreInformationTriggers(in);
        if (!reasons.isEmpty()) {
            return decision(ApprovalRoute.REQUEST_MORE_INFORMATION, reasons);
        }

        reasons = managerApprovalTriggers(in);
        if (!reasons.isEmpty()) {
            return decision(ApprovalRoute.MANAGER_APPROVAL, reasons);
        }

        reasons = processOwnerTriggers(in);
        if (!reasons.isEmpty()) {
            return decision(ApprovalRoute.PROCESS_OWNER_REVIEW, reasons);
        }

        reasons = teamLeadTriggers(in);
        if (!reasons.isEmpty()) {
            return decision(ApprovalRoute.TEAM_LEAD_REVIEW, reasons);
        }

        // Nothing above matched, so every documented trigger is absent. The auto-assign
        // gate is still evaluated positively rather than assumed: the fourteen conditions
        // include requirements (a score floor, an unambiguous winner) that are not the
        // negation of any review trigger.
        List<ReasonCode> blockers = autoAssignBlockers(in);
        if (blockers.isEmpty()) {
            return decision(ApprovalRoute.AUTO_ASSIGN, List.of(ReasonCode.ALL_AUTO_ASSIGN_CONDITIONS_MET));
        }
        return decision(ApprovalRoute.TEAM_LEAD_REVIEW, blockers);
    }

    // ------------------------------------------------------------------
    // §5, first branch: analysis could not produce a usable result
    // ------------------------------------------------------------------

    private List<ReasonCode> manualExceptionTriggers(RoutingInputs in) {
        List<ReasonCode> reasons = new ArrayList<>();
        if (in.analysisFailed()) {
            reasons.add(ReasonCode.ANALYSIS_FAILED);
        }
        // Tested before everything else deliberately. A suspected injection means the
        // analysis itself may be adversarial, which makes every downstream signal it
        // produced — risk, sensitivity, confidence — untrustworthy as a routing input.
        if (in.injectionSuspected()) {
            reasons.add(ReasonCode.INJECTION_SUSPECTED);
        }
        return reasons;
    }

    private List<ReasonCode> moreInformationTriggers(RoutingInputs in) {
        return in.requiresMoreInformation()
                ? List.of(ReasonCode.MORE_INFORMATION_REQUIRED)
                : List.of();
    }

    // ------------------------------------------------------------------
    // §5.4 MANAGER_APPROVAL
    // ------------------------------------------------------------------

    private List<ReasonCode> managerApprovalTriggers(RoutingInputs in) {
        List<ReasonCode> reasons = new ArrayList<>();
        if (in.riskLevel() == com.smartwork.workitem.domain.RiskLevel.CRITICAL) {
            reasons.add(ReasonCode.RISK_CRITICAL);
        }
        if (in.securityImpact()) {
            reasons.add(ReasonCode.SECURITY_IMPACT);
        }
        if (in.productionImpact()) {
            reasons.add(ReasonCode.PRODUCTION_IMPACT);
        }
        if (in.budgetImpact()) {
            reasons.add(ReasonCode.BUDGET_IMPACT);
        }
        if (in.complianceImpact()) {
            reasons.add(ReasonCode.COMPLIANCE_IMPACT);
        }
        if (in.estimatedTotalHours() != null
                && in.estimatedTotalHours().compareTo(MAJOR_RESOURCE_HOURS) > 0) {
            reasons.add(ReasonCode.MAJOR_RESOURCE_COMMITMENT);
        }
        if (in.crossTeamPriorityConflict()) {
            reasons.add(ReasonCode.CROSS_TEAM_PRIORITY_CONFLICT);
        }
        if (in.activePolicyException()) {
            reasons.add(ReasonCode.ACTIVE_POLICY_EXCEPTION);
        }
        if (in.escalatedByTeamLead()) {
            reasons.add(ReasonCode.TEAM_LEAD_ESCALATION);
        }
        if (in.escalatedByProcessOwner()) {
            reasons.add(ReasonCode.PROCESS_OWNER_ESCALATION);
        }
        return reasons;
    }

    // ------------------------------------------------------------------
    // §5.3 PROCESS_OWNER_REVIEW
    // ------------------------------------------------------------------

    private List<ReasonCode> processOwnerTriggers(RoutingInputs in) {
        List<ReasonCode> reasons = new ArrayList<>();
        if (in.departmentCount() > 1) {
            reasons.add(ReasonCode.MULTI_DEPARTMENT);
        }
        if (in.teamCount() > 1) {
            reasons.add(ReasonCode.MULTI_TEAM);
        }
        // Only for process work: a development story has no template to match, and
        // treating that as a review trigger would send every story to a Process Owner.
        if (in.processWork() && !in.templateMatched()) {
            reasons.add(ReasonCode.NO_TEMPLATE_MATCH);
        }
        if (!in.scopeClear()) {
            reasons.add(ReasonCode.SCOPE_UNCLEAR);
        }
        if (!in.acceptanceCriteriaComplete()) {
            reasons.add(ReasonCode.ACCEPTANCE_CRITERIA_INCOMPLETE);
        }
        if (in.crossTeamDependencies()) {
            reasons.add(ReasonCode.CROSS_TEAM_DEPENDENCIES);
        }
        if (in.epicScopeChangedAfterApproval()) {
            reasons.add(ReasonCode.EPIC_SCOPE_CHANGED);
        }
        if (in.nonStandardPlanGenerated()) {
            reasons.add(ReasonCode.NON_STANDARD_PLAN);
        }
        return reasons;
    }

    // ------------------------------------------------------------------
    // §5.2 TEAM_LEAD_REVIEW
    // ------------------------------------------------------------------

    private List<ReasonCode> teamLeadTriggers(RoutingInputs in) {
        List<ReasonCode> reasons = new ArrayList<>();
        if (in.riskLevel() == com.smartwork.workitem.domain.RiskLevel.MEDIUM
                || in.riskLevel() == com.smartwork.workitem.domain.RiskLevel.HIGH) {
            reasons.add(ReasonCode.RISK_MEDIUM);
        }
        if (in.noEligibleCandidate()) {
            // docs/08 §3: an empty pool must never leave the item silently unassigned.
            reasons.add(ReasonCode.NO_ELIGIBLE_CANDIDATE);
        }
        if (in.confidence() != null
                && in.confidence().compareTo(TEAM_LEAD_CONFIDENCE_FLOOR) >= 0
                && !thresholds.confidenceSufficient(in.confidence())) {
            reasons.add(ReasonCode.CONFIDENCE_BELOW_AUTO_THRESHOLD);
        }
        if (in.requiresDecomposition() && in.decompositionConfidence() != null
                && in.decompositionConfidence().compareTo(DECOMPOSITION_CONFIDENCE_FLOOR) < 0) {
            reasons.add(ReasonCode.DECOMPOSITION_UNCERTAIN);
        }
        if (in.affectedComponentCount() > MULTI_COMPONENT_THRESHOLD) {
            reasons.add(ReasonCode.MULTI_COMPONENT);
        }
        if (in.requiresSeniorSkill()) {
            reasons.add(ReasonCode.SENIOR_SKILL_REQUIRED);
        }
        if (in.classificationConfidence() != null
                && in.classificationConfidence().compareTo(CLASSIFICATION_CONFIDENCE_FLOOR) < 0) {
            reasons.add(ReasonCode.CLASSIFICATION_UNCERTAIN);
        }
        return reasons;
    }

    // ------------------------------------------------------------------
    // §5.1 the fourteen AUTO_ASSIGN conditions
    // ------------------------------------------------------------------

    /**
     * Returns the conditions that are <em>not</em> met. Empty means auto-assign.
     *
     * <p>Reported as a list rather than a boolean so the reviewer who receives the item
     * sees why it stopped. "This needed you because the top two candidates were two points
     * apart" is actionable; "this needed review" is not.
     */
    List<ReasonCode> autoAssignBlockers(RoutingInputs in) {
        List<ReasonCode> blockers = new ArrayList<>();

        // 1 risk / 5-8 sensitivity: already excluded by the routes above, re-checked here
        // so this method is correct in isolation and the condition tests are honest.
        if (!thresholds.riskAcceptable(in.riskLevel())) {
            blockers.add(in.riskLevel() == com.smartwork.workitem.domain.RiskLevel.CRITICAL
                    ? ReasonCode.RISK_CRITICAL : ReasonCode.RISK_MEDIUM);
        }
        if (in.securityImpact()) {
            blockers.add(ReasonCode.SECURITY_IMPACT);
        }
        if (in.productionImpact()) {
            blockers.add(ReasonCode.PRODUCTION_IMPACT);
        }
        if (in.budgetImpact()) {
            blockers.add(ReasonCode.BUDGET_IMPACT);
        }
        if (in.complianceImpact()) {
            blockers.add(ReasonCode.COMPLIANCE_IMPACT);
        }

        // 2 confidence
        if (!thresholds.confidenceSufficient(in.confidence())) {
            blockers.add(ReasonCode.CONFIDENCE_BELOW_AUTO_THRESHOLD);
        }

        // 3 top candidate workload
        if (!thresholds.workloadAcceptable(in.topCandidateWorkload())) {
            blockers.add(ReasonCode.TOP_CANDIDATE_WORKLOAD_HIGH);
        }

        // 4 exactly one department and one team
        if (in.departmentCount() > 1) {
            blockers.add(ReasonCode.MULTI_DEPARTMENT);
        }
        if (in.teamCount() > 1) {
            blockers.add(ReasonCode.MULTI_TEAM);
        }
        if (in.crossDepartmentPool() && !thresholds.allowCrossDepartmentAuto()) {
            blockers.add(ReasonCode.CROSS_DEPARTMENT_POOL);
        }

        // 9 policy exception
        if (in.activePolicyException()) {
            blockers.add(ReasonCode.ACTIVE_POLICY_EXCEPTION);
        }

        // 10 an eligible candidate exists at all (level restrictions are applied upstream
        // in EligibilityFilter, so "nobody eligible" is how they surface here)
        if (in.noEligibleCandidate() || in.topScore() == null) {
            blockers.add(ReasonCode.NO_ELIGIBLE_CANDIDATE);
        }

        // 11 size, and the item must be a leaf
        if (!thresholds.sizeAcceptable(in.estimatedHours())) {
            blockers.add(ReasonCode.OVERSIZED_FOR_AUTO_ASSIGN);
        }
        if (!in.leafItem()) {
            blockers.add(ReasonCode.OVERSIZED_FOR_AUTO_ASSIGN);
        }

        // 12 no unresolved dependency
        if (in.hasUnresolvedDependency()) {
            blockers.add(ReasonCode.UNRESOLVED_DEPENDENCY);
        }

        // 13 an unambiguous winner
        if (in.scoreGap() != null && in.scoreGap() < thresholds.minScoreGap()) {
            blockers.add(ReasonCode.CANDIDATE_SCORES_CLOSE);
        }

        // 14 the winner is actually good, not merely the best of a bad pool
        if (in.topScore() != null && in.topScore() < thresholds.minAutoAssignScore()) {
            blockers.add(ReasonCode.TOP_SCORE_BELOW_THRESHOLD);
        }

        return blockers;
    }

    // ------------------------------------------------------------------

    private RoutingDecision decision(ApprovalRoute route, List<ReasonCode> reasons) {
        return new RoutingDecision(
                route,
                reasons,
                Explanation.render(route, reasons),
                slaFor(route),
                thresholds.policyId(),
                thresholds.policyVersion(),
                VERSION);
    }

    /**
     * How long the reviewer has before the request is chased. Stricter routes get shorter
     * windows: the things that need a Manager are the things that hurt most while waiting.
     */
    private Duration slaFor(ApprovalRoute route) {
        return switch (route) {
            case MANUAL_EXCEPTION_REVIEW -> Duration.ofHours(4);
            case MANAGER_APPROVAL -> Duration.ofHours(8);
            case PROCESS_OWNER_REVIEW -> Duration.ofHours(24);
            case TEAM_LEAD_REVIEW -> Duration.ofHours(12);
            case REQUEST_MORE_INFORMATION -> Duration.ofDays(3);
            case AUTO_ASSIGN -> Duration.ZERO;
        };
    }

    public PolicyThresholds thresholds() {
        return thresholds;
    }
}
