package com.smartwork.assignment.domain;

import com.smartwork.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Assignment history, distinct from {@code work_items.assignee_id} (the current holder).
 *
 * <p>Kept separate because the fairness score counts assignments in the last 14 days, and
 * an item that was assigned to someone and then moved away still consumed their attention.
 * A single {@code assignee_id} column cannot answer that question after the reassignment.
 */
@Entity
@Table(name = "assignments")
public class Assignment extends TenantEntity {

    public enum Source {
        AUTO, TEAM_LEAD, PROCESS_OWNER, MANAGER, SELF
    }

    @Column(name = "work_item_id", nullable = false, updatable = false)
    private UUID workItemId;

    @Column(name = "assignee_id", nullable = false, updatable = false)
    private UUID assigneeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private Source source;

    @Column(name = "recommendation_id")
    private UUID recommendationId;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    protected Assignment() {
    }

    public Assignment(UUID organizationId, UUID workItemId, UUID assigneeId, Source source) {
        super(organizationId);
        this.workItemId = workItemId;
        this.assigneeId = assigneeId;
        this.source = source;
    }

    public void end(Instant when) {
        this.active = false;
        this.endedAt = when;
    }

    public UUID getWorkItemId() {
        return workItemId;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public Source getSource() {
        return source;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setRecommendationId(UUID recommendationId) {
        this.recommendationId = recommendationId;
    }

    public void setAssignedBy(UUID assignedBy) {
        this.assignedBy = assignedBy;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
