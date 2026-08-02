package com.smartwork.workitem.application;

import java.util.UUID;

/**
 * The system-initiated assignment path, exposed to the policy module.
 *
 * <p>Separate from {@code WorkItemTransitionService}'s actor-driven assignment because the
 * authorization question is different. When a Team Lead assigns someone, the check is "may
 * <em>you</em> place this work". When the policy engine assigns someone, there is no
 * "you" — the check has already happened, upstream, in the eligibility filter and the
 * fourteen auto-assign conditions. Routing that through the actor-permission path would
 * mean inventing a synthetic principal with enough rights to do anything, which is a worse
 * idea than a narrow, explicitly-named system entry point.
 */
public interface WorkItemAssignment {

    /**
     * Sets the assignee without an actor permission check.
     *
     * <p>Still validates that the item and user exist and belong to the organization —
     * "no actor check" is not "no checks".
     */
    void assignBySystem(UUID organizationId, UUID workItemId, UUID assigneeId);

    /** Everything routing needs to know about an item, without exposing the entity. */
    RoutingView routingView(UUID organizationId, UUID workItemId);

    record RoutingView(
            UUID workItemId,
            UUID organizationId,
            String referenceKey,
            String status,
            UUID teamId,
            UUID departmentId,
            UUID projectId,
            UUID processOwnerId,
            UUID reporterId,
            UUID assigneeId,
            /** Drives the INTERN mentor restriction: no reviewer means no supervision. */
            UUID reviewerId,
            String workItemType,
            String workFamily,
            com.smartwork.workitem.domain.Priority priority,
            com.smartwork.workitem.domain.RiskLevel riskLevel,
            java.math.BigDecimal estimatedHours,
            java.time.LocalDate dueDate,
            boolean securitySensitive,
            boolean productionImpacting,
            boolean budgetSensitive,
            boolean complianceSensitive,
            boolean leaf,
            java.time.Instant updatedAt) {
    }
}
