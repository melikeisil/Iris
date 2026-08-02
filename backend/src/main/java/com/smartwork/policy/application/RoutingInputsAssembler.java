package com.smartwork.policy.application;

import com.smartwork.analysis.application.RecommendationAccess;
import com.smartwork.assignment.application.RankingService;
import com.smartwork.policy.domain.RoutingInputs;
import com.smartwork.workitem.application.WorkItemAssignment;
import com.smartwork.workitem.domain.RiskLevel;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Bridges stored state into the pure {@link RoutingInputs} record.
 *
 * <p>The counterpart of {@code GuardContextAssembler} in Phase 3 and
 * {@code CandidateFeatureAssembler} in Phase 5. Everything messy — reading the analysis
 * JSON, counting departments, deciding what "scope is clear" means — happens here, so the
 * engine stays a function of a record and the fourteen condition tests stay honest.
 */
@Service
public class RoutingInputsAssembler {

    /** Below this, the analysis is not confident enough to call the scope defined. */
    private static final BigDecimal SCOPE_CONFIDENCE_FLOOR = new BigDecimal("0.60");

    public RoutingInputs assemble(
            WorkItemAssignment.RoutingView item,
            RecommendationAccess.View analysis,
            Map<String, Object> analysisPayload,
            RankingService.Ranking ranking,
            PlanFacts plan) {

        boolean analysisMissingOrFailed = analysis == null || "FAILED".equals(analysis.status());
        BigDecimal confidence = analysis == null ? null : analysis.confidence();

        return RoutingInputs.builder()
                .analysisFailed(analysisMissingOrFailed)
                .injectionSuspected(flag(analysisPayload, "injection_suspected"))
                .requiresMoreInformation(flag(analysisPayload, "requires_more_information"))
                .confidence(confidence)

                .riskLevel(item.riskLevel() == null ? RiskLevel.LOW : item.riskLevel())
                .securityImpact(item.securitySensitive())
                .productionImpact(item.productionImpacting())
                .budgetImpact(item.budgetSensitive())
                .complianceImpact(item.complianceSensitive())
                .activePolicyException(plan.activePolicyException())

                .estimatedHours(item.estimatedHours())
                .estimatedTotalHours(plan.estimatedTotalHours())
                .leafItem(item.leaf())
                .affectedComponentCount(plan.affectedComponentCount())
                .departmentCount(plan.departmentCount())
                .teamCount(plan.teamCount())
                .hasUnresolvedDependency(plan.hasUnresolvedDependency())
                .crossTeamDependencies(plan.crossTeamDependencies())
                .requiresSeniorSkill(plan.requiresSeniorSkill())

                .requiresDecomposition(flag(analysisPayload, "requires_decomposition"))
                .decompositionConfidence(confidence)
                .templateMatched(plan.templateMatched())
                .processWork("PROCESS_WORK".equals(item.workFamily()))
                .scopeClear(confidence == null || confidence.compareTo(SCOPE_CONFIDENCE_FLOOR) >= 0)
                .acceptanceCriteriaComplete(plan.acceptanceCriteriaComplete())
                .nonStandardPlanGenerated(plan.nonStandardPlanGenerated())
                .epicScopeChangedAfterApproval(plan.epicScopeChangedAfterApproval())
                .classificationConfidence(plan.classificationConfidence())

                .noEligibleCandidate(ranking.noEligibleCandidate())
                .topScore(ranking.top().map(c -> c.totalScore()).orElse(null))
                .scoreGap(ranking.ranked().size() < 2 ? Integer.MAX_VALUE : ranking.scoreGap())
                .topCandidateWorkload(ranking.top().map(c -> c.workloadRatio()).orElse(null))
                .crossDepartmentPool(
                        ranking.tier() == com.smartwork.assignment.application
                                .CandidateDiscovery.Tier.ORGANIZATION_BY_SKILL)

                .escalatedByTeamLead(plan.escalatedByTeamLead())
                .escalatedByProcessOwner(plan.escalatedByProcessOwner())
                .crossTeamPriorityConflict(plan.crossTeamPriorityConflict())
                .build();
    }

    /**
     * Facts about the plan and the surrounding context that no single entity owns.
     * Supplied by the caller because they vary by entry point — routing a freshly analyzed
     * ticket knows different things than routing after an escalation.
     */
    public record PlanFacts(
            int departmentCount,
            int teamCount,
            int affectedComponentCount,
            BigDecimal estimatedTotalHours,
            boolean templateMatched,
            boolean nonStandardPlanGenerated,
            boolean acceptanceCriteriaComplete,
            boolean crossTeamDependencies,
            boolean hasUnresolvedDependency,
            boolean requiresSeniorSkill,
            boolean epicScopeChangedAfterApproval,
            boolean activePolicyException,
            boolean escalatedByTeamLead,
            boolean escalatedByProcessOwner,
            boolean crossTeamPriorityConflict,
            BigDecimal classificationConfidence) {

        /** A single, already-classified item with no generated plan around it. */
        public static PlanFacts singleItem(BigDecimal classificationConfidence) {
            return new PlanFacts(
                    1, 1, 0, null, true, false, true, false, false, false,
                    false, false, false, false, false, classificationConfidence);
        }
    }

    private static boolean flag(Map<String, Object> payload, String key) {
        return payload != null && Boolean.TRUE.equals(payload.get(key));
    }
}
