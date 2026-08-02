package com.smartwork.policy.domain;

import java.util.List;

/**
 * Template-generated explanations, built from the reason codes rather than written
 * alongside them.
 *
 * <p>Same reasoning as the candidate explanations in Phase 5: an explanation composed from
 * the codes cannot contradict the codes. An explanation written separately — by a person or
 * by a model — can, and a routing decision that explains itself incorrectly is worse than
 * one that does not explain itself at all, because the reader has no way to tell.
 */
public final class Explanation {

    private Explanation() {
    }

    public static String render(ApprovalRoute route, List<ReasonCode> reasons) {
        if (reasons.isEmpty()) {
            return lead(route) + " No specific triggers were recorded.";
        }
        if (route == ApprovalRoute.AUTO_ASSIGN) {
            return "Assigned automatically: every auto-assign condition is satisfied — "
                    + "low risk, no sensitivity flags, sufficient confidence, an unambiguous "
                    + "top candidate with capacity, and a single team involved.";
        }

        StringBuilder sb = new StringBuilder(lead(route)).append(' ');
        if (reasons.size() == 1) {
            sb.append(capitalize(reasons.get(0).phrase())).append('.');
        } else {
            sb.append("Specifically: ");
            for (int i = 0; i < reasons.size(); i++) {
                if (i > 0) {
                    sb.append(i == reasons.size() - 1 ? "; and " : "; ");
                }
                sb.append(reasons.get(i).phrase());
            }
            sb.append('.');
        }
        return sb.toString();
    }

    private static String lead(ApprovalRoute route) {
        return switch (route) {
            case MANUAL_EXCEPTION_REVIEW ->
                    "Held for manual review before any automated handling.";
            case REQUEST_MORE_INFORMATION ->
                    "Returned to the requester for more information.";
            case MANAGER_APPROVAL ->
                    "Sent for manager approval.";
            case PROCESS_OWNER_REVIEW ->
                    "Sent to the process owner for review.";
            case TEAM_LEAD_REVIEW ->
                    "Sent to the team lead for review.";
            case AUTO_ASSIGN ->
                    "Assigned automatically.";
        };
    }

    private static String capitalize(String phrase) {
        return phrase.isEmpty() ? phrase : Character.toUpperCase(phrase.charAt(0)) + phrase.substring(1);
    }
}
