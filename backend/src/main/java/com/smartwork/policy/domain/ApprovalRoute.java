package com.smartwork.policy.domain;

/**
 * The six outcomes of the policy engine (docs/08 §5).
 *
 * <p>Declaration order is the evaluation order, most restrictive first, and that is a
 * safety property rather than a stylistic one: the engine tests routes top-down and takes
 * the first match, so no combination of conditions can produce a laxer outcome than the
 * strictest applicable trigger. A CRITICAL-risk item with low confidence must reach
 * MANAGER_APPROVAL, never TEAM_LEAD_REVIEW, however many of the lesser triggers also fire.
 *
 * <p>{@code PolicyOrderingTest} asserts the ordinal order matches this reasoning, so
 * reordering the constants breaks the build rather than quietly weakening the model.
 */
public enum ApprovalRoute {

    /** Analysis failed or an injection is suspected: a human must look before anything else. */
    MANUAL_EXCEPTION_REVIEW(0),

    /** The AI could not proceed without answers; goes back to the requester. */
    REQUEST_MORE_INFORMATION(1),

    MANAGER_APPROVAL(2),

    PROCESS_OWNER_REVIEW(3),

    TEAM_LEAD_REVIEW(4),

    /** No human involved. Only reachable when all fourteen conditions hold. */
    AUTO_ASSIGN(5);

    private final int severityRank;

    ApprovalRoute(int severityRank) {
        this.severityRank = severityRank;
    }

    /** Lower is stricter. */
    public int severityRank() {
        return severityRank;
    }

    public boolean isStricterThan(ApprovalRoute other) {
        return severityRank < other.severityRank;
    }

    public boolean requiresHuman() {
        return this != AUTO_ASSIGN;
    }

    /**
     * Where a Team Lead or Process Owner sends something beyond their authority.
     * Both escalate to the Manager; the Manager has nowhere further to go, which is the
     * correct answer rather than a gap - an escalation loop would be worse than a stop.
     */
    public java.util.Optional<ApprovalRoute> escalationTarget() {
        return switch (this) {
            case TEAM_LEAD_REVIEW, PROCESS_OWNER_REVIEW, MANUAL_EXCEPTION_REVIEW ->
                    java.util.Optional.of(MANAGER_APPROVAL);
            default -> java.util.Optional.empty();
        };
    }
}
