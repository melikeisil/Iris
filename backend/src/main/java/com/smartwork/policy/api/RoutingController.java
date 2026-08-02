package com.smartwork.policy.api;

import com.smartwork.assignment.domain.CandidateScore;
import com.smartwork.assignment.domain.ExclusionReason;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.policy.application.RoutingService;
import com.smartwork.policy.application.WorkItemRoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The public entry point for routing.
 *
 * <p>Returns the whole outcome in one response — the decision and its reason codes, the
 * ranked candidates with every score component, the people who were excluded and why, and
 * either the assignee or the approval request that was opened. That is deliberate: the
 * value of this pipeline is its explainability, and an endpoint that answered only
 * "TEAM_LEAD_REVIEW" would make the caller issue three more requests to find out why.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Routing", description = "Rank candidates and apply the policy engine")
public class RoutingController {

    private final WorkItemRoutingService routing;
    private final Clock clock;

    public RoutingController(WorkItemRoutingService routing, Clock clock) {
        this.routing = routing;
        this.clock = clock;
    }

    @PostMapping("/work-items/{id}/route")
    @Operation(summary = "Rank candidates and route the work item",
            description = """
                    Runs the full pipeline: discover candidates, apply eligibility filters,
                    score the seven components, evaluate the policy, then either assign
                    automatically or open an approval request in the correct queue.

                    Requires a prior analysis — call POST /work-items/{id}/analyze first,
                    otherwise this returns 400, because a routing decision built on no
                    analysis would look like a policy judgement and be nothing of the sort.
                    """)
    public RoutingResponse route(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {

        RoutingService.Outcome outcome = routing.route(principal, id);

        return new RoutingResponse(
                id,
                PolicyDtos.RoutingDecisionResponse.from(outcome.decision()),
                outcome.assignedWithoutHuman(),
                outcome.assignedTo().map(CandidateScore::candidateUserId).orElse(null),
                outcome.assignedTo().map(CandidateScore::fullName).orElse(null),
                outcome.approvalRequest()
                        .map(request -> PolicyDtos.ApprovalRequestResponse.from(request, clock.instant()))
                        .orElse(null),
                outcome.ranking().tier().name(),
                outcome.ranking().ranked().stream().map(CandidateResponse::from).toList(),
                outcome.ranking().excluded().entrySet().stream()
                        .map(e -> new ExcludedCandidateResponse(
                                e.getKey(), e.getValue().name(), e.getValue().explanation()))
                        .toList());
    }

    public record RoutingResponse(
            UUID workItemId,
            PolicyDtos.RoutingDecisionResponse decision,
            boolean assignedWithoutHuman,
            UUID assigneeId,
            String assigneeName,
            PolicyDtos.ApprovalRequestResponse approvalRequest,
            String candidatePoolTier,
            List<CandidateResponse> candidates,
            List<ExcludedCandidateResponse> excludedCandidates) {
    }

    /** Every component is exposed separately — the breakdown is the product. */
    public record CandidateResponse(
            UUID candidateUserId,
            String fullName,
            int rank,
            int totalScore,
            Map<String, Integer> scores,
            BigDecimal workloadRatio,
            String explanation) {

        static CandidateResponse from(CandidateScore score) {
            return new CandidateResponse(
                    score.candidateUserId(),
                    score.fullName(),
                    score.rank(),
                    score.totalScore(),
                    Map.of(
                            "skill", score.components().skill(),
                            "capacity", score.components().capacity(),
                            "team", score.components().team(),
                            "experience", score.components().experience(),
                            "component", score.components().component(),
                            "deadline", score.components().deadline(),
                            "fairness", score.components().fairness()),
                    score.workloadRatio(),
                    score.explanation());
        }
    }

    /**
     * Why an obvious person was skipped. docs/08 §3 calls this one of the most useful
     * explainability features in the product, and it only works if it reaches the client.
     */
    public record ExcludedCandidateResponse(UUID candidateUserId, String reason, String meaning) {

        static ExcludedCandidateResponse of(UUID userId, ExclusionReason reason) {
            return new ExcludedCandidateResponse(userId, reason.name(), reason.explanation());
        }
    }
}
