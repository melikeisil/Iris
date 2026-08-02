package com.smartwork.workflow.domain;

/**
 * A precondition on a workflow edge.
 *
 * <p>Guards are named in {@code workflow_transitions.guards} as a comma-separated list, so
 * an admin composes them per transition without a deployment. Two of them -
 * {@code NOT_SELF_REVIEW} and the absence of an {@code IN_PROGRESS -> DONE} edge - are the
 * mechanisms behind the promise that review and testing cannot be skipped.
 */
public interface TransitionGuard {

    String code();

    GuardResult evaluate(GuardContext context);
}
