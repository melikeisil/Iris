package com.smartwork.policy.domain;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * The engine's output (docs/08 §5.6).
 *
 * <p>Carries the policy identity it was produced under so the decision can be replayed:
 * the same inputs, the same policy version and the same engine version must reproduce this
 * record exactly. {@code RoutingIsReplayableTest} is the guard on that.
 */
public record RoutingDecision(
        ApprovalRoute route,
        List<ReasonCode> reasonCodes,
        String explanation,
        Duration suggestedSla,
        UUID policyId,
        Long policyVersion,
        int engineVersion) {

    public RoutingDecision {
        reasonCodes = List.copyOf(reasonCodes);
    }

    public boolean isAutoAssign() {
        return route == ApprovalRoute.AUTO_ASSIGN;
    }

    /** CSV for the {@code routing_reason_codes} column. */
    public String reasonCodesCsv() {
        return reasonCodes.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse("");
    }

    public boolean hasReason(ReasonCode code) {
        return reasonCodes.contains(code);
    }
}
