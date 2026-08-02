package com.smartwork.policy.domain;

import com.smartwork.workitem.domain.RiskLevel;

import java.math.BigDecimal;

/**
 * Everything the policy engine is allowed to know.
 *
 * <p>Same seam as {@code GuardContext} in Phase 3 and {@code CandidateFeatures} in
 * Phase 5: the decision logic is a pure function of a record that somebody else assembled.
 * That is what makes the fourteen auto-assign condition tests plain function calls instead
 * of fourteen database fixtures, and what makes a stored decision replayable — replay is
 * just calling the function again with the same record.
 *
 * <p>Note what is <em>not</em> here: no user, no team, no work item entity. The engine
 * decides <em>what kind of scrutiny</em> this work needs. Turning that into "whose queue"
 * is {@code ApproverResolver}'s job, and keeping the two apart means a change to the org
 * chart cannot change a routing outcome.
 */
public record RoutingInputs(

        // ---- analysis state ----
        boolean analysisFailed,
        boolean injectionSuspected,
        boolean requiresMoreInformation,
        BigDecimal confidence,

        // ---- sensitivity signals (docs/07 §6: inputs, never decisions) ----
        RiskLevel riskLevel,
        boolean securityImpact,
        boolean productionImpact,
        boolean budgetImpact,
        boolean complianceImpact,
        boolean activePolicyException,

        // ---- shape of the work ----
        BigDecimal estimatedHours,
        BigDecimal estimatedTotalHours,
        boolean leafItem,
        int affectedComponentCount,
        int departmentCount,
        int teamCount,
        boolean hasUnresolvedDependency,
        boolean crossTeamDependencies,
        boolean requiresSeniorSkill,

        // ---- planning state ----
        boolean requiresDecomposition,
        BigDecimal decompositionConfidence,
        boolean templateMatched,
        boolean processWork,
        boolean scopeClear,
        boolean acceptanceCriteriaComplete,
        boolean nonStandardPlanGenerated,
        boolean epicScopeChangedAfterApproval,
        BigDecimal classificationConfidence,

        // ---- candidate ranking outcome (from Phase 5) ----
        boolean noEligibleCandidate,
        Integer topScore,
        Integer scoreGap,
        BigDecimal topCandidateWorkload,
        boolean crossDepartmentPool,

        // ---- escalation ----
        boolean escalatedByTeamLead,
        boolean escalatedByProcessOwner,
        boolean crossTeamPriorityConflict) {

    /** Starting point for tests and for callers that only set the fields they care about. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A deliberately permissive baseline: every field set so that, on its own, it would
     * permit AUTO_ASSIGN. Each of the fourteen condition tests flips exactly one field and
     * asserts the route degrades — which only isolates that condition if the baseline
     * itself is clean.
     */
    public static Builder autoAssignable() {
        return new Builder()
                .confidence(new BigDecimal("0.95"))
                .riskLevel(RiskLevel.LOW)
                .estimatedHours(new BigDecimal("4"))
                .estimatedTotalHours(new BigDecimal("4"))
                .leafItem(true)
                .affectedComponentCount(1)
                .departmentCount(1)
                .teamCount(1)
                .templateMatched(true)
                .scopeClear(true)
                .acceptanceCriteriaComplete(true)
                .classificationConfidence(new BigDecimal("0.95"))
                .decompositionConfidence(new BigDecimal("0.95"))
                .topScore(85)
                .scoreGap(20)
                .topCandidateWorkload(new BigDecimal("0.50"));
    }

    public static final class Builder {
        private boolean analysisFailed;
        private boolean injectionSuspected;
        private boolean requiresMoreInformation;
        private BigDecimal confidence;
        private RiskLevel riskLevel = RiskLevel.LOW;
        private boolean securityImpact;
        private boolean productionImpact;
        private boolean budgetImpact;
        private boolean complianceImpact;
        private boolean activePolicyException;
        private BigDecimal estimatedHours;
        private BigDecimal estimatedTotalHours;
        private boolean leafItem = true;
        private int affectedComponentCount;
        private int departmentCount = 1;
        private int teamCount = 1;
        private boolean hasUnresolvedDependency;
        private boolean crossTeamDependencies;
        private boolean requiresSeniorSkill;
        private boolean requiresDecomposition;
        private BigDecimal decompositionConfidence;
        private boolean templateMatched = true;
        private boolean processWork;
        private boolean scopeClear = true;
        private boolean acceptanceCriteriaComplete = true;
        private boolean nonStandardPlanGenerated;
        private boolean epicScopeChangedAfterApproval;
        private BigDecimal classificationConfidence;
        private boolean noEligibleCandidate;
        private Integer topScore;
        private Integer scoreGap;
        private BigDecimal topCandidateWorkload;
        private boolean crossDepartmentPool;
        private boolean escalatedByTeamLead;
        private boolean escalatedByProcessOwner;
        private boolean crossTeamPriorityConflict;

        public Builder analysisFailed(boolean v) { this.analysisFailed = v; return this; }
        public Builder injectionSuspected(boolean v) { this.injectionSuspected = v; return this; }
        public Builder requiresMoreInformation(boolean v) { this.requiresMoreInformation = v; return this; }
        public Builder confidence(BigDecimal v) { this.confidence = v; return this; }
        public Builder riskLevel(RiskLevel v) { this.riskLevel = v; return this; }
        public Builder securityImpact(boolean v) { this.securityImpact = v; return this; }
        public Builder productionImpact(boolean v) { this.productionImpact = v; return this; }
        public Builder budgetImpact(boolean v) { this.budgetImpact = v; return this; }
        public Builder complianceImpact(boolean v) { this.complianceImpact = v; return this; }
        public Builder activePolicyException(boolean v) { this.activePolicyException = v; return this; }
        public Builder estimatedHours(BigDecimal v) { this.estimatedHours = v; return this; }
        public Builder estimatedTotalHours(BigDecimal v) { this.estimatedTotalHours = v; return this; }
        public Builder leafItem(boolean v) { this.leafItem = v; return this; }
        public Builder affectedComponentCount(int v) { this.affectedComponentCount = v; return this; }
        public Builder departmentCount(int v) { this.departmentCount = v; return this; }
        public Builder teamCount(int v) { this.teamCount = v; return this; }
        public Builder hasUnresolvedDependency(boolean v) { this.hasUnresolvedDependency = v; return this; }
        public Builder crossTeamDependencies(boolean v) { this.crossTeamDependencies = v; return this; }
        public Builder requiresSeniorSkill(boolean v) { this.requiresSeniorSkill = v; return this; }
        public Builder requiresDecomposition(boolean v) { this.requiresDecomposition = v; return this; }
        public Builder decompositionConfidence(BigDecimal v) { this.decompositionConfidence = v; return this; }
        public Builder templateMatched(boolean v) { this.templateMatched = v; return this; }
        public Builder processWork(boolean v) { this.processWork = v; return this; }
        public Builder scopeClear(boolean v) { this.scopeClear = v; return this; }
        public Builder acceptanceCriteriaComplete(boolean v) { this.acceptanceCriteriaComplete = v; return this; }
        public Builder nonStandardPlanGenerated(boolean v) { this.nonStandardPlanGenerated = v; return this; }
        public Builder epicScopeChangedAfterApproval(boolean v) { this.epicScopeChangedAfterApproval = v; return this; }
        public Builder classificationConfidence(BigDecimal v) { this.classificationConfidence = v; return this; }
        public Builder noEligibleCandidate(boolean v) { this.noEligibleCandidate = v; return this; }
        public Builder topScore(Integer v) { this.topScore = v; return this; }
        public Builder scoreGap(Integer v) { this.scoreGap = v; return this; }
        public Builder topCandidateWorkload(BigDecimal v) { this.topCandidateWorkload = v; return this; }
        public Builder crossDepartmentPool(boolean v) { this.crossDepartmentPool = v; return this; }
        public Builder escalatedByTeamLead(boolean v) { this.escalatedByTeamLead = v; return this; }
        public Builder escalatedByProcessOwner(boolean v) { this.escalatedByProcessOwner = v; return this; }
        public Builder crossTeamPriorityConflict(boolean v) { this.crossTeamPriorityConflict = v; return this; }

        public RoutingInputs build() {
            return new RoutingInputs(
                    analysisFailed, injectionSuspected, requiresMoreInformation, confidence,
                    riskLevel, securityImpact, productionImpact, budgetImpact, complianceImpact,
                    activePolicyException, estimatedHours, estimatedTotalHours, leafItem,
                    affectedComponentCount, departmentCount, teamCount, hasUnresolvedDependency,
                    crossTeamDependencies, requiresSeniorSkill, requiresDecomposition,
                    decompositionConfidence, templateMatched, processWork, scopeClear,
                    acceptanceCriteriaComplete, nonStandardPlanGenerated,
                    epicScopeChangedAfterApproval, classificationConfidence, noEligibleCandidate,
                    topScore, scoreGap, topCandidateWorkload, crossDepartmentPool,
                    escalatedByTeamLead, escalatedByProcessOwner, crossTeamPriorityConflict);
        }
    }
}
