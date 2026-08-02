package com.smartwork.workitem.domain;

import java.util.Set;

/**
 * Where a work item <em>came from</em>.
 *
 * <p>The reason a Task does not need a Ticket: eight distinct origins, only one of which
 * involves a ticket at all. Drives the planned-versus-unplanned report and decides
 * whether an item needs its own AI analysis.
 */
public enum OriginType {

    USER_CREATED,
    JIRA_IMPORTED,
    TICKET_GENERATED,
    AI_GENERATED,
    PROCESS_TEMPLATE,
    PROJECT_PLANNING,
    INCIDENT_GENERATED,
    RECURRING;

    private static final Set<OriginType> PLANNED =
            Set.of(USER_CREATED, PROJECT_PLANNING, PROCESS_TEMPLATE, RECURRING);

    private static final Set<OriginType> UNPLANNED =
            Set.of(TICKET_GENERATED, INCIDENT_GENERATED);

    /** AI_GENERATED and JIRA_IMPORTED inherit this from their root ancestor. */
    public boolean isPlanned() {
        return PLANNED.contains(this);
    }

    public boolean isUnplanned() {
        return UNPLANNED.contains(this);
    }

    public boolean inheritsPlannedFromAncestor() {
        return this == AI_GENERATED || this == JIRA_IMPORTED;
    }

    /**
     * Recurring work carries its classification from the schedule that created it, so
     * re-analyzing every instance would spend tokens to reach the same answer.
     */
    public boolean skipsInitialAnalysis() {
        return this == RECURRING;
    }
}
