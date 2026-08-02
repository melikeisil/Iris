package com.smartwork.assignment.application;

import java.util.UUID;

/**
 * Write contract for assignment history, exposed to the policy module.
 *
 * <p>Narrow on purpose. The policy engine needs to say "this person got this item, this
 * way, for this reason" and nothing else — it has no business reading the fairness counts
 * its own writes feed.
 */
public interface AssignmentHistory {

    enum Source {
        AUTO, TEAM_LEAD, PROCESS_OWNER, MANAGER, SELF
    }

    record Entry(
            UUID organizationId,
            UUID workItemId,
            UUID assigneeId,
            Source source,
            UUID recommendationId,
            UUID assignedBy,
            String reason) {
    }

    /**
     * Records a new assignment, closing any currently active one for the same item.
     * Closing rather than deleting: the previous holder's fourteen-day fairness count
     * must still include work that has since moved on.
     */
    void record(Entry entry);

    /** How many items this user was assigned in the window, for the fairness score. */
    int countAssignedSince(UUID userId, java.time.Instant since);
}
