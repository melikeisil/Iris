package com.smartwork.process.domain;

import com.smartwork.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * PROCESS_OWNER as a resource-scoped relationship (docs/03), not a system role.
 *
 * <p>The demo seeds the HR Operations Lead as a plain {@code EMPLOYEE} who nonetheless owns
 * the onboarding process — that is the whole point of the two-axis model, and this table is
 * where the second axis lives.
 *
 * <p>{@code SUBTREE} scope is what lets one row cover an onboarding request and all nine of
 * its generated children.
 *
 * <p>Named {@code ProcessOwnerAssignment} rather than {@code ProcessOwner} because the row
 * is the grant, not the person.
 */
@Entity
@Table(name = "process_owners")
public class ProcessOwnerAssignment extends TenantEntity {

    public enum Scope {
        WORK_ITEM, PROCESS, SUBTREE
    }

    @Column(name = "work_item_id")
    private UUID workItemId;

    @Column(name = "process_definition_id")
    private UUID processDefinitionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 16)
    private Scope scope = Scope.SUBTREE;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt = Instant.now();

    protected ProcessOwnerAssignment() {
    }

    public static ProcessOwnerAssignment forWorkItem(
            UUID organizationId, UUID workItemId, UUID userId, Scope scope) {
        ProcessOwnerAssignment assignment = new ProcessOwnerAssignment();
        assignment.setOrganizationId(organizationId);
        assignment.workItemId = workItemId;
        assignment.userId = userId;
        assignment.scope = scope;
        return assignment;
    }

    public static ProcessOwnerAssignment forProcess(
            UUID organizationId, UUID processDefinitionId, UUID userId) {
        ProcessOwnerAssignment assignment = new ProcessOwnerAssignment();
        assignment.setOrganizationId(organizationId);
        assignment.processDefinitionId = processDefinitionId;
        assignment.userId = userId;
        assignment.scope = Scope.PROCESS;
        return assignment;
    }

    public UUID getWorkItemId() {
        return workItemId;
    }

    public UUID getProcessDefinitionId() {
        return processDefinitionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Scope getScope() {
        return scope;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
