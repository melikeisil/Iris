package com.smartwork.analysis.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwork.analysis.domain.Recommendation;
import com.smartwork.analysis.infrastructure.AiAnalysisClient;
import com.smartwork.analysis.infrastructure.AiServiceDtos.AnalysisEnvelope;
import com.smartwork.analysis.infrastructure.AiServiceDtos.AnalysisResponse;
import com.smartwork.analysis.infrastructure.AiServiceDtos.AnalyzeTicketRequest;
import com.smartwork.analysis.infrastructure.AiServiceDtos.ClarificationAnswer;
import com.smartwork.analysis.infrastructure.AiServiceDtos.ClarificationQuestion;
import com.smartwork.analysis.infrastructure.AiServiceDtos.OrgContext;
import com.smartwork.analysis.infrastructure.RecommendationRepository;
import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.organization.application.DepartmentDirectory;
import com.smartwork.project.application.ProjectDirectory;
import com.smartwork.workflow.application.WorkflowCatalog;
import com.smartwork.workflow.application.WorkflowEngine;
import com.smartwork.workflow.domain.StateCategory;
import com.smartwork.workitem.application.WorkItemService;
import com.smartwork.workitem.application.WorkItemTransitionService;
import com.smartwork.workitem.domain.OriginType;
import com.smartwork.workitem.domain.Priority;
import com.smartwork.workitem.domain.WorkFamily;
import com.smartwork.workitem.domain.WorkItem;
import com.smartwork.workitem.domain.WorkItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the clarification-answer flow added to {@link AnalysisService}:
 * {@link AnalysisService#submitAdditionalInformation} and the wiring that carries stored
 * answers into the next {@link AnalysisService#analyze}.
 *
 * <p>Collaborators are mocked; {@link WorkItem} and {@link Recommendation} are real
 * instances, not mocks — both assign their own ID in the constructor (see
 * {@code BaseEntity}), so building one needs no persistence context.
 */
class AnalysisServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID WORK_ITEM_ID = UUID.randomUUID();
    private static final UUID REPORTER_ID = UUID.randomUUID();
    private static final UUID WORKFLOW_DEFINITION_ID = UUID.randomUUID();

    private AiAnalysisClient client;
    private OrgContextBuilder orgContextBuilder;
    private RecommendationRepository recommendations;
    private WorkItemService workItemService;
    private WorkItemTransitionService transitions;
    private DepartmentDirectory departments;
    private ProjectDirectory projects;
    private AnalysisService service;

    private AuthPrincipal principal;
    private WorkItem workItem;

    @BeforeEach
    void setUp() {
        client = mock(AiAnalysisClient.class);
        orgContextBuilder = mock(OrgContextBuilder.class);
        recommendations = mock(RecommendationRepository.class);
        workItemService = mock(WorkItemService.class);
        transitions = mock(WorkItemTransitionService.class);
        departments = mock(DepartmentDirectory.class);
        projects = mock(ProjectDirectory.class);

        service = new AnalysisService(client, orgContextBuilder, recommendations,
                workItemService, transitions, departments, projects, new ObjectMapper());

        principal = new AuthPrincipal(REPORTER_ID, ORG_ID, null, Set.of(), Set.of("EMPLOYEE"), "MID", false);

        workItem = new WorkItem(ORG_ID, "TCK-42", WorkItemType.REQUEST, WorkFamily.SERVICE_WORK,
                OriginType.USER_CREATED, WORKFLOW_DEFINITION_ID, "WAITING_INFORMATION",
                "Give an intern production database access", REPORTER_ID);
        workItem.assignId(WORK_ITEM_ID);

        when(orgContextBuilder.build(ORG_ID)).thenReturn(
                new OrgContext(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
    }

    private Recommendation liveAnalysisRequestingInformation() {
        Recommendation analysis = new Recommendation(ORG_ID, WORK_ITEM_ID, Recommendation.Type.ANALYSIS);
        analysis.setStatus(Recommendation.Status.PENDING);
        analysis.setAnalysisPayload(Map.of(
                "requires_more_information", true,
                "clarification_questions", List.of(
                        Map.of("id", "q1", "field", "scope",
                                "question", "Which database and tables?"))));
        return analysis;
    }

    @Nested
    @DisplayName("submitAdditionalInformation")
    class SubmitAdditionalInformation {

        @Test
        @DisplayName("persists the answers and applies INFO_PROVIDED")
        void recordsAnswersAndTransitions() {
            Recommendation analysis = liveAnalysisRequestingInformation();
            when(workItemService.requireVisible(principal, WORK_ITEM_ID)).thenReturn(workItem);
            when(recommendations.findLatestLive(WORK_ITEM_ID, Recommendation.Type.ANALYSIS))
                    .thenReturn(Optional.of(analysis));

            when(transitions.transitionSummary(eq(principal), eq(WORK_ITEM_ID), any()))
                    .thenReturn(new WorkItemTransitionService.TransitionSummary(
                            WORK_ITEM_ID, "TCK-42", "AI_ANALYSIS"));

            List<AnalysisService.ClarificationAnswerInput> answers = List.of(
                    new AnalysisService.ClarificationAnswerInput("q1", "scope",
                            "The customers table in the reporting replica, read-only."));

            AnalysisService.AdditionalInformationResult result =
                    service.submitAdditionalInformation(principal, WORK_ITEM_ID, answers);

            assertThat(result.status()).isEqualTo("AI_ANALYSIS");
            assertThat(result.workItemId()).isEqualTo(WORK_ITEM_ID);
            assertThat(result.referenceKey()).isEqualTo("TCK-42");

            // The answer was recorded on the same recommendation instance that was looked up -
            // this is what makes it visible to the next analyze() call, see below.
            assertThat(analysis.getClarificationAnswers()).hasSize(1);
            Map<String, Object> stored = analysis.getClarificationAnswers().get(0);
            assertThat(stored.get("question_id")).isEqualTo("q1");
            assertThat(stored.get("field")).isEqualTo("scope");
            assertThat(stored.get("answer")).isEqualTo("The customers table in the reporting replica, read-only.");

            ArgumentCaptor<WorkItemTransitionService.Command> command =
                    ArgumentCaptor.forClass(WorkItemTransitionService.Command.class);
            verify(transitions).transitionSummary(eq(principal), eq(WORK_ITEM_ID), command.capture());
            assertThat(command.getValue().transition()).isEqualTo("INFO_PROVIDED");
        }

        @Test
        @DisplayName("rejects a blank question id or field by falling back to an empty string, never null")
        void blankOptionalFieldsAreStoredAsEmptyStrings() {
            Recommendation analysis = liveAnalysisRequestingInformation();
            when(workItemService.requireVisible(principal, WORK_ITEM_ID)).thenReturn(workItem);
            when(recommendations.findLatestLive(WORK_ITEM_ID, Recommendation.Type.ANALYSIS))
                    .thenReturn(Optional.of(analysis));
            when(transitions.transitionSummary(eq(principal), eq(WORK_ITEM_ID), any()))
                    .thenReturn(new WorkItemTransitionService.TransitionSummary(
                            WORK_ITEM_ID, "TCK-42", "AI_ANALYSIS"));

            service.submitAdditionalInformation(principal, WORK_ITEM_ID,
                    List.of(new AnalysisService.ClarificationAnswerInput(null, null, "Yes, approved by both.")));

            Map<String, Object> stored = analysis.getClarificationAnswers().get(0);
            assertThat(stored.get("question_id")).isEqualTo("");
            assertThat(stored.get("field")).isEqualTo("");
            assertThat(stored.get("answer")).isEqualTo("Yes, approved by both.");
        }

        @Test
        @DisplayName("fails with VALIDATION_FAILED when no analysis exists")
        void noAnalysisToAnswer() {
            when(workItemService.requireVisible(principal, WORK_ITEM_ID)).thenReturn(workItem);
            when(recommendations.findLatestLive(WORK_ITEM_ID, Recommendation.Type.ANALYSIS))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submitAdditionalInformation(principal, WORK_ITEM_ID,
                    List.of(new AnalysisService.ClarificationAnswerInput("q1", "scope", "answer"))))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

            verify(transitions, never()).transitionSummary(any(), any(), any());
        }

        @Test
        @DisplayName("fails with VALIDATION_FAILED when the latest analysis never asked for more information")
        void analysisDidNotRequestInformation() {
            Recommendation confidentAnalysis = new Recommendation(ORG_ID, WORK_ITEM_ID, Recommendation.Type.ANALYSIS);
            confidentAnalysis.setAnalysisPayload(Map.of("requires_more_information", false));

            when(workItemService.requireVisible(principal, WORK_ITEM_ID)).thenReturn(workItem);
            when(recommendations.findLatestLive(WORK_ITEM_ID, Recommendation.Type.ANALYSIS))
                    .thenReturn(Optional.of(confidentAnalysis));

            assertThatThrownBy(() -> service.submitAdditionalInformation(principal, WORK_ITEM_ID,
                    List.of(new AnalysisService.ClarificationAnswerInput("q1", "scope", "answer"))))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

            verify(transitions, never()).transitionSummary(any(), any(), any());
            assertThat(confidentAnalysis.getClarificationAnswers()).isNull();
        }

        @Test
        @DisplayName("propagates TRANSITION_NOT_PERMITTED from the workflow engine without persisting the answer")
        void authorizationComesFromTheWorkflowEngine() {
            Recommendation analysis = liveAnalysisRequestingInformation();
            when(workItemService.requireVisible(principal, WORK_ITEM_ID)).thenReturn(workItem);
            when(recommendations.findLatestLive(WORK_ITEM_ID, Recommendation.Type.ANALYSIS))
                    .thenReturn(Optional.of(analysis));
            when(transitions.transitionSummary(eq(principal), eq(WORK_ITEM_ID), any()))
                    .thenThrow(new ApiException(ErrorCode.TRANSITION_NOT_PERMITTED,
                            "You do not have permission to perform 'INFO_PROVIDED' on this item."));

            assertThatThrownBy(() -> service.submitAdditionalInformation(principal, WORK_ITEM_ID,
                    List.of(new AnalysisService.ClarificationAnswerInput("q1", "scope", "answer"))))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.TRANSITION_NOT_PERMITTED));
        }
    }

    @Nested
    @DisplayName("analyze")
    class Analyze {

        @Test
        @DisplayName("sends clarification answers stored on the previous live analysis")
        void carriesStoredAnswersIntoTheNextRequest() {
            WorkItemService.AnalysisView view = new WorkItemService.AnalysisView(
                    WORK_ITEM_ID, ORG_ID, null, null, null, null, "TCK-42",
                    "Give an intern production database access", "description", null,
                    OriginType.USER_CREATED, WorkItemType.REQUEST, Priority.HIGH, Instant.now(), null);
            when(workItemService.requireVisibleForAnalysisLocked(principal, WORK_ITEM_ID)).thenReturn(view);

            Recommendation previous = new Recommendation(ORG_ID, WORK_ITEM_ID, Recommendation.Type.ANALYSIS);
            previous.recordClarificationAnswers(List.of(
                    Map.of("question_id", "q1", "field", "scope", "answer", "Reporting replica, read-only.")));
            when(recommendations.findLatestLive(WORK_ITEM_ID, Recommendation.Type.ANALYSIS))
                    .thenReturn(Optional.of(previous));
            when(recommendations.save(any(Recommendation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            when(client.analyzeTicket(any())).thenReturn(
                    new AiAnalysisClient.Outcome<>(AiAnalysisClient.Status.UNAVAILABLE, null, null, "not needed here"));

            service.analyze(principal, WORK_ITEM_ID);

            ArgumentCaptor<AnalyzeTicketRequest> request = ArgumentCaptor.forClass(AnalyzeTicketRequest.class);
            verify(client).analyzeTicket(request.capture());

            List<ClarificationAnswer> sent = request.getValue().clarificationAnswers();
            assertThat(sent).hasSize(1);
            assertThat(sent.get(0).questionId()).isEqualTo("q1");
            assertThat(sent.get(0).field()).isEqualTo("scope");
            assertThat(sent.get(0).answer()).isEqualTo("Reporting replica, read-only.");

            // The previous analysis is superseded, not deleted - its answers remain readable
            // in the recommendation history even after this call.
            assertThat(previous.getStatus()).isEqualTo(Recommendation.Status.SUPERSEDED);
        }

        @Test
        @DisplayName("sends no clarification answers when the previous analysis never asked a question")
        void noStoredAnswersMeansAnEmptyList() {
            WorkItemService.AnalysisView view = new WorkItemService.AnalysisView(
                    WORK_ITEM_ID, ORG_ID, null, null, null, null, "TCK-42",
                    "A routine request", "description", null,
                    OriginType.USER_CREATED, WorkItemType.REQUEST, Priority.MEDIUM, Instant.now(), null);
            when(workItemService.requireVisibleForAnalysisLocked(principal, WORK_ITEM_ID)).thenReturn(view);
            when(recommendations.findLatestLive(WORK_ITEM_ID, Recommendation.Type.ANALYSIS))
                    .thenReturn(Optional.empty());
            when(recommendations.save(any(Recommendation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(client.analyzeTicket(any())).thenReturn(
                    new AiAnalysisClient.Outcome<>(AiAnalysisClient.Status.UNAVAILABLE, null, null, "not needed here"));

            service.analyze(principal, WORK_ITEM_ID);

            ArgumentCaptor<AnalyzeTicketRequest> request = ArgumentCaptor.forClass(AnalyzeTicketRequest.class);
            verify(client).analyzeTicket(request.capture());
            assertThat(request.getValue().clarificationAnswers()).isEmpty();
        }

        @Test
        @DisplayName("reuses the latest successful analysis when the work item has not changed")
        void reusesExistingSuccessfulAnalysis() {
            Instant sourceUpdatedAt = Instant.parse("2026-08-02T04:00:00Z");
            WorkItemService.AnalysisView view = new WorkItemService.AnalysisView(
                    WORK_ITEM_ID, ORG_ID, null, null, null, null, "TCK-42",
                    "A routine request", "description", null,
                    OriginType.USER_CREATED, WorkItemType.REQUEST, Priority.MEDIUM, sourceUpdatedAt, null);
            when(workItemService.requireVisibleForAnalysisLocked(principal, WORK_ITEM_ID)).thenReturn(view);

            Recommendation previous = new Recommendation(ORG_ID, WORK_ITEM_ID, Recommendation.Type.ANALYSIS);
            previous.setStatus(Recommendation.Status.PENDING);
            previous.setSourceUpdatedAt(sourceUpdatedAt);
            previous.setAnalysisPayload(Map.of("summary", "Already analyzed"));
            when(recommendations.findLatestLive(WORK_ITEM_ID, Recommendation.Type.ANALYSIS))
                    .thenReturn(Optional.of(previous));

            Recommendation result = service.analyze(principal, WORK_ITEM_ID);

            assertThat(result).isSameAs(previous);
            verify(client, never()).analyzeTicket(any());
            verify(recommendations, never()).save(any(Recommendation.class));
            verify(transitions, never()).applyAutomatic(WORK_ITEM_ID, "ANALYZE");
        }

        @Test
        @DisplayName("stamps the recommendation with the post-transition timestamp, "
                + "not the stale pre-transition snapshot")
        void storesTheSettledTimestampAfterTheInitialTransition() {
            // The very first analysis of a brand-new item fires ANALYZE (NEW -> AI_ANALYSIS),
            // which bumps the work item's own updated_at. `item` is read before that
            // transition (preTransition); a second, identically-timed request that raced this
            // one and blocked on the row lock will only ever observe the work item *after*
            // this transition committed (postTransition). Stamping the recommendation with
            // `item`'s pre-transition timestamp would make that racing request's reuse check
            // fail every time, defeating dedup for exactly the case it exists to cover.
            Instant preTransition = Instant.parse("2026-08-02T04:00:00Z");
            Instant postTransition = Instant.parse("2026-08-02T04:00:00.010Z");
            WorkItemService.AnalysisView beforeTransition = new WorkItemService.AnalysisView(
                    WORK_ITEM_ID, ORG_ID, null, null, null, null, "TCK-42",
                    "A routine request", "description", null,
                    OriginType.USER_CREATED, WorkItemType.REQUEST, Priority.MEDIUM, preTransition, null);
            WorkItemService.AnalysisView afterTransition = new WorkItemService.AnalysisView(
                    WORK_ITEM_ID, ORG_ID, null, null, null, null, "TCK-42",
                    "A routine request", "description", null,
                    OriginType.USER_CREATED, WorkItemType.REQUEST, Priority.MEDIUM, postTransition, null);
            when(workItemService.requireVisibleForAnalysisLocked(principal, WORK_ITEM_ID))
                    .thenReturn(beforeTransition, afterTransition);
            when(recommendations.findLatestLive(WORK_ITEM_ID, Recommendation.Type.ANALYSIS))
                    .thenReturn(Optional.empty());
            when(recommendations.save(any(Recommendation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(client.analyzeTicket(any())).thenReturn(
                    new AiAnalysisClient.Outcome<>(AiAnalysisClient.Status.UNAVAILABLE, null, null, "down"));

            Recommendation result = service.analyze(principal, WORK_ITEM_ID);

            assertThat(result.getSourceUpdatedAt()).isEqualTo(postTransition);
            assertThat(result.getSourceUpdatedAt()).isNotEqualTo(preTransition);
        }

        @Test
        @DisplayName("a duplicate request that only sees the work item after the first one's "
                + "ANALYZE transition reuses that result instead of calling the provider again")
        void racingDuplicateRequestReusesTheSettledAnalysis() {
            Instant settledAt = Instant.parse("2026-08-02T04:00:00.010Z");
            // What a second, near-simultaneous caller sees once it acquires the row lock: the
            // work item already moved past NEW by the first call's ANALYZE transition.
            WorkItemService.AnalysisView view = new WorkItemService.AnalysisView(
                    WORK_ITEM_ID, ORG_ID, null, null, null, null, "TCK-42",
                    "A routine request", "description", null,
                    OriginType.USER_CREATED, WorkItemType.REQUEST, Priority.MEDIUM, settledAt, null);
            when(workItemService.requireVisibleForAnalysisLocked(principal, WORK_ITEM_ID)).thenReturn(view);

            Recommendation firstCallsResult = new Recommendation(ORG_ID, WORK_ITEM_ID, Recommendation.Type.ANALYSIS);
            firstCallsResult.setStatus(Recommendation.Status.PENDING);
            firstCallsResult.setSourceUpdatedAt(settledAt);
            firstCallsResult.setAnalysisPayload(Map.of("summary", "Already analyzed"));
            when(recommendations.findLatestLive(WORK_ITEM_ID, Recommendation.Type.ANALYSIS))
                    .thenReturn(Optional.of(firstCallsResult));

            Recommendation result = service.analyze(principal, WORK_ITEM_ID);

            assertThat(result).isSameAs(firstCallsResult);
            verify(client, never()).analyzeTicket(any());
            verify(recommendations, never()).save(any(Recommendation.class));
            verify(transitions, never()).applyAutomatic(WORK_ITEM_ID, "ANALYZE");
        }

        @Test
        @DisplayName("fires ANALYZE unconditionally, even when the AI call itself fails")
        void firesAnalyzeUnconditionally() {
            stubBaseline();
            when(client.analyzeTicket(any())).thenReturn(
                    new AiAnalysisClient.Outcome<>(AiAnalysisClient.Status.UNAVAILABLE, null, null, "down"));

            service.analyze(principal, WORK_ITEM_ID);

            verify(transitions).applyAutomatic(WORK_ITEM_ID, "ANALYZE");
        }

        @Test
        @DisplayName("fires NEEDS_INFO and records the questions when the AI asks for more information")
        void movesToWaitingInformationAndRecordsQuestions() {
            stubBaseline();
            AnalysisResponse response = analysisResponse(true, List.of(
                    new ClarificationQuestion("q1", "Which environment?", "environment", true)));
            when(client.analyzeTicket(any())).thenReturn(new AiAnalysisClient.Outcome<>(
                    AiAnalysisClient.Status.OK, new AnalysisEnvelope(response, null), null, null));
            when(transitions.applyAutomatic(WORK_ITEM_ID, "NEEDS_INFO")).thenReturn(Optional.of(resolvedTransition()));

            service.analyze(principal, WORK_ITEM_ID);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Map<String, Object>>> questions = ArgumentCaptor.forClass(List.class);
            verify(workItemService).recordPendingClarification(eq(WORK_ITEM_ID), questions.capture());
            assertThat(questions.getValue()).hasSize(1);
            Map<String, Object> stored = questions.getValue().get(0);
            assertThat(stored.get("id")).isEqualTo("q1");
            assertThat(stored.get("question")).isEqualTo("Which environment?");
            assertThat(stored.get("field")).isEqualTo("environment");
            assertThat(stored.get("required")).isEqualTo(true);
            verify(workItemService, never()).clearPendingClarification(any());
        }

        @Test
        @DisplayName("does not record pending clarification when the workflow profile has no NEEDS_INFO edge")
        void doesNotRecordWhenTheTransitionDoesNotResolve() {
            stubBaseline();
            AnalysisResponse response = analysisResponse(true, List.of(
                    new ClarificationQuestion("q1", "Which environment?", "environment", true)));
            when(client.analyzeTicket(any())).thenReturn(new AiAnalysisClient.Outcome<>(
                    AiAnalysisClient.Status.OK, new AnalysisEnvelope(response, null), null, null));
            when(transitions.applyAutomatic(WORK_ITEM_ID, "NEEDS_INFO")).thenReturn(Optional.empty());

            service.analyze(principal, WORK_ITEM_ID);

            verify(workItemService, never()).recordPendingClarification(any(), any());
        }

        @Test
        @DisplayName("clears pending clarification once the fresh analysis no longer needs more information")
        void clearsPendingClarificationWhenNoLongerNeeded() {
            stubBaseline();
            AnalysisResponse response = analysisResponse(false, List.of());
            when(client.analyzeTicket(any())).thenReturn(new AiAnalysisClient.Outcome<>(
                    AiAnalysisClient.Status.OK, new AnalysisEnvelope(response, null), null, null));

            service.analyze(principal, WORK_ITEM_ID);

            verify(workItemService).clearPendingClarification(WORK_ITEM_ID);
            verify(transitions, never()).applyAutomatic(WORK_ITEM_ID, "NEEDS_INFO");
        }

        private void stubBaseline() {
            WorkItemService.AnalysisView view = new WorkItemService.AnalysisView(
                    WORK_ITEM_ID, ORG_ID, null, null, null, null, "TCK-42",
                    "Give an intern production database access", "description", null,
                    OriginType.USER_CREATED, WorkItemType.REQUEST, Priority.HIGH, Instant.now(), null);
            when(workItemService.requireVisibleForAnalysisLocked(principal, WORK_ITEM_ID)).thenReturn(view);
            when(recommendations.findLatestLive(WORK_ITEM_ID, Recommendation.Type.ANALYSIS))
                    .thenReturn(Optional.empty());
            when(recommendations.save(any(Recommendation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
        }

        private AnalysisResponse analysisResponse(
                boolean requiresMoreInformation, List<ClarificationQuestion> questions) {
            return new AnalysisResponse(
                    WorkFamily.SERVICE_WORK, WorkItemType.REQUEST, "general", "summary",
                    null, null, List.of(), List.of(), List.of(),
                    Priority.MEDIUM, null, null, List.of(), null, null,
                    requiresMoreInformation, questions,
                    false, null, null, new BigDecimal("0.80"), List.of(),
                    false, false, false, false,
                    null, null, null, false, null, false, null);
        }

        private WorkflowEngine.ResolvedTransition resolvedTransition() {
            WorkflowCatalog.StateView targetState = new WorkflowCatalog.StateView(
                    "WAITING_INFORMATION", "Waiting for information", StateCategory.TODO,
                    false, false, false, false, false, false);
            return new WorkflowEngine.ResolvedTransition(
                    "NEEDS_INFO", "Needs information", "AI_ANALYSIS", "WAITING_INFORMATION", targetState);
        }
    }
}
