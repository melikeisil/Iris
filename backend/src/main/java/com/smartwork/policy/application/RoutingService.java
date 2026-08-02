package com.smartwork.policy.application;

import com.smartwork.analysis.application.RecommendationAccess;
import com.smartwork.assignment.application.PolicyResolver;
import com.smartwork.assignment.application.RankingService;
import com.smartwork.assignment.domain.CandidateScore;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.policy.domain.ApprovalRequest;
import com.smartwork.policy.domain.ApprovalRoute;
import com.smartwork.policy.domain.ApproverScope;
import com.smartwork.policy.domain.PolicyEngine;
import com.smartwork.policy.domain.PolicyThresholds;
import com.smartwork.policy.domain.RoutingDecision;
import com.smartwork.policy.domain.RoutingInputs;
import com.smartwork.policy.infrastructure.ApprovalRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Step 5 of the pipeline in docs/08 §1: take a ranking, decide the route, and either
 * assign or open an approval request.
 *
 * <p>The ordering inside {@link #route} is worth noting. The decision is computed and
 * <em>recorded</em> before anything is assigned, so a failure during assignment leaves a
 * stored decision explaining what should have happened rather than an item with no
 * history. A recommendation with a routing decision and no assignment is diagnosable; an
 * assignment with no recorded reason is not.
 */
@Service
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    private final RankingService rankingService;
    private final ApproverResolver approverResolver;
    private final ApprovalRequestRepository approvalRequests;
    private final AssignmentApplier assignmentApplier;
    private final RecommendationAccess recommendations;
    private final Clock clock;

    public RoutingService(
            RankingService rankingService,
            ApproverResolver approverResolver,
            ApprovalRequestRepository approvalRequests,
            AssignmentApplier assignmentApplier,
            RecommendationAccess recommendations,
            Clock clock) {
        this.rankingService = rankingService;
        this.approverResolver = approverResolver;
        this.approvalRequests = approvalRequests;
        this.assignmentApplier = assignmentApplier;
        this.recommendations = recommendations;
        this.clock = clock;
    }

    public record Outcome(
            RoutingDecision decision,
            Optional<ApprovalRequest> approvalRequest,
            Optional<CandidateScore> assignedTo,
            RankingService.Ranking ranking) {

        public boolean assignedWithoutHuman() {
            return decision.isAutoAssign() && assignedTo.isPresent();
        }
    }

    /**
     * Routes an already-ranked item.
     *
     * @param recommendationId the ASSIGNMENT recommendation this decision belongs to;
     *                         candidates and the decision are both stored against it
     */
    @Transactional
    public Outcome route(
            AuthPrincipal principal,
            UUID workItemId,
            UUID recommendationId,
            RoutingInputs inputs,
            RankingService.Ranking ranking,
            ApproverResolver.Target target,
            Optional<PolicyResolver.PolicyView> policy) {

        Instant now = clock.instant();
        PolicyThresholds thresholds = policy.map(RoutingService::thresholdsOf)
                .orElseGet(PolicyThresholds::defaults);

        RoutingDecision decision = new PolicyEngine(thresholds).decide(inputs);

        rankingService.persist(recommendationId, ranking);
        recommendations.recordRouting(
                recommendationId, decision.route().name(), decision.reasonCodesCsv(),
                decision.policyId(), decision.policyVersion(), decision.engineVersion());

        if (decision.isAutoAssign()) {
            Optional<CandidateScore> top = ranking.top();
            if (top.isEmpty()) {
                // Defensive: the engine's condition 10 already blocks this. If it ever
                // fires, assigning nobody silently would be the worst possible response.
                log.error("AUTO_ASSIGN with no candidate for work item {} — routing to team lead instead",
                        workItemId);
                return openRequest(workItemId, recommendationId, degradeToTeamLead(decision),
                        target, ranking, now);
            }
            assignmentApplier.assignAutomatically(
                    principal, workItemId, top.get().candidateUserId(), recommendationId, decision);
            log.info("Auto-assigned work item {} to {} (score {})",
                    workItemId, top.get().candidateUserId(), top.get().totalScore());
            return new Outcome(decision, Optional.empty(), top, ranking);
        }

        return openRequest(workItemId, recommendationId, decision, target, ranking, now);
    }

    private Outcome openRequest(
            UUID workItemId, UUID recommendationId, RoutingDecision decision,
            ApproverResolver.Target target, RankingService.Ranking ranking, Instant now) {

        ApproverScope scope = approverResolver.resolve(decision.route(), target);

        // Re-routing the same item to the same route closes the previous request rather
        // than opening a second: the unique index on (work_item_id, route) where status is
        // OPEN would reject it anyway, and a reviewer holding two identical requests has
        // no way to tell which one matters.
        approvalRequests
                .findByWorkItemIdAndRouteAndStatus(workItemId, decision.route(), ApprovalRequest.Status.OPEN)
                .ifPresent(existing -> existing.expire(now));

        ApprovalRequest request = approvalRequests.save(ApprovalRequest.open(
                target.organizationId(), workItemId, recommendationId, decision, scope, now));

        recommendations.markAwaitingApproval(recommendationId);

        log.info("Work item {} routed to {} ({}), addressed to {}",
                workItemId, decision.route(), decision.reasonCodesCsv(), scope.serialize());

        return new Outcome(decision, Optional.of(request), Optional.empty(), ranking);
    }

    private RoutingDecision degradeToTeamLead(RoutingDecision original) {
        return new RoutingDecision(
                ApprovalRoute.TEAM_LEAD_REVIEW,
                java.util.List.of(com.smartwork.policy.domain.ReasonCode.NO_ELIGIBLE_CANDIDATE),
                "No candidate was available at assignment time despite the policy permitting "
                        + "automatic assignment; a team lead must place this work.",
                java.time.Duration.ofHours(12),
                original.policyId(), original.policyVersion(), original.engineVersion());
    }

    static PolicyThresholds thresholdsOf(PolicyResolver.PolicyView policy) {
        return new PolicyThresholds(
                policy.id(),
                policy.version(),
                policy.minAutoAssignConfidence(),
                policy.maxAutoAssignWorkload(),
                policy.maxAutoAssignHours(),
                policy.autoAssignMaxRisk(),
                policy.minAutoAssignScore(),
                policy.minScoreGap(),
                policy.allowCrossDepartmentAuto());
    }
}
