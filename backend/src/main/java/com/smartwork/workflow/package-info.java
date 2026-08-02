/**
 * The workflow engine: definitions, states, transitions and guards.
 *
 * <p>Depends only on {@code common}. Guards evaluate a {@link
 * com.smartwork.workflow.domain.GuardContext} assembled by the caller rather than
 * reaching into work items, users or sprints themselves - that seam is what keeps the
 * engine a pure state machine and lets it be unit-tested without a database.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Workflow",
        allowedDependencies = {"common"})
package com.smartwork.workflow;
