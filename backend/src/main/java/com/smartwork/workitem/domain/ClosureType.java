package com.smartwork.workitem.domain;

/**
 * Who confirms the work is actually done.
 *
 * <p>If the named verifier does not act within the configured window a reminder is
 * raised; an item is never auto-closed against a non-{@code AUTO_CLOSE} policy. Closing
 * work on someone's behalf because they were slow is how a system stops being trusted.
 */
public enum ClosureType {
    AUTO_CLOSE,
    REQUESTER_VERIFICATION,
    PROCESS_OWNER_VERIFICATION,
    TEAM_LEAD_VERIFICATION
}
