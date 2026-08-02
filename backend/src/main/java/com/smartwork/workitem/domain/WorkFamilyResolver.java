package com.smartwork.workitem.domain;

/**
 * Derives the work family from type, origin and parent context.
 *
 * <p>A TASK is the interesting case: the same type is service work when it came out of a
 * ticket, process work when it came out of a template, and development work when it sits
 * under a story. The family is what selects the workflow profile, so getting this wrong
 * puts the item on the wrong board.
 */
public final class WorkFamilyResolver {

    private WorkFamilyResolver() {
    }

    /**
     * @param parentFamily family of the parent, or null for a root item
     */
    public static WorkFamily resolve(WorkItemType type, OriginType origin, WorkFamily parentFamily) {
        // Types that determine their own family regardless of context.
        switch (type) {
            case REQUEST -> {
                return WorkFamily.SERVICE_WORK;
            }
            case INCIDENT -> {
                return WorkFamily.INCIDENT_WORK;
            }
            case EPIC, FEATURE, STORY, TECHNICAL_DEBT -> {
                return WorkFamily.DEVELOPMENT_WORK;
            }
            default -> {
                // fall through
            }
        }

        // A subtask always belongs to whatever its parent belongs to.
        if (type == WorkItemType.SUBTASK && parentFamily != null) {
            return parentFamily;
        }

        // BUG is development work even when it was raised from a ticket: the fix is code,
        // and it belongs on the development board with the rest of the code.
        if (type == WorkItemType.BUG) {
            return WorkFamily.DEVELOPMENT_WORK;
        }

        // RESEARCH, TEST and DOCUMENTATION follow their parent when they have one,
        // because a spike under an epic is development work while a standalone one is not.
        if (type == WorkItemType.RESEARCH || type == WorkItemType.TEST
                || type == WorkItemType.DOCUMENTATION) {
            return parentFamily != null ? parentFamily : WorkFamily.DEVELOPMENT_WORK;
        }

        // TASK: origin decides.
        return switch (origin) {
            case TICKET_GENERATED -> WorkFamily.SERVICE_WORK;
            case PROCESS_TEMPLATE -> WorkFamily.PROCESS_WORK;
            case INCIDENT_GENERATED -> WorkFamily.INCIDENT_WORK;
            case RECURRING -> WorkFamily.GENERAL_OPERATIONAL_WORK;
            case PROJECT_PLANNING -> WorkFamily.DEVELOPMENT_WORK;
            case USER_CREATED, JIRA_IMPORTED, AI_GENERATED ->
                    parentFamily != null ? parentFamily : WorkFamily.GENERAL_OPERATIONAL_WORK;
        };
    }

    /**
     * The workflow profile a family and type should use.
     *
     * <p>BUG and RESEARCH have their own profiles even though both are development work -
     * a bug goes through root-cause analysis and QA, a spike through findings review, and
     * forcing either into the generic development flow loses the stage that matters.
     */
    public static String workflowCodeFor(WorkItemType type, WorkFamily family) {
        if (type == WorkItemType.BUG) {
            return "BUG";
        }
        if (type == WorkItemType.RESEARCH) {
            return "RESEARCH";
        }
        return family.defaultWorkflowCode();
    }
}
