package com.smartwork.workflow.domain;

/**
 * Outcome of one guard.
 *
 * <p>A failure carries the guard's code so the API can return a machine-readable reason,
 * and a message written for the person who hit it - "Cannot start: DEV-458 must finish
 * first" is actionable in a way that "guard failed" is not.
 */
public record GuardResult(boolean passed, String guardCode, String message) {

    public static GuardResult pass(String guardCode) {
        return new GuardResult(true, guardCode, null);
    }

    public static GuardResult fail(String guardCode, String message) {
        return new GuardResult(false, guardCode, message);
    }
}
