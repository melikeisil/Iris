package com.smartwork.analysis.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwork.analysis.domain.Recommendation;
import com.smartwork.analysis.infrastructure.AiAnalysisClient;
import com.smartwork.analysis.infrastructure.AiServiceDtos.AnalysisEnvelope;
import com.smartwork.analysis.infrastructure.AiServiceDtos.AnalysisResponse;
import com.smartwork.analysis.infrastructure.AiServiceDtos.AnalyzeDevelopmentRequest;
import com.smartwork.analysis.infrastructure.AiServiceDtos.AnalyzeTicketRequest;
import com.smartwork.analysis.infrastructure.AiServiceDtos.ClarificationAnswer;
import com.smartwork.analysis.infrastructure.AiServiceDtos.ClarificationQuestion;
import com.smartwork.analysis.infrastructure.AiServiceDtos.OrgContext;
import com.smartwork.analysis.infrastructure.AiServiceDtos.ProviderMeta;
import com.smartwork.analysis.infrastructure.RecommendationRepository;
import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.common.web.CorrelationId;
import com.smartwork.organization.application.DepartmentDirectory;
import com.smartwork.project.application.ProjectDirectory;
import com.smartwork.workitem.application.WorkItemService;
import com.smartwork.workitem.application.WorkItemTransitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates analysis: build context, call the AI service, persist the proposal.
 *
 * <p>What this class deliberately does <em>not</em> do is apply the result. An analysis
 * arrives as a {@code PENDING} recommendation and stays that way until something decides
 * to act on it — the policy engine in Phase 6, or a human. docs/07 §1 is explicit that the
 * AI's output is a proposal the backend may accept, ignore, override or discard, and the
 * cleanest way to keep that true is for the code path that fetches an analysis to have no
 * ability to mutate the work item at all.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    /**
     * The JSONB payload shape. A {@code TypeReference} rather than {@code Map.class}:
     * the raw form compiles with an unchecked warning and silently erases to
     * {@code Map<Object, Object>}, which only fails later, at the JPA boundary.
     */
    private static final com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> JSON_OBJECT =
            new com.fasterxml.jackson.core.type.TypeReference<>() {
            };

    private final AiAnalysisClient client;
    private final OrgContextBuilder orgContextBuilder;
    private final RecommendationRepository recommendations;
    private final WorkItemService workItemService;
    private final WorkItemTransitionService transitions;
    private final DepartmentDirectory departments;
    private final ProjectDirectory projects;
    private final ObjectMapper objectMapper;

    public AnalysisService(
            AiAnalysisClient client,
            OrgContextBuilder orgContextBuilder,
            RecommendationRepository recommendations,
            WorkItemService workItemService,
            WorkItemTransitionService transitions,
            DepartmentDirectory departments,
            ProjectDirectory projects,
            ObjectMapper objectMapper) {
        this.client = client;
        this.orgContextBuilder = orgContextBuilder;
        this.recommendations = recommendations;
        this.workItemService = workItemService;
        this.transitions = transitions;
        this.departments = departments;
        this.projects = projects;
        this.objectMapper = objectMapper;
    }

    /**
     * Analyzes a work item and stores the result as a recommendation.
     *
     * <p>Always returns a persisted {@link Recommendation}, including when the AI service
     * was unreachable — in that case status is {@code FAILED} with the reason recorded.
     * A failed analysis is a fact worth storing: it is what tells a retry job there is
     * something to retry, and what tells a reviewer why an item has no proposal.
     *
     * <p>Also drives the two automatic edges either side of the AI call. {@code ANALYZE}
     * (initial state → {@code AI_ANALYSIS}) fires first, unconditionally: without it an
     * item sat in {@code NEW}/{@code BACKLOG} forever, because nothing else was ever wired
     * to ask for that move — this call is what a person means by "analyze this", so it is
     * also what should get it out of the inbox. {@code NEEDS_INFO} ({@code AI_ANALYSIS} →
     * {@code WAITING_INFORMATION}) fires afterward, only when the AI said it could not
     * proceed without more information. Both are no-ops (never errors) when the workflow
     * profile does not define the edge from the item's current state — most profiles do
     * not use {@code AI_ANALYSIS} at all, and this method has no way to know that in
     * advance without duplicating the workflow engine's own logic.
     */
    @Transactional
    public Recommendation analyze(AuthPrincipal principal, UUID workItemId) {
        WorkItemService.AnalysisView item = workItemService.requireVisibleForAnalysisLocked(principal, workItemId);
        Optional<Recommendation> previousAnalysis =
                recommendations.findLatestLive(item.id(), Recommendation.Type.ANALYSIS);
        if (previousAnalysis.filter(previous -> isReusableSuccessfulAnalysis(previous, item)).isPresent()) {
            return previousAnalysis.orElseThrow();
        }
        transitions.applyAutomatic(workItemId, "ANALYZE");

        // Re-read after the transition, still under the same row lock, rather than reusing
        // the pre-transition `item` snapshot's timestamp. ANALYZE (on a brand-new item) bumps
        // the work item's own updated_at, so a request that raced this one and blocked on the
        // lock will see that post-transition timestamp once it wakes up. Stamping the
        // recommendation with the stale pre-transition value would make every such
        // concurrent/duplicate request miss the reuse check above and re-call the provider -
        // precisely the "double-click Analyze" case this guard exists for.
        WorkItemService.AnalysisView settled = workItemService.requireVisibleForAnalysisLocked(principal, workItemId);

        OrgContext orgContext = orgContextBuilder.build(item.organizationId());

        Recommendation recommendation =
                new Recommendation(item.organizationId(), item.id(), Recommendation.Type.ANALYSIS);
        recommendation.setSourceUpdatedAt(settled.updatedAt());
        recommendation.setCorrelationId(CorrelationId.current());

        // Any previous live analysis is superseded, not left alongside. Two "current"
        // analyses for one item is a state the UI would have to arbitrate, and it would
        // arbitrate it differently from the policy engine sooner or later.
        //
        // Read before superseding: if that previous analysis asked clarification questions
        // and POST .../additional-information answered them, its answers are what this
        // reanalysis is for. Losing them here would make answering the questions pointless.
        List<ClarificationAnswer> clarificationAnswers = previousAnalysis
                .map(Recommendation::getClarificationAnswers)
                .filter(answers -> answers != null && !answers.isEmpty())
                .map(this::toClarificationAnswers)
                .orElse(List.of());
        previousAnalysis.ifPresent(previous -> previous.supersededBy(recommendation.getId()));

        AiAnalysisClient.Outcome<AnalysisEnvelope> outcome = item.workItemType().isDevelopment()
                ? client.analyzeDevelopment(developmentRequest(item, orgContext, clarificationAnswers))
                : client.analyzeTicket(ticketRequest(item, orgContext, clarificationAnswers));

        AnalysisResponse analysisResponse = null;
        switch (outcome.status()) {
            case OK -> {
                applyEnvelope(recommendation, outcome.body());
                if (outcome.body() != null) {
                    analysisResponse = outcome.body().analysis();
                }
            }
            case REJECTED -> recommendation.markFailed(
                    "AI output failed validation twice: "
                            + (outcome.validationError() == null
                                    ? "no detail" : String.join("; ", outcome.validationError().errors())));
            case UNAVAILABLE -> recommendation.markFailed(outcome.detail());
            case DISABLED -> recommendation.markFailed("AI analysis is disabled by configuration");
            default -> recommendation.markFailed("Unknown AI outcome");
        }

        Recommendation saved = recommendations.save(recommendation);
        applyClarificationOutcome(workItemId, analysisResponse);
        saved.setSourceUpdatedAt(workItemService.requireVisibleForAnalysisLocked(principal, workItemId).updatedAt());
        return saved;
    }

    private boolean isReusableSuccessfulAnalysis(Recommendation recommendation, WorkItemService.AnalysisView item) {
        return recommendation.getStatus() != Recommendation.Status.FAILED
                && recommendation.getAnalysisPayload() != null
                && recommendation.getSourceUpdatedAt() != null
                && !recommendation.getSourceUpdatedAt().isBefore(item.updatedAt());
    }

    /**
     * The state and mirrored-data consequences of what the AI just said.
     *
     * <p>{@code null} (analysis failed, was rejected, or the AI is unavailable/disabled)
     * leaves the item wherever it is - a transient AI outage should not itself move
     * something into {@code WAITING_INFORMATION} or silently clear a question that is
     * still legitimately pending from an earlier, successful analysis.
     */
    private void applyClarificationOutcome(UUID workItemId, AnalysisResponse analysisResponse) {
        if (analysisResponse == null) {
            return;
        }
        if (analysisResponse.requiresMoreInformation()) {
            boolean moved = transitions.applyAutomatic(workItemId, "NEEDS_INFO").isPresent();
            if (moved) {
                workItemService.recordPendingClarification(workItemId,
                        toQuestionMaps(analysisResponse.clarificationQuestions()));
            }
        } else {
            workItemService.clearPendingClarification(workItemId);
        }
    }

    private List<Map<String, Object>> toQuestionMaps(List<ClarificationQuestion> questions) {
        if (questions == null) {
            return List.of();
        }
        return questions.stream()
                .map(question -> Map.<String, Object>of(
                        "id", question.id() == null ? "" : question.id(),
                        "question", question.question() == null ? "" : question.question(),
                        "field", question.field() == null ? "" : question.field(),
                        "required", question.required()))
                .toList();
    }

    /**
     * Cross-module entry point for {@code policy}'s reanalyze-and-reroute flow, run once a
     * requester answers clarification questions.
     *
     * <p>Returns only the resulting work item status, not a {@link Recommendation}:
     * {@code policy} is not permitted to depend on this module's domain package (see its
     * module's {@code allowedDependencies}), and has no need to — everything it needs
     * about the fresh analysis it already reads through {@code RecommendationAccess},
     * which it depends on for routing regardless.
     */
    @Transactional
    public String answerClarificationAndReanalyze(
            AuthPrincipal principal, UUID workItemId, List<ClarificationAnswerInput> answers) {
        submitAdditionalInformation(principal, workItemId, answers);
        analyze(principal, workItemId);
        return workItemService.requireVisibleStatus(principal, workItemId);
    }

    private void applyEnvelope(Recommendation recommendation, AnalysisEnvelope envelope) {
        if (envelope == null || envelope.analysis() == null) {
            recommendation.markFailed("AI service returned an empty envelope");
            return;
        }
        AnalysisResponse analysis = envelope.analysis();
        recommendation.setAnalysisPayload(objectMapper.convertValue(analysis, JSON_OBJECT));
        recommendation.setConfidence(analysis.confidence());
        recommendation.setStatus(Recommendation.Status.PENDING);

        ProviderMeta meta = envelope.meta();
        if (meta != null) {
            recommendation.recordProvenance(
                    meta.provider(), meta.model(), meta.latencyMs(), meta.promptVersion(), meta.isMock());
        }

        if (analysis.injectionSuspected()) {
            // docs/07 §5: a suspected injection routes to MANUAL_EXCEPTION_REVIEW. The
            // route itself is Phase 6's call; recording the signal is this phase's job.
            log.warn("Suspected prompt injection in analysis of work item {}", recommendation.getWorkItemId());
        }
    }

    private AnalyzeTicketRequest ticketRequest(
            WorkItemService.AnalysisView item, OrgContext orgContext,
            List<ClarificationAnswer> clarificationAnswers) {
        return new AnalyzeTicketRequest(
                CorrelationId.current(),
                item.id().toString(),
                item.summary(),
                item.description(),
                departmentCodeOf(item),
                item.originType(),
                item.priority(),
                clarificationAnswers,
                orgContext,
                List.of());
    }

    private AnalyzeDevelopmentRequest developmentRequest(
            WorkItemService.AnalysisView item, OrgContext orgContext,
            List<ClarificationAnswer> clarificationAnswers) {
        return new AnalyzeDevelopmentRequest(
                CorrelationId.current(),
                item.id().toString(),
                item.workItemType(),
                item.summary(),
                item.description(),
                item.acceptanceCriteria(),
                projectCodeOf(item),
                null,
                List.of(),
                orgContext,
                clarificationAnswers);
    }

    private List<ClarificationAnswer> toClarificationAnswers(List<Map<String, Object>> stored) {
        return stored.stream()
                .map(entry -> new ClarificationAnswer(
                        stringValue(entry.get("question_id")),
                        stringValue(entry.get("field")),
                        stringValue(entry.get("answer"))))
                .toList();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String departmentCodeOf(WorkItemService.AnalysisView item) {
        if (item.departmentId() == null) {
            return null;
        }
        return departments.find(item.organizationId(), item.departmentId())
                .map(DepartmentDirectory.DepartmentView::code)
                .orElse(null);
    }

    private String projectCodeOf(WorkItemService.AnalysisView item) {
        if (item.projectId() == null) {
            return null;
        }
        return projects.find(item.organizationId(), item.projectId())
                .map(ProjectDirectory.ProjectView::code)
                .orElse(null);
    }

    /**
     * Submits answers to the clarification questions on this item's latest analysis.
     *
     * <p>Authorization and the state change both come from one place: the {@code
     * INFO_PROVIDED} workflow transition (WAITING_INFORMATION → AI_ANALYSIS), configured
     * per profile in {@code V6_1__seed_workflow_definitions.sql} to permit the REQUESTER
     * or ASSIGNEE relationship, or a TEAM_LEAD/MANAGER role. Re-implementing that check
     * here would let this endpoint and the workflow engine disagree about who may act —
     * exactly what docs/01 §"frontend cannot invent a state" exists to prevent on the
     * other side of this same rule.
     *
     * <p>Landing in AI_ANALYSIS is what "prepares it for reanalysis" means concretely:
     * the item is back where {@code POST .../analyze} expects to find it, and that call
     * will pick up the answers recorded here (see {@link #analyze}).
     */
    @Transactional
    public AdditionalInformationResult submitAdditionalInformation(
            AuthPrincipal principal, UUID workItemId, List<ClarificationAnswerInput> answers) {

        workItemService.requireVisible(principal, workItemId);

        Recommendation analysis = recommendations.findLatestLive(workItemId, Recommendation.Type.ANALYSIS)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "There is no analysis on this item to answer questions for. Run analysis first."));

        if (!requiresMoreInformation(analysis)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "The latest analysis for this item did not request additional information.");
        }

        analysis.recordClarificationAnswers(answers.stream()
                .map(answer -> Map.<String, Object>of(
                        "question_id", answer.questionId() == null ? "" : answer.questionId(),
                        "field", answer.field() == null ? "" : answer.field(),
                        "answer", answer.answer()))
                .toList());

        WorkItemTransitionService.TransitionSummary result = transitions.transitionSummary(principal, workItemId,
                new WorkItemTransitionService.Command("INFO_PROVIDED", null, null, null, null));

        log.info("{} answers recorded on the analysis for {}; moved {} -> {}",
                answers.size(), result.referenceKey(), "WAITING_INFORMATION", result.status());

        return new AdditionalInformationResult(result.workItemId(), result.referenceKey(), result.status(), analysis);
    }

    private boolean requiresMoreInformation(Recommendation analysis) {
        Map<String, Object> payload = analysis.getAnalysisPayload();
        return payload != null && Boolean.TRUE.equals(payload.get("requires_more_information"));
    }

    /** One answer to one clarification question. Field-level validation lives at the API boundary. */
    public record ClarificationAnswerInput(String questionId, String field, String answer) {
    }

    public record AdditionalInformationResult(
            UUID workItemId, String referenceKey, String status, Recommendation analysis) {
    }

    @Transactional(readOnly = true)
    public List<Recommendation> history(AuthPrincipal principal, UUID workItemId) {
        workItemService.requireVisible(principal, workItemId);
        return recommendations.findByWorkItemIdOrderByCreatedAtDesc(workItemId);
    }

    /**
     * Fetches a recommendation and marks it STALE if the work item has changed since it was
     * generated. Callers must treat a STALE recommendation as unusable — see
     * {@link Recommendation#isStaleAgainst}.
     */
    @Transactional
    public Recommendation requireFresh(AuthPrincipal principal, UUID recommendationId) {
        Recommendation recommendation = recommendations
                .findByOrganizationIdAndId(principal.organizationId(), recommendationId)
                .orElseThrow(() -> com.smartwork.common.error.ApiException
                        .notFound("Recommendation", recommendationId));

        WorkItemService.AnalysisView item =
                workItemService.requireVisibleForAnalysis(principal, recommendation.getWorkItemId());
        if (recommendation.isStaleAgainst(item.updatedAt())) {
            recommendation.markStale();
            throw new com.smartwork.common.error.ApiException(
                    com.smartwork.common.error.ErrorCode.RECOMMENDATION_STALE,
                    "This recommendation was generated before the work item was last edited. "
                            + "Re-run the analysis so the proposal reflects the current content.");
        }
        return recommendation;
    }
}
