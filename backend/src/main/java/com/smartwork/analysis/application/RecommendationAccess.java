package com.smartwork.analysis.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The narrow write contract {@code policy} needs against a recommendation.
 *
 * <p>An interface rather than direct access to {@code RecommendationRepository} for the
 * usual modular-monolith reason, and one specific one: the policy engine should be able to
 * record a routing decision and nothing else. Handing it the repository would also hand it
 * the ability to rewrite the analysis payload it is supposed to be acting on.
 *
 * <p>{@link #recordRouting} deliberately takes primitives rather than a
 * {@code RoutingDecision}. {@code policy} already depends on this interface, so accepting a
 * policy type here would make the two modules mutually dependent — a cycle Spring Modulith
 * rejects, and rightly: the module that <em>stores</em> a decision has no business knowing
 * how decisions are made. Storing a route name and a CSV of reason codes is exactly the
 * amount of coupling the job needs.
 */
public interface RecommendationAccess {

    /** Creates the ASSIGNMENT recommendation a routing decision will be recorded against. */
    UUID openAssignmentRecommendation(UUID organizationId, UUID workItemId, Instant sourceUpdatedAt);

    /**
     * @param policyId      the {@code assignment_policies} row in force, or null for defaults
     * @param policyVersion that row's optimistic-lock version at decision time
     * @param engineVersion the routing engine's own version, so a replay under changed
     *                      code is detectable rather than silently different
     */
    void recordRouting(
            UUID recommendationId, String route, String reasonCodesCsv,
            UUID policyId, Long policyVersion, int engineVersion);

    void markAwaitingApproval(UUID recommendationId);

    void markApproved(UUID recommendationId);

    void markRejected(UUID recommendationId);

    void markApplied(UUID recommendationId);

    Optional<View> find(UUID organizationId, UUID recommendationId);

    /** The newest live ANALYSIS for an item — what routing reads its signals from. */
    Optional<View> findLatestAnalysis(UUID organizationId, UUID workItemId);

    /** Read projection; deliberately not the entity (ADR-028). */
    record View(
            UUID id,
            UUID organizationId,
            UUID workItemId,
            String type,
            String status,
            java.math.BigDecimal confidence,
            String routingDecision,
            String routingReasonCodes,
            Instant sourceUpdatedAt,
            java.util.Map<String, Object> analysisPayload) {

        /** Reads a boolean signal from the AI payload, defaulting to false when absent. */
        public boolean flag(String key) {
            return analysisPayload != null && Boolean.TRUE.equals(analysisPayload.get(key));
        }

        @SuppressWarnings("unchecked")
        public java.util.List<String> stringList(String key) {
            if (analysisPayload == null) {
                return java.util.List.of();
            }
            Object value = analysisPayload.get(key);
            return value instanceof java.util.List<?> list
                    ? list.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                    : java.util.List.of();
        }

        public String text(String key) {
            Object value = analysisPayload == null ? null : analysisPayload.get(key);
            return value instanceof String s ? s : null;
        }
    }
}
