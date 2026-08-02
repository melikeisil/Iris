package com.smartwork.analysis.api;

import com.smartwork.analysis.application.AnalysisService;
import com.smartwork.analysis.application.DecompositionService;
import com.smartwork.analysis.domain.Recommendation;
import com.smartwork.analysis.infrastructure.AiAnalysisClient;
import com.smartwork.common.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Analysis", description = "AI analysis, plan proposals and recommendation history")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final DecompositionService decompositionService;
    private final AiAnalysisClient client;

    public AnalysisController(
            AnalysisService analysisService,
            DecompositionService decompositionService,
            AiAnalysisClient client) {
        this.analysisService = analysisService;
        this.decompositionService = decompositionService;
        this.client = client;
    }

    @PostMapping("/work-items/{id}/analyze")
    @Operation(summary = "Analyze a work item",
            description = "Always returns a recommendation. If the AI service is unreachable the "
                    + "recommendation is stored with status FAILED and the reason recorded, rather "
                    + "than the request failing — analysis is an enhancement, not a precondition.")
    public AnalysisDtos.RecommendationResponse analyze(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return AnalysisDtos.RecommendationResponse.from(analysisService.analyze(principal, id));
    }

    @GetMapping("/work-items/{id}/recommendations")
    @Operation(summary = "Recommendation history for a work item, newest first")
    public List<AnalysisDtos.RecommendationResponse> history(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return analysisService.history(principal, id).stream()
                .map(AnalysisDtos.RecommendationResponse::from)
                .toList();
    }

    @PostMapping("/work-items/{id}/plan")
    @Operation(summary = "Propose a decomposition plan",
            description = "Template first, AI second. Creates no work items — call apply-plan "
                    + "once the proposal has been accepted.")
    public AnalysisDtos.PlanResponse propose(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return AnalysisDtos.PlanResponse.from(decompositionService.propose(principal, id));
    }

    @PostMapping("/work-items/{id}/apply-plan")
    @Operation(summary = "Instantiate a process template under this work item")
    public AnalysisDtos.AppliedPlanResponse applyPlan(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @RequestParam UUID templateId) {
        return AnalysisDtos.AppliedPlanResponse.from(
                decompositionService.applyTemplatePlan(principal, id, templateId));
    }

    // POST /work-items/{id}/additional-information moved to
    // com.smartwork.policy.api.ClarificationController: answering clarification questions
    // now also reruns analysis and routing in the same request, and that composition needs
    // policy's routing pipeline, which this module is not permitted to depend on (see
    // analysis's module allowedDependencies). AnalysisService.submitAdditionalInformation
    // and .answerClarificationAndReanalyze remain here; only the HTTP endpoint moved.

    @GetMapping("/recommendations/{id}")
    @Operation(summary = "Fetch a recommendation",
            description = "Returns 422 RECOMMENDATION_STALE if the work item has been edited since "
                    + "the recommendation was generated.")
    public AnalysisDtos.RecommendationResponse get(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        Recommendation recommendation = analysisService.requireFresh(principal, id);
        return AnalysisDtos.RecommendationResponse.from(recommendation);
    }

    @GetMapping("/ai/provider")
    @Operation(summary = "Which LLM provider is active",
            description = "Drives the UI's 'demo analysis' badge when the mock provider is in use.")
    public ResponseEntity<AiAnalysisClient.ProviderInfo> provider() {
        AiAnalysisClient.Outcome<AiAnalysisClient.ProviderInfo> outcome = client.providerInfo();
        return outcome.isOk()
                ? ResponseEntity.ok(outcome.body())
                : ResponseEntity.status(503).build();
    }
}
