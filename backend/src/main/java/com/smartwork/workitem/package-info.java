/**
 * The Work Item aggregate: one table for tickets, incidents, epics, features, stories,
 * bugs, tasks and subtasks, plus typed links, comments, watchers and required skills.
 *
 * <p>Depends on {@code workflow::application} for transition validation, and on the
 * organizational modules for the identifiers it carries.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Work Item",
        allowedDependencies = {"common", "workflow::application", "workflow::domain",
                "identity::application", "organization::application", "team::application",
                "project::application"})
package com.smartwork.workitem;
