package com.smartwork.analysis.api;

import com.smartwork.analysis.application.DecompositionService;
import com.smartwork.analysis.domain.Recommendation;
import com.smartwork.process.application.ProcessInstantiator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API types for the analysis module.
 *
 * <p>Separate from the entities on purpose ({@code ArchitectureTest.controllersMustNot
 * ExposeJpaEntities}): exposing {@code Recommendation} directly would make every new column
 * a breaking API change, and would leak {@code sourceUpdatedAt} and {@code supersededBy},
 * which are internal bookkeeping rather than contract.
 */
public final class AnalysisDtos {

    private AnalysisDtos() {
    }

    public record ProvenanceResponse(
            String provider, String model, Integer latencyMs, boolean isMock) {
    }

    public record RecommendationResponse(
            UUID id,
            UUID workItemId,
            String type,
            String status,
            BigDecimal confidence,
            Map<String, Object> analysis,
            Map<String, Object> plan,
            ProvenanceResponse provenance,
            String errorDetail,
            Instant createdAt) {

        public static RecommendationResponse from(Recommendation recommendation) {
            return new RecommendationResponse(
                    recommendation.getId(),
                    recommendation.getWorkItemId(),
                    recommendation.getRecommendationType().name(),
                    recommendation.getStatus().name(),
                    recommendation.getConfidence(),
                    recommendation.getAnalysisPayload(),
                    recommendation.getSuggestedPlan(),
                    new ProvenanceResponse(
                            recommendation.getLlmProvider(),
                            recommendation.getLlmModel(),
                            recommendation.getLlmLatencyMs(),
                            recommendation.isMock()),
                    recommendation.getErrorDetail(),
                    recommendation.getCreatedAt());
        }
    }

    public record SuggestedItemResponse(
            String tempId,
            String title,
            String description,
            String workItemType,
            String departmentCode,
            String teamCode,
            List<String> requiredSkills,
            BigDecimal estimatedHours,
            String priority,
            String riskLevel,
            List<String> dependencies,
            String explanation) {
    }

    public record PlanResponse(
            String source,
            UUID recommendationId,
            String matchedTemplateCode,
            List<SuggestedItemResponse> suggestedItems,
            List<String> warnings,
            boolean needsProcessOwnerReview) {

        public static PlanResponse from(DecompositionService.Plan plan) {
            return new PlanResponse(
                    plan.source().name(),
                    plan.recommendation() == null ? null : plan.recommendation().getId(),
                    plan.matchedTemplate() == null ? null : plan.matchedTemplate().code(),
                    plan.suggestedItems() == null ? List.of() : plan.suggestedItems().stream()
                            .map(item -> new SuggestedItemResponse(
                                    item.tempId(), item.title(), item.description(),
                                    item.workItemType() == null ? null : item.workItemType().name(),
                                    item.departmentCode(), item.teamCode(),
                                    item.requiredSkills(), item.estimatedHours(),
                                    item.priority() == null ? null : item.priority().name(),
                                    item.riskLevel() == null ? null : item.riskLevel().name(),
                                    item.dependencies(), item.explanation()))
                            .toList(),
                    plan.warnings(),
                    plan.needsProcessOwnerReview());
        }
    }

    public record AppliedPlanResponse(
            UUID processDefinitionId,
            List<CreatedItemResponse> created,
            int dependencyLinksCreated,
            UUID processOwnerId) {

        public static AppliedPlanResponse from(ProcessInstantiator.Result result) {
            return new AppliedPlanResponse(
                    result.processDefinitionId(),
                    result.createdItems().stream()
                            .map(item -> new CreatedItemResponse(item.id(), item.referenceKey(), item.summary()))
                            .toList(),
                    result.dependencyLinksCreated(),
                    result.processOwnerId());
        }
    }

    public record CreatedItemResponse(UUID id, String referenceKey, String summary) {
    }
}
