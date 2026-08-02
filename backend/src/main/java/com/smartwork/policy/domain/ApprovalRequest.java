package com.smartwork.policy.domain;

import com.smartwork.common.domain.TenantEntity;
import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One open question addressed to a reviewer or a scope. */
@Entity
@Table(name = "approval_requests")
public class ApprovalRequest extends TenantEntity {

    public enum Status {
        OPEN, APPROVED, REJECTED, ESCALATED, EXPIRED
    }

    @Column(name = "recommendation_id")
    private UUID recommendationId;

    @Column(name = "work_item_id", nullable = false, updatable = false)
    private UUID workItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "route", nullable = false, length = 32)
    private ApprovalRoute route;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.OPEN;

    @Column(name = "assigned_to_user_id")
    private UUID assignedToUserId;

    @Column(name = "assigned_to_scope", length = 64)
    private String assignedToScope;

    @Column(name = "reason_codes", length = 512)
    private String reasonCodes;

    @Column(name = "policy_exception", nullable = false)
    private boolean policyException;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decision_note", columnDefinition = "text")
    private String decisionNote;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "escalated_from_id")
    private UUID escalatedFromId;

    @Column(name = "escalation_reason", columnDefinition = "text")
    private String escalationReason;

    protected ApprovalRequest() {
    }

    public static ApprovalRequest open(
            UUID organizationId, UUID workItemId, UUID recommendationId,
            RoutingDecision decision, ApproverScope scope, Instant now) {

        ApprovalRequest request = new ApprovalRequest();
        request.setOrganizationId(organizationId);
        request.workItemId = workItemId;
        request.recommendationId = recommendationId;
        request.route = decision.route();
        request.reasonCodes = decision.reasonCodesCsv();
        request.policyException = decision.hasReason(ReasonCode.ACTIVE_POLICY_EXCEPTION);
        request.applyScope(scope);
        request.dueAt = now.plus(decision.suggestedSla());
        return request;
    }

    private void applyScope(ApproverScope scope) {
        this.assignedToScope = scope.serialize();
        // A person-scoped request also populates the indexed user column, so "my queue"
        // is an index lookup rather than a LIKE over the scope string.
        this.assignedToUserId = scope.findUserId().orElse(null);
    }

    public ApproverScope scope() {
        return ApproverScope.parse(assignedToScope);
    }

    /**
     * Records a decision. Rejects a second one rather than overwriting the first: two
     * people approving the same request should be a visible conflict, not a silent
     * last-write-wins.
     */
    public void decide(Status outcome, UUID actorId, String note, Instant now) {
        requireOpen();
        if (outcome == Status.OPEN) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "OPEN is not a decision");
        }
        this.status = outcome;
        this.decidedBy = actorId;
        this.decisionNote = note;
        this.decidedAt = now;
    }

    /** Closes this request as ESCALATED. The successor is created by the caller. */
    public void escalate(UUID actorId, String reason, Instant now) {
        requireOpen();
        if (reason == null || reason.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "An escalation must state why. Passing a decision upward without a reason "
                            + "leaves the next reviewer with less context than you had.");
        }
        this.status = Status.ESCALATED;
        this.decidedBy = actorId;
        this.decisionNote = reason;
        this.decidedAt = now;
    }

    public void expire(Instant now) {
        if (status == Status.OPEN) {
            this.status = Status.EXPIRED;
            this.decidedAt = now;
        }
    }

    public void markEscalatedFrom(UUID predecessorId, String reason) {
        this.escalatedFromId = predecessorId;
        this.escalationReason = reason;
    }

    private void requireOpen() {
        if (status != Status.OPEN) {
            throw new ApiException(ErrorCode.ALREADY_DECIDED,
                    "This request was already " + status.name().toLowerCase() + ".");
        }
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }

    public boolean isOverdue(Instant now) {
        return isOpen() && dueAt != null && now.isAfter(dueAt);
    }

    public UUID getWorkItemId() {
        return workItemId;
    }

    public UUID getRecommendationId() {
        return recommendationId;
    }

    public ApprovalRoute getRoute() {
        return route;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getAssignedToUserId() {
        return assignedToUserId;
    }

    public String getAssignedToScope() {
        return assignedToScope;
    }

    public String getReasonCodes() {
        return reasonCodes;
    }

    public boolean isPolicyException() {
        return policyException;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public UUID getEscalatedFromId() {
        return escalatedFromId;
    }

    public String getEscalationReason() {
        return escalationReason;
    }
}
