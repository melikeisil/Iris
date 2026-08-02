package com.smartwork.policy.api;

import com.smartwork.common.api.PageResponse;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.policy.application.ApprovalService;
import com.smartwork.policy.domain.ApprovalRoute;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Approvals", description = "Review queues, approval decisions and escalation")
public class ApprovalController {

    private final ApprovalService approvals;
    private final Clock clock;

    public ApprovalController(ApprovalService approvals, Clock clock) {
        this.approvals = approvals;
        this.clock = clock;
    }

    @GetMapping("/approvals/pending")
    @Operation(summary = "The caller's review queue",
            description = "Scoped to what the caller may see: their teams, their department, "
                    + "their role scopes, and anything addressed to them personally. Ordered by "
                    + "due date so the most urgent surfaces first.")
    public PageResponse<PolicyDtos.ApprovalRequestResponse> pending(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Instant now = clock.instant();
        Page<com.smartwork.policy.domain.ApprovalRequest> results =
                approvals.queueFor(principal, PageRequest.of(page, Math.min(size, 100)));

        return PageResponse.from(results, request ->
                PolicyDtos.ApprovalRequestResponse.from(request, now));
    }

    @GetMapping("/team-lead/review-queue")
    @Operation(summary = "Team lead review queue")
    public List<PolicyDtos.ApprovalRequestResponse> teamLeadQueue(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return render(approvals.queueFor(principal, ApprovalRoute.TEAM_LEAD_REVIEW));
    }

    @GetMapping("/process-owner/review-queue")
    @Operation(summary = "Process owner review queue")
    public List<PolicyDtos.ApprovalRequestResponse> processOwnerQueue(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return render(approvals.queueFor(principal, ApprovalRoute.PROCESS_OWNER_REVIEW));
    }

    @GetMapping("/manager/escalations")
    @Operation(summary = "Open escalations awaiting a manager")
    public List<PolicyDtos.ApprovalRequestResponse> escalations(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return render(approvals.openEscalations(principal));
    }

    @GetMapping("/approvals/overdue")
    @Operation(summary = "Requests past their SLA window")
    public List<PolicyDtos.ApprovalRequestResponse> overdue(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return render(approvals.overdue(principal));
    }

    @PostMapping("/approvals/{id}/approve")
    @Operation(summary = "Approve a request, optionally overriding the assignee",
            description = "Returns 409 ALREADY_DECIDED if someone got there first, 403 "
                    + "ROUTE_NOT_PERMITTED if the caller has no authority over the scope, and "
                    + "422 RECOMMENDATION_STALE if the work item changed since the analysis ran.")
    public PolicyDtos.ApprovalRequestResponse approve(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @RequestBody PolicyDtos.ApproveRequest body) {

        return PolicyDtos.ApprovalRequestResponse.from(
                approvals.approve(principal, id, new ApprovalService.ApproveCommand(
                        body.assigneeIdOverride(), body.note(), body.version())),
                clock.instant());
    }

    @PostMapping("/approvals/{id}/reject")
    @Operation(summary = "Reject a request with a required reason")
    public PolicyDtos.ApprovalRequestResponse reject(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @RequestBody PolicyDtos.RejectRequest body) {

        return PolicyDtos.ApprovalRequestResponse.from(
                approvals.reject(principal, id, body.reason()), clock.instant());
    }

    @PostMapping("/approvals/{id}/escalate")
    @Operation(summary = "Escalate to a manager",
            description = "Closes this request as ESCALATED and opens a linked "
                    + "MANAGER_APPROVAL request. A reason is required — passing a decision "
                    + "upward without one leaves the next reviewer with less context than you had.")
    public PolicyDtos.ApprovalRequestResponse escalate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @RequestBody PolicyDtos.EscalateRequest body) {

        return PolicyDtos.ApprovalRequestResponse.from(
                approvals.escalate(principal, id, body.reason()), clock.instant());
    }

    private List<PolicyDtos.ApprovalRequestResponse> render(
            List<com.smartwork.policy.domain.ApprovalRequest> requests) {
        Instant now = clock.instant();
        return requests.stream()
                .map(request -> PolicyDtos.ApprovalRequestResponse.from(request, now))
                .toList();
    }
}
