package com.smartwork.common.security;

/**
 * Axis 2 of authorization: what the caller is <em>to this particular item</em>.
 *
 * <p>Deliberately not stored as global roles. An employee who files a ticket is the
 * REQUESTER of that ticket, not "a Requester" in the organization; a user may own the
 * onboarding process for one ticket and own nothing else. Making either a global role
 * would grant rights over every ticket and every process.
 *
 * <p>Derived at evaluation time from the work item itself - see
 * {@code WorkItemAccessEvaluator.relationshipsOf}.
 */
public enum ResourceRole {
    REQUESTER,
    ASSIGNEE,
    PROCESS_OWNER,
    WATCHER,
    APPROVER,
    REVIEWER
}
