package com.smartwork.policy.api;

import com.smartwork.policy.domain.ApprovalRequest;
import com.smartwork.policy.domain.ReasonCode;
import com.smartwork.policy.domain.RoutingDecision;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** API types for the policy module. Never the entities — see {@code ArchitectureTest}. */
public final class PolicyDtos {

    private PolicyDtos() {
    }

    public record ReasonResponse(String code, String meaning) {

        static List<ReasonResponse> from(String csv) {
            if (csv == null || csv.isBlank()) {
                return List.of();
            }
            return Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(code -> {
                        try {
                            ReasonCode parsed = ReasonCode.valueOf(code);
                            return new ReasonResponse(parsed.name(), parsed.phrase());
                        } catch (IllegalArgumentException e) {
                            // A code stored by an older engine version that no longer
                            // exists. Shown rather than dropped: a historical decision
                            // should stay readable even after the enum moves on.
                            return new ReasonResponse(code, "(reason code no longer defined)");
                        }
                    })
                    .toList();
        }
    }

    public record ApprovalRequestResponse(
            UUID id,
            UUID workItemId,
            UUID recommendationId,
            String route,
            String status,
            List<ReasonResponse> reasons,
            String assignedToScope,
            UUID assignedToUserId,
            boolean policyException,
            Instant dueAt,
            boolean overdue,
            UUID escalatedFromId,
            String escalationReason,
            UUID decidedBy,
            String decisionNote,
            Instant decidedAt,
            Instant createdAt) {

        public static ApprovalRequestResponse from(ApprovalRequest request, Instant now) {
            return new ApprovalRequestResponse(
                    request.getId(),
                    request.getWorkItemId(),
                    request.getRecommendationId(),
                    request.getRoute().name(),
                    request.getStatus().name(),
                    ReasonResponse.from(request.getReasonCodes()),
                    request.getAssignedToScope(),
                    request.getAssignedToUserId(),
                    request.isPolicyException(),
                    request.getDueAt(),
                    request.isOverdue(now),
                    request.getEscalatedFromId(),
                    request.getEscalationReason(),
                    request.getDecidedBy(),
                    request.getDecisionNote(),
                    request.getDecidedAt(),
                    request.getCreatedAt());
        }
    }

    public record RoutingDecisionResponse(
            String route,
            List<ReasonResponse> reasons,
            String explanation,
            Long suggestedSlaMinutes,
            UUID policyId,
            Long policyVersion,
            int engineVersion) {

        public static RoutingDecisionResponse from(RoutingDecision decision) {
            return new RoutingDecisionResponse(
                    decision.route().name(),
                    decision.reasonCodes().stream()
                            .map(code -> new ReasonResponse(code.name(), code.phrase()))
                            .toList(),
                    decision.explanation(),
                    decision.suggestedSla() == null ? null : decision.suggestedSla().toMinutes(),
                    decision.policyId(),
                    decision.policyVersion(),
                    decision.engineVersion());
        }
    }

    public record ApproveRequest(UUID assigneeIdOverride, String note, Long version) {
    }

    public record RejectRequest(String reason) {
    }

    public record EscalateRequest(String reason) {
    }
}
