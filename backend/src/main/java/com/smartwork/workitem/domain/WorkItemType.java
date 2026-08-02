package com.smartwork.workitem.domain;

import java.util.Set;

/**
 * What a work item <em>is</em>. One of three orthogonal classifications - do not collapse
 * it with {@link WorkFamily} (how it behaves) or {@link OriginType} (where it came from).
 */
public enum WorkItemType {

    /** What users call a "Ticket". Has a REQUESTER. */
    REQUEST,
    /** Unplanned disruption. Priority floor is HIGH. */
    INCIDENT,
    EPIC,
    FEATURE,
    STORY,
    BUG,
    /** The universal unit of assignable work. */
    TASK,
    TECHNICAL_DEBT,
    /** Time-boxed spike; produces findings, not shipped code. */
    RESEARCH,
    TEST,
    DOCUMENTATION,
    /** Leaf only. Cannot itself have children. */
    SUBTASK;

    private static final Set<WorkItemType> POINTED =
            Set.of(EPIC, FEATURE, STORY, BUG, TECHNICAL_DEBT);

    private static final Set<WorkItemType> ROOT_ONLY = Set.of(REQUEST, INCIDENT, EPIC);

    private static final Set<WorkItemType> REQUIRE_PROJECT =
            Set.of(EPIC, FEATURE, STORY, TECHNICAL_DEBT);

    private static final Set<WorkItemType> DEVELOPMENT =
            Set.of(EPIC, FEATURE, STORY, BUG, TASK, TECHNICAL_DEBT, RESEARCH, TEST, DOCUMENTATION);

    /** Story points are meaningless on a ticket or a subtask; only these carry them. */
    public boolean supportsStoryPoints() {
        return POINTED.contains(this);
    }

    /** These are always roots: a ticket, incident or epic never has a parent. */
    public boolean mustBeRoot() {
        return ROOT_ONLY.contains(this);
    }

    public boolean mustHaveParent() {
        return this == SUBTASK;
    }

    public boolean canHaveChildren() {
        return this != SUBTASK;
    }

    public boolean requiresProject() {
        return REQUIRE_PROJECT.contains(this);
    }

    public boolean isDevelopment() {
        return DEVELOPMENT.contains(this);
    }

    public boolean requiresAcceptanceCriteriaBeforeReady() {
        return this == STORY || this == FEATURE;
    }
}
