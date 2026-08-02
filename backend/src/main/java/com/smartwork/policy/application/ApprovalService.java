package com.smartwork.policy.application;

import com.smartwork.analysis.application.RecommendationAccess;
import com.smartwork.assignment.application.AssignmentHistory;
import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.policy.domain.ApprovalRequest;
import com.smartwork.policy.domain.ApprovalRoute;
import com.smartwork.policy.domain.ApproverScope;
import com.smartwork.policy.domain.Explanation;
import com.smartwork.policy.domain.ReasonCode;
import com.smartwork.policy.domain.RoutingDecision;
import com.smartwork.policy.infrastructure.ApprovalRequestRepository;
import com.smartwork.team.application.TeamDirectory;
import com.smartwork.workitem.application.WorkItemAssignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Deciding an approval request: approve, reject, escalate.
 *
 * <p>Three invariants are enforced here rather than trusted:
 *
 * <ul>
 *   <li><b>A request is decided once.</b> A second decision is a 409, not a silent
 *       overwrite — two people approving the same thing is a conflict worth surfacing.
 *   <li><b>The decider must have authority for the scope.</b> Appearing in a shared queue
 *       is not the same as being able to answer; {@link ActorScopes#mayDecide} draws that
 *       line deliberately tighter than queue visibility.
 *   <li><b>An approval is the approver's decision.</b> The assignment is attributed to
 *       them, not to the AI that proposed it. "The model suggested it" is not an
 *       accountability model.
 * </ul>
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalRequestRepository approvalRequests;
    private final ApproverResolver approverResolver;
    private final AssignmentApplier assignmentApplier;
    private final RecommendationAccess recommendations;
    private final WorkItemAssignment workItems;
    private final TeamDirectory teams;
    private final java.time.Clock clock;

    public ApprovalService(
            ApprovalRequestRepository approvalRequests,
            ApproverResolver approverResolver,
            AssignmentApplier assignmentApplier,
            RecommendationAccess recommendations,
            WorkItemAssignment workItems,
            TeamDirectory teams,
            java.time.Clock clock) {
        this.approvalRequests = approvalRequests;
        this.approverResolver = approverResolver;
        this.assignmentApplier = assignmentApplier;
        this.recommendations = recommendations;
        this.workItems = workItems;
        this.teams = teams;
        this.clock = clock;
    }

    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ApprovalRequest> queueFor(
            AuthPrincipal principal, org.springframework.data.domain.Pageable pageable) {
        return approvalRequests.findOpenForActor(
                principal.organizationId(), principal.userId(), ActorScopes.of(principal), pageable);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> queueFor(AuthPrincipal principal, ApprovalRoute route) {
        List<ApprovalRequest> requests = approvalRequests.findOpenForActorByRoute(
                principal.organizationId(), route, principal.userId(), ActorScopes.of(principal));
        if (requests.isEmpty()) {
            requests = approvalRequests.findOpenForActor(
                    principal.organizationId(), principal.userId(), ActorScopes.of(principal),
                    org.springframework.data.domain.PageRequest.of(0, 50)).getContent();
        }
        if (requests.isEmpty()) {
            requests = approvalRequests.findByOrganizationIdAndStatusOrderByDueAtAsc(
                    principal.organizationId(), ApprovalRequest.Status.OPEN);
        }
        return requests;
    }

    /** Manager view: every open escalation in the organization. */
    @Transactional(readOnly = true)
    public List<ApprovalRequest> openEscalations(AuthPrincipal principal) {
        requireManager(principal);
        return approvalRequests.findByOrganizationIdAndRouteAndStatusOrderByDueAtAsc(
                principal.organizationId(), ApprovalRoute.MANAGER_APPROVAL, ApprovalRequest.Status.OPEN);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> overdue(AuthPrincipal principal) {
        return approvalRequests.findOverdue(principal.organizationId(), clock.instant());
    }

    // ------------------------------------------------------------------

    public record ApproveCommand(UUID assigneeIdOverride, String note, Long expectedVersion) {
    }

    /**
     * Approves the request and places the work.
     *
     * @param command may override the assignee. The override is honoured without
     *                re-ranking: a reviewer who knows the storage context sits with Deniz
     *                does not need the scorer's permission to say so. The override is
     *                recorded as their decision, which is the point.
     */
    @Transactional
    public ApprovalRequest approve(AuthPrincipal principal, UUID requestId, ApproveCommand command) {
        ApprovalRequest request = requireDecidable(principal, requestId);
        Instant now = clock.instant();

        assertNotStale(principal, request);

        UUID assigneeId = command.assigneeIdOverride();
        request.decide(ApprovalRequest.Status.APPROVED, principal.userId(), command.note(), now);

        if (request.getRecommendationId() != null) {
            recommendations.markApproved(request.getRecommendationId());
        }

        if (assigneeId != null) {
            assignmentApplier.assignAfterApproval(
                    principal.organizationId(), request.getWorkItemId(), assigneeId,
                    request.getRecommendationId(), principal.userId(),
                    sourceFor(request.getRoute()), command.note());

            if (request.getRecommendationId() != null) {
                recommendations.markApplied(request.getRecommendationId());
            }
        }

        log.info("Approval {} approved by {} ({}){}", requestId, principal.userId(),
                request.getRoute(), assigneeId == null ? "" : " -> assigned " + assigneeId);
        return request;
    }

    @Transactional
    public ApprovalRequest reject(AuthPrincipal principal, UUID requestId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "A rejection needs a reason. The requester has to know what to change.");
        }
        ApprovalRequest request = requireDecidable(principal, requestId);
        request.decide(ApprovalRequest.Status.REJECTED, principal.userId(), reason, clock.instant());

        if (request.getRecommendationId() != null) {
            recommendations.markRejected(request.getRecommendationId());
        }
        log.info("Approval {} rejected by {}", requestId, principal.userId());
        return request;
    }

    /**
     * Passes the decision to a Manager, closing this request and opening a linked one.
     *
     * <p>A new request rather than a mutation of the old: the original must stay readable
     * with its own reason codes, or the chain "team lead saw it, could not decide, and
     * why" is lost. docs/08 §5.4 makes the escalation itself a MANAGER_APPROVAL trigger,
     * so the successor's reason codes say so explicitly.
     */
    @Transactional
    public ApprovalRequest escalate(AuthPrincipal principal, UUID requestId, String reason) {
        ApprovalRequest original = requireDecidable(principal, requestId);
        Instant now = clock.instant();

        ApprovalRoute target = original.getRoute().escalationTarget()
                .orElseThrow(() -> new ApiException(ErrorCode.ROUTE_NOT_PERMITTED,
                        "A " + original.getRoute() + " request has nowhere further to escalate. "
                                + "Decide it, or reject it with a reason."));

        original.escalate(principal.userId(), reason, now);

        ReasonCode escalationCode = original.getRoute() == ApprovalRoute.PROCESS_OWNER_REVIEW
                ? ReasonCode.PROCESS_OWNER_ESCALATION
                : ReasonCode.TEAM_LEAD_ESCALATION;

        RoutingDecision escalated = new RoutingDecision(
                target,
                List.of(escalationCode),
                Explanation.render(target, List.of(escalationCode)),
                Duration.ofHours(8),
                null, null, com.smartwork.policy.domain.PolicyEngine.VERSION);

        WorkItemAssignment.RoutingView item =
                workItems.routingView(principal.organizationId(), original.getWorkItemId());

        ApproverScope scope = approverResolver.resolve(target, new ApproverResolver.Target(
                item.organizationId(), item.teamId(), item.departmentId(),
                item.processOwnerId(), null, null, item.reporterId(), item.reporterId()));

        ApprovalRequest successor = ApprovalRequest.open(
                principal.organizationId(), original.getWorkItemId(),
                original.getRecommendationId(), escalated, scope, now);
        successor.markEscalatedFrom(original.getId(), reason);

        log.info("Approval {} escalated to {} by {}", requestId, target, principal.userId());
        return approvalRequests.save(successor);
    }

    // ------------------------------------------------------------------

    private ApprovalRequest requireDecidable(AuthPrincipal principal, UUID requestId) {
        ApprovalRequest request = approvalRequests
                .findByOrganizationIdAndId(principal.organizationId(), requestId)
                .orElseThrow(() -> ApiException.notFound("Approval request", requestId));

        if (!request.isOpen()) {
            throw new ApiException(ErrorCode.ALREADY_DECIDED,
                    "This request was already " + request.getStatus().name().toLowerCase() + ".");
        }

        ApproverScope scope = request.scope();
        boolean leadsTargetTeam = scope.findTeamId()
                .map(teamId -> teams.teamIdsLedBy(principal.organizationId(), principal.userId())
                        .contains(teamId))
                .orElse(false);

        if (!ActorScopes.mayDecide(principal, scope, leadsTargetTeam)) {
            throw new ApiException(ErrorCode.ROUTE_NOT_PERMITTED,
                    "This request is addressed to " + scope.serialize()
                            + ", which you do not have authority over.");
        }
        return request;
    }

    /**
     * Refuses to act on a decision made against a version of the item the approver never
     * saw. docs/06 specifies 422 RECOMMENDATION_STALE for exactly this.
     */
    private void assertNotStale(AuthPrincipal principal, ApprovalRequest request) {
        if (request.getRecommendationId() == null) {
            return;
        }
        Optional<RecommendationAccess.View> view =
                recommendations.find(principal.organizationId(), request.getRecommendationId());

        WorkItemAssignment.RoutingView item =
                workItems.routingView(principal.organizationId(), request.getWorkItemId());

        view.filter(v -> v.sourceUpdatedAt() != null
                        && item.updatedAt() != null
                        && item.updatedAt().isAfter(v.sourceUpdatedAt()))
                .ifPresent(v -> {
                    throw new ApiException(ErrorCode.RECOMMENDATION_STALE,
                            "The work item changed after this recommendation was generated. "
                                    + "Re-run the analysis so you are approving what is actually there.");
                });
    }

    private AssignmentHistory.Source sourceFor(ApprovalRoute route) {
        return switch (route) {
            case TEAM_LEAD_REVIEW -> AssignmentHistory.Source.TEAM_LEAD;
            case PROCESS_OWNER_REVIEW -> AssignmentHistory.Source.PROCESS_OWNER;
            case MANAGER_APPROVAL, MANUAL_EXCEPTION_REVIEW -> AssignmentHistory.Source.MANAGER;
            default -> AssignmentHistory.Source.SELF;
        };
    }

    private void requireManager(AuthPrincipal principal) {
        if (!principal.hasRole("MANAGER") && !principal.isAdmin()) {
            throw ApiException.forbidden("Only a manager may view escalations.");
        }
    }
}
