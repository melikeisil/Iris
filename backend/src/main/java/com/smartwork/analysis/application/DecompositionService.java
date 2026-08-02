package com.smartwork.analysis.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwork.analysis.domain.Recommendation;
import com.smartwork.analysis.infrastructure.AiAnalysisClient;
import com.smartwork.analysis.infrastructure.AiServiceDtos.AnalysisResponse;
import com.smartwork.analysis.infrastructure.AiServiceDtos.DecomposeEnvelope;
import com.smartwork.analysis.infrastructure.AiServiceDtos.DecomposeRequest;
import com.smartwork.analysis.infrastructure.AiServiceDtos.OrgContext;
import com.smartwork.analysis.infrastructure.AiServiceDtos.ProcessTemplateSpec;
import com.smartwork.analysis.infrastructure.AiServiceDtos.SuggestedWorkItem;
import com.smartwork.analysis.infrastructure.AiServiceProperties;
import com.smartwork.analysis.infrastructure.RecommendationRepository;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.common.web.CorrelationId;
import com.smartwork.process.application.ProcessInstantiator;
import com.smartwork.process.application.ProcessTemplateService;
import com.smartwork.workitem.application.WorkItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Produces a plan for an analyzed item: template first, AI second.
 *
 * <p>The ordering is the design. A matched template is a plan the organization has already
 * agreed on, with owners and dependencies someone thought about once and does not have to
 * think about again. Asking a model to regenerate that plan from scratch on every ticket
 * spends tokens to produce a slightly different answer each time — worse on every axis
 * that matters: consistency, latency, cost and auditability.
 *
 * <p>The AI's role here is to <em>customize</em> a matched template, or to propose a plan
 * only when nothing matched. docs/08 §5.3 treats "a new non-standard plan was generated" as
 * a PROCESS_OWNER_REVIEW trigger precisely because the second case deserves a human look.
 */
@Service
public class DecompositionService {

    private static final Logger log = LoggerFactory.getLogger(DecompositionService.class);

    /** See {@code AnalysisService.JSON_OBJECT} — a raw {@code Map.class} erases unsafely. */
    private static final com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> JSON_OBJECT =
            new com.fasterxml.jackson.core.type.TypeReference<>() {
            };

    private final AiAnalysisClient client;
    private final OrgContextBuilder orgContextBuilder;
    private final ProcessTemplateService templateService;
    private final ProcessInstantiator instantiator;
    private final RecommendationRepository recommendations;
    private final WorkItemService workItemService;
    private final AiServiceProperties properties;
    private final ObjectMapper objectMapper;

    public DecompositionService(
            AiAnalysisClient client,
            OrgContextBuilder orgContextBuilder,
            ProcessTemplateService templateService,
            ProcessInstantiator instantiator,
            RecommendationRepository recommendations,
            WorkItemService workItemService,
            AiServiceProperties properties,
            ObjectMapper objectMapper) {
        this.client = client;
        this.orgContextBuilder = orgContextBuilder;
        this.templateService = templateService;
        this.instantiator = instantiator;
        this.recommendations = recommendations;
        this.workItemService = workItemService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public enum Source {
        /** A known template was instantiated directly. */
        TEMPLATE,
        /** The AI customized a matched template. */
        TEMPLATE_CUSTOMIZED_BY_AI,
        /** No template matched; the AI proposed a plan. Needs a Process Owner's eyes. */
        AI_GENERATED,
        /** Neither a template nor the AI could produce a plan. */
        NONE
    }

    public record Plan(
            Source source,
            Recommendation recommendation,
            List<SuggestedWorkItem> suggestedItems,
            ProcessTemplateService.TemplateDetailsView matchedTemplate,
            List<String> warnings,
            boolean needsProcessOwnerReview) {
    }

    /**
     * Builds a plan proposal. Does not create work items — that is
     * {@link #applyTemplatePlan}, which a human or the Phase 6 policy engine calls once the
     * plan has been accepted.
     */
    @Transactional
    public Plan propose(AuthPrincipal principal, UUID workItemId) {
        WorkItemService.AnalysisView item = workItemService.requireVisibleForAnalysis(principal, workItemId);
        OrgContext orgContext = orgContextBuilder.build(item.organizationId());

        Recommendation recommendation =
                new Recommendation(item.organizationId(), item.id(), Recommendation.Type.PLAN);
        recommendation.setSourceUpdatedAt(item.updatedAt());
        recommendation.setCorrelationId(CorrelationId.current());

        AnalysisResponse analysis = latestAnalysisOf(item.id());
        ProcessTemplateService.MatchOutcome match = templateService.reconcile(
                item.organizationId(),
                item.summary(),
                item.description(),
                analysis == null ? null : analysis.matchedProcessTemplate());

        AiAnalysisClient.Outcome<DecomposeEnvelope> outcome = analysis == null
                ? null
                : client.decompose(new DecomposeRequest(
                        CorrelationId.current(),
                        item.id().toString(),
                        analysis,
                        match.matched() ? specFor(match.template()) : null,
                        properties.getMaxDecompositionItems(),
                        orgContext));

        // AI succeeded: its plan is the proposal, labelled by whether it had a template to
        // work from. Either way the template match is retained so the applier can fall back.
        if (outcome != null && outcome.isOk() && outcome.body() != null
                && outcome.body().decomposition() != null) {

            DecomposeEnvelope envelope = outcome.body();
            recommendation.setSuggestedPlan(
                    objectMapper.convertValue(envelope.decomposition(), JSON_OBJECT));
            recommendation.setStatus(Recommendation.Status.PENDING);
            if (envelope.meta() != null) {
                recommendation.recordProvenance(
                        envelope.meta().provider(), envelope.meta().model(),
                        envelope.meta().latencyMs(), envelope.meta().promptVersion(),
                        envelope.meta().isMock());
            }
            recommendations.save(recommendation);

            Source source = match.matched() ? Source.TEMPLATE_CUSTOMIZED_BY_AI : Source.AI_GENERATED;
            return new Plan(
                    source,
                    recommendation,
                    envelope.decomposition().suggestedWorkItems(),
                    match.template(),
                    envelope.decomposition().warnings() == null
                            ? List.of() : envelope.decomposition().warnings(),
                    source == Source.AI_GENERATED || match.needsHumanReview());
        }

        // AI unavailable or rejected. A matched template is a complete, usable plan on its
        // own — this is the path that keeps the product working with no LLM at all.
        if (match.matched()) {
            log.info("AI decomposition unavailable for {}; falling back to template {}",
                    item.referenceKey(), match.template().code());
            recommendation.setSuggestedPlan(objectMapper.convertValue(
                    templatePreview(match.template()), JSON_OBJECT));
            recommendation.setStatus(Recommendation.Status.PENDING);
            recommendations.save(recommendation);

            return new Plan(Source.TEMPLATE, recommendation, List.of(), match.template(),
                    List.of("AI decomposition unavailable; using the matched template unmodified"),
                    match.needsHumanReview());
        }

        recommendation.markFailed(outcome == null
                ? "No analysis exists for this item, so there is nothing to decompose"
                : outcome.detail());
        recommendations.save(recommendation);
        return new Plan(Source.NONE, recommendation, List.of(), null,
                List.of("No template matched and no AI plan is available"), true);
    }

    /** Creates the real work item tree from a matched template. */
    @Transactional
    public ProcessInstantiator.Result applyTemplatePlan(
            AuthPrincipal principal, UUID workItemId, UUID templateId) {

        WorkItemService.ProcessRootView item = workItemService.requireEditableForProcess(principal, workItemId);
        var template = templateService.requireForInstantiation(item.organizationId(), templateId);
        return instantiator.instantiate(principal, item, template, item.dueDate());
    }

    private AnalysisResponse latestAnalysisOf(UUID workItemId) {
        return recommendations.findLatestLive(workItemId, Recommendation.Type.ANALYSIS)
                .map(Recommendation::getAnalysisPayload)
                .filter(payload -> payload != null && !payload.isEmpty())
                .map(payload -> objectMapper.convertValue(payload, AnalysisResponse.class))
                .orElse(null);
    }

    private ProcessTemplateSpec specFor(ProcessTemplateService.TemplateDetailsView template) {
        return new ProcessTemplateSpec(
                template.code(),
                template.name(),
                template.description(),
                template.tasks().stream().map(ProcessTemplateService.TemplateTaskView::title).toList());
    }

    private Map<String, Object> templatePreview(ProcessTemplateService.TemplateDetailsView template) {
        return Map.of(
                "source", "TEMPLATE",
                "templateCode", template.code(),
                "tasks", template.tasks().stream()
                        .map(task -> Map.of(
                                "sequence", task.sequence(),
                                "title", task.title(),
                                "estimatedHours", Optional.ofNullable(task.estimatedHours())
                                        .map(Object::toString).orElse("")))
                        .toList());
    }
}
