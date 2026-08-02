package com.smartwork.workflow.domain;

/**
 * Board column a state maps to.
 *
 * <p>This is what lets My Work render items from six different workflow profiles side by
 * side: a BUG in {@code QA} and a STORY in {@code TESTING} both land in the Testing column
 * without the board knowing anything about either profile.
 */
public enum StateCategory {
    TODO,
    IN_PROGRESS,
    BLOCKED,
    REVIEW,
    TESTING,
    DONE
}
