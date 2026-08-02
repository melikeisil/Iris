package com.smartwork.policy.api;

import com.smartwork.policy.application.WorkItemRoutingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API types for answering AI clarification questions.
 *
 * <p>Defined here, not reused from {@code analysis.api.AnalysisDtos}: this module may only
 * depend on {@code analysis::application} (see this module's {@code allowedDependencies}),
 * and {@code analysis.api} is not part of that surface. {@link RecommendationSummary} is
 * therefore a fresh, narrower projection built from {@code RecommendationAccess.View} -
 * itself already part of {@code analysis::application} - rather than the full {@code
 * Recommendation} entity, which this module is not permitted to touch at all.
 */
public final class ClarificationDtos {

    private ClarificationDtos() {
    }

    /**
     * One answer to one clarification question raised by the latest analysis.
     *
     * <p>{@code questionId} and {@code field} are echoed back from the analysis payload's
     * {@code clarification_questions} so the AI service can match answers to questions on
     * reanalysis; the API does not validate them against that list; an unmatched or blank
     * {@code questionId} is still recorded; only {@code answer} is required.
     */
    public record AnswerInput(
            String questionId,
            String field,
            @NotBlank @Size(max = 4000) String answer) {
    }

    public record SubmitAdditionalInformationRequest(
            @NotEmpty @Valid List<AnswerInput> answers) {
    }

    public record RecommendationSummary(
            UUID id,
            String status,
            BigDecimal confidence,
            boolean requiresMoreInformation,
            Map<String, Object> analysis) {
    }

    public record AdditionalInformationResponse(
            UUID workItemId,
            String referenceKey,
            String status,
            RecommendationSummary recommendation,
            boolean routed) {

        public static AdditionalInformationResponse from(WorkItemRoutingService.AdditionalInformationOutcome outcome) {
            RecommendationSummary summary = outcome.analysis() == null ? null
                    : new RecommendationSummary(
                            outcome.analysis().id(),
                            outcome.analysis().status(),
                            outcome.analysis().confidence(),
                            outcome.analysis().flag("requires_more_information"),
                            outcome.analysis().analysisPayload());

            return new AdditionalInformationResponse(
                    outcome.workItemId(),
                    outcome.referenceKey(),
                    outcome.status(),
                    summary,
                    outcome.routing() != null);
        }
    }
}
