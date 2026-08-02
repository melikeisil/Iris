package com.smartwork.analysis.application;

import com.smartwork.analysis.domain.Recommendation;
import com.smartwork.analysis.infrastructure.RecommendationRepository;
import com.smartwork.common.error.ApiException;
import com.smartwork.common.web.CorrelationId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RecommendationAccessService implements RecommendationAccess {

    private final RecommendationRepository recommendations;

    public RecommendationAccessService(RecommendationRepository recommendations) {
        this.recommendations = recommendations;
    }

    @Override
    @Transactional
    public UUID openAssignmentRecommendation(
            UUID organizationId, UUID workItemId, Instant sourceUpdatedAt) {

        // Supersede rather than accumulate: two live ASSIGNMENT recommendations for one
        // item is a state the UI would have to arbitrate, and it would arbitrate it
        // differently from the policy engine sooner or later.
        Recommendation recommendation =
                new Recommendation(organizationId, workItemId, Recommendation.Type.ASSIGNMENT);
        recommendation.setSourceUpdatedAt(sourceUpdatedAt);
        recommendation.setCorrelationId(CorrelationId.current());

        recommendations.findLatestLive(workItemId, Recommendation.Type.ASSIGNMENT)
                .ifPresent(previous -> previous.supersededBy(recommendation.getId()));

        return recommendations.save(recommendation).getId();
    }

    @Override
    @Transactional
    public void recordRouting(
            UUID recommendationId, String route, String reasonCodesCsv,
            UUID policyId, Long policyVersion, int engineVersion) {

        Recommendation recommendation = require(recommendationId);
        recommendation.recordRouting(route, reasonCodesCsv);
        recommendation.recordPolicy(policyId, policyVersion, engineVersion);
    }

    @Override
    @Transactional
    public void markAwaitingApproval(UUID recommendationId) {
        require(recommendationId).setStatus(Recommendation.Status.AWAITING_APPROVAL);
    }

    @Override
    @Transactional
    public void markApproved(UUID recommendationId) {
        require(recommendationId).setStatus(Recommendation.Status.APPROVED);
    }

    @Override
    @Transactional
    public void markRejected(UUID recommendationId) {
        require(recommendationId).setStatus(Recommendation.Status.REJECTED);
    }

    @Override
    @Transactional
    public void markApplied(UUID recommendationId) {
        require(recommendationId).markApplied();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<View> find(UUID organizationId, UUID recommendationId) {
        return recommendations.findByOrganizationIdAndId(organizationId, recommendationId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<View> findLatestAnalysis(UUID organizationId, UUID workItemId) {
        return recommendations.findLatestLive(workItemId, Recommendation.Type.ANALYSIS)
                .filter(r -> r.getOrganizationId().equals(organizationId))
                .map(this::toView);
    }

    private View toView(Recommendation r) {
        return new View(
                r.getId(), r.getOrganizationId(), r.getWorkItemId(),
                r.getRecommendationType().name(), r.getStatus().name(),
                r.getConfidence(), r.getRoutingDecision(), r.getRoutingReasonCodes(),
                r.getSourceUpdatedAt(), r.getAnalysisPayload());
    }

    private Recommendation require(UUID recommendationId) {
        return recommendations.findById(recommendationId)
                .orElseThrow(() -> ApiException.notFound("Recommendation", recommendationId));
    }
}
