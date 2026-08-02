package com.smartwork.policy.application;

import com.smartwork.analysis.application.AnalysisService;
import com.smartwork.analysis.application.RecommendationAccess;
import com.smartwork.assignment.application.CandidateDiscovery;
import com.smartwork.assignment.application.PolicyResolver;
import com.smartwork.assignment.application.RankingService;
import com.smartwork.assignment.domain.ScoringWeights;
import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.identity.application.SkillService;
import com.smartwork.organization.application.DepartmentDirectory;
import com.smartwork.policy.domain.ApprovalRoute;
import com.smartwork.policy.domain.RoutingDecision;
import com.smartwork.team.application.TeamDirectory;
import com.smartwork.workitem.application.WorkItemAssignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WorkItemRoutingService#resumeAfterAdditionalInformation} - the
 * "answer, reanalyze, reroute" orchestration that only this module can perform, since
 * {@code analysis} is not permitted to depend on {@code policy} (see the two modules'
 * {@code allowedDependencies}).
 *
 * <p>{@link WorkItemRoutingService#route} itself is exercised elsewhere; here it is
 * stubbed via a partial mock so these tests stay focused on the decision this method adds
 * on top of it: whether to route at all.
 */
class WorkItemRoutingServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID WORK_ITEM_ID = UUID.randomUUID();

    private WorkItemAssignment workItems;
    private RecommendationAccess recommendations;
    private AnalysisService analysisService;
    private WorkItemRoutingService spyService;

    private AuthPrincipal principal;
    private List<AnalysisService.ClarificationAnswerInput> answers;

    @BeforeEach
    void setUp() {
        workItems = mock(WorkItemAssignment.class);
        recommendations = mock(RecommendationAccess.class);
        analysisService = mock(AnalysisService.class);

        WorkItemRoutingService service = new WorkItemRoutingService(
                workItems, recommendations, mock(RankingService.class), mock(RoutingService.class),
                mock(RoutingInputsAssembler.class), mock(PolicyResolver.class), mock(SkillService.class),
                mock(DepartmentDirectory.class), mock(TeamDirectory.class), analysisService);
        spyService = Mockito.spy(service);

        principal = new AuthPrincipal(UUID.randomUUID(), ORG_ID, null, Set.of(), Set.of("EMPLOYEE"), "MID", false);
        answers = List.of(new AnalysisService.ClarificationAnswerInput("q1", "scope", "answer"));
    }

    private WorkItemAssignment.RoutingView routingView(String status) {
        return new WorkItemAssignment.RoutingView(
                WORK_ITEM_ID, ORG_ID, "TCK-1", status, null, null, null, null, null, null, null,
                "REQUEST", "SERVICE_WORK", null, null, null, null,
                false, false, false, false, true, Instant.now());
    }

    private RecommendationAccess.View analysisView(String status, Map<String, Object> payload) {
        return new RecommendationAccess.View(
                UUID.randomUUID(), ORG_ID, WORK_ITEM_ID, "ANALYSIS", status,
                null, null, null, Instant.now(), payload);
    }

    @Test
    @DisplayName("routes once the fresh analysis is usable and does not ask for more information")
    void routesWhenTheFreshAnalysisIsUsable() {
        when(analysisService.answerClarificationAndReanalyze(principal, WORK_ITEM_ID, answers))
                .thenReturn("AI_ANALYSIS");
        when(recommendations.findLatestAnalysis(ORG_ID, WORK_ITEM_ID))
                .thenReturn(Optional.of(analysisView("PENDING", Map.of("requires_more_information", false))));
        when(workItems.routingView(ORG_ID, WORK_ITEM_ID)).thenReturn(routingView("ASSIGNED"));

        RoutingService.Outcome outcome = new RoutingService.Outcome(
                new RoutingDecision(
                        ApprovalRoute.AUTO_ASSIGN, List.of(), "auto-assigned", Duration.ZERO, null, null, 1),
                Optional.empty(), Optional.empty(),
                new RankingService.Ranking(
                        List.of(), Map.of(), CandidateDiscovery.Tier.RECOMMENDED_TEAM, false,
                        ScoringWeights.defaults()));
        doReturn(outcome).when(spyService).route(principal, WORK_ITEM_ID);

        WorkItemRoutingService.AdditionalInformationOutcome result =
                spyService.resumeAfterAdditionalInformation(principal, WORK_ITEM_ID, answers);

        verify(spyService).route(principal, WORK_ITEM_ID);
        assertThat(result.routing()).isSameAs(outcome);
        assertThat(result.status()).isEqualTo("ASSIGNED");
        assertThat(result.workItemId()).isEqualTo(WORK_ITEM_ID);
        assertThat(result.referenceKey()).isEqualTo("TCK-1");
    }

    @Test
    @DisplayName("skips routing when the item is back in WAITING_INFORMATION")
    void skipsRoutingWhenStillWaitingForInformation() {
        when(analysisService.answerClarificationAndReanalyze(principal, WORK_ITEM_ID, answers))
                .thenReturn("WAITING_INFORMATION");
        when(recommendations.findLatestAnalysis(ORG_ID, WORK_ITEM_ID))
                .thenReturn(Optional.of(analysisView("PENDING", Map.of("requires_more_information", true))));
        when(workItems.routingView(ORG_ID, WORK_ITEM_ID)).thenReturn(routingView("WAITING_INFORMATION"));

        WorkItemRoutingService.AdditionalInformationOutcome result =
                spyService.resumeAfterAdditionalInformation(principal, WORK_ITEM_ID, answers);

        verify(spyService, never()).route(any(), any());
        assertThat(result.routing()).isNull();
        assertThat(result.status()).isEqualTo("WAITING_INFORMATION");
    }

    @Test
    @DisplayName("skips routing when the fresh analysis failed rather than routing on nothing")
    void skipsRoutingWhenTheFreshAnalysisFailed() {
        when(analysisService.answerClarificationAndReanalyze(principal, WORK_ITEM_ID, answers))
                .thenReturn("AI_ANALYSIS");
        when(recommendations.findLatestAnalysis(ORG_ID, WORK_ITEM_ID))
                .thenReturn(Optional.of(analysisView("FAILED", null)));
        when(workItems.routingView(ORG_ID, WORK_ITEM_ID)).thenReturn(routingView("AI_ANALYSIS"));

        WorkItemRoutingService.AdditionalInformationOutcome result =
                spyService.resumeAfterAdditionalInformation(principal, WORK_ITEM_ID, answers);

        verify(spyService, never()).route(any(), any());
        assertThat(result.routing()).isNull();
    }

    @Test
    @DisplayName("does not propagate a routing failure - the saved answers must not be rolled back by it")
    void routingFailureDoesNotPropagate() {
        when(analysisService.answerClarificationAndReanalyze(principal, WORK_ITEM_ID, answers))
                .thenReturn("AI_ANALYSIS");
        when(recommendations.findLatestAnalysis(ORG_ID, WORK_ITEM_ID))
                .thenReturn(Optional.of(analysisView("PENDING", Map.of("requires_more_information", false))));
        when(workItems.routingView(ORG_ID, WORK_ITEM_ID)).thenReturn(routingView("AI_ANALYSIS"));

        doThrow(new ApiException(ErrorCode.VALIDATION_FAILED, "no eligible candidate"))
                .when(spyService).route(principal, WORK_ITEM_ID);

        assertThatCode(() -> {
            WorkItemRoutingService.AdditionalInformationOutcome result =
                    spyService.resumeAfterAdditionalInformation(principal, WORK_ITEM_ID, answers);
            assertThat(result.routing()).isNull();
        }).doesNotThrowAnyException();
    }
}
