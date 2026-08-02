package com.smartwork.workflow.domain;

import com.smartwork.common.security.ResourceRole;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Everything a guard is allowed to know, assembled by the caller.
 *
 * <p>This record is the seam that keeps the workflow module dependency-free. Guards do
 * not query work items, users or sprints; they evaluate pure predicates over facts handed
 * to them, which means the whole guard suite is unit-testable without a database and the
 * engine cannot accidentally acquire a dependency on half the system.
 *
 * @param actorRoles          the caller's global system roles
 * @param actorRelationships  what the caller is to <em>this</em> item (axis 2)
 * @param assigneeLevel       employee level of the prospective assignee, for
 *                            LEVEL_RESTRICTION
 * @param hasUnfinishedChildren whether any child is not in a terminal-success state
 * @param openDependencyKeys  reference keys of unfinished DEPENDS_ON targets, so the
 *                            failure message can name them
 */
public record GuardContext(
        UUID workItemId,
        String workItemType,
        String currentState,
        String targetState,

        UUID actorId,
        Set<String> actorRoles,
        Set<ResourceRole> actorRelationships,

        String reason,

        UUID assigneeId,
        UUID reporterId,
        UUID processOwnerId,
        String assigneeLevel,

        String acceptanceCriteria,
        String closureType,
        String priority,
        BigDecimal estimatedHours,

        boolean hasChildren,
        boolean hasUnfinishedChildren,
        Set<String> openDependencyKeys,

        UUID sprintId,
        boolean sprintActive,

        boolean securitySensitive,
        boolean productionImpacting,
        boolean budgetSensitive,
        boolean complianceSensitive) {

    public boolean actorIs(ResourceRole role) {
        return actorRelationships.contains(role);
    }

    public boolean actorHasRole(String role) {
        return actorRoles.contains(role);
    }

    public boolean actorIsAssignee() {
        return assigneeId != null && assigneeId.equals(actorId);
    }
}
