package com.smartwork.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartwork.analysis.domain.Recommendation;
import com.smartwork.analysis.infrastructure.RecommendationRepository;
import com.smartwork.identity.domain.SystemRole;
import com.smartwork.support.IntegrationTest;
import com.smartwork.support.PostgresContainerConfig;
import com.smartwork.support.TestFixtures;
import com.smartwork.support.WorkItemTestClient;
import com.smartwork.workitem.domain.WorkItem;
import com.smartwork.workitem.infrastructure.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage for {@code POST /work-items/{id}/additional-information}, now served
 * by {@code ClarificationController} (moved out of {@code AnalysisController} — see the
 * comment left in its place).
 *
 * <p>The AI service is not reachable from this test environment (no stub bean is registered
 * for {@code AiAnalysisClient}; see {@code AiServiceProperties}'s default {@code baseUrl}),
 * so every reanalysis triggered here comes back {@code UNAVAILABLE} and the recommendation
 * it produces is stored as {@code FAILED}. That is enough to exercise the orchestration this
 * endpoint now performs — persist answers, move back to {@code AI_ANALYSIS}, reanalyze, and
 * skip routing because the fresh analysis is not usable — without depending on a live model.
 *
 * <p>Each test arranges the {@code WAITING_INFORMATION} starting state directly against the
 * repositories, the same way the real {@code NEEDS_INFO} transition would have left it.
 */
@IntegrationTest
@Import({PostgresContainerConfig.class, WorkItemTestClient.class})
@DisplayName("Submitting answers to AI clarification questions")
class AdditionalInformationIT {

    @Autowired
    private WorkItemTestClient client;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private WorkItemRepository workItems;

    @Autowired
    private RecommendationRepository recommendations;

    private UUID organizationId;
    private UUID departmentId;
    private String requesterEmail;
    private String requesterToken;
    private String strangerToken;

    @BeforeEach
    void seed() {
        organizationId = fixtures.organization("CLARIFY-" + UUID.randomUUID().toString().substring(0, 8));
        departmentId = fixtures.department(organizationId, "IT").id();

        requesterEmail = "requester-" + UUID.randomUUID() + "@acme.io";
        String strangerEmail = "stranger-" + UUID.randomUUID() + "@acme.io";

        fixtures.user(organizationId, requesterEmail, Set.of(SystemRole.EMPLOYEE), departmentId);
        fixtures.user(organizationId, strangerEmail, Set.of(SystemRole.EMPLOYEE), departmentId);

        requesterToken = client.tokenFor(requesterEmail);
        strangerToken = client.tokenFor(strangerEmail);
    }

    @Test
    @DisplayName("The requester answers, the item is reanalyzed, and the answers are preserved on the "
            + "now-superseded recommendation that held them")
    void requesterCanAnswer() {
        UUID ticketId = createWaitingForInformationTicket();

        JsonNode response = client.raw(requesterToken, "POST",
                "/api/v1/work-items/" + ticketId + "/additional-information", """
                {"answers":[{"questionId":"q1","field":"scope","answer":"Reporting replica, read-only."}]}
                """, HttpStatus.OK);

        // ANALYZE is a no-op from AI_ANALYSIS (there is no such edge), so the item stays put;
        // the AI call itself comes back UNAVAILABLE, so it never reaches WAITING_INFORMATION
        // again either. Reanalysis genuinely ran, though - see the FAILED recommendation below.
        assertThat(response.get("status").asText()).isEqualTo("AI_ANALYSIS");
        assertThat(response.get("workItemId").asText()).isEqualTo(ticketId.toString());
        assertThat(response.get("routed").asBoolean()).isFalse();
        assertThat(response.get("recommendation").get("status").asText()).isEqualTo("FAILED");

        JsonNode ticket = client.get(requesterToken, ticketId);
        assertThat(ticket.get("status").asText()).isEqualTo("AI_ANALYSIS");

        // The recommendation that held the answers was superseded by the reanalysis that
        // followed in the same request - find it by its answers, not by "latest live".
        Recommendation stored = recommendations.findByWorkItemIdOrderByCreatedAtDesc(ticketId).stream()
                .filter(r -> r.getClarificationAnswers() != null && !r.getClarificationAnswers().isEmpty())
                .findFirst()
                .orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(Recommendation.Status.SUPERSEDED);
        assertThat(stored.getClarificationAnswers()).hasSize(1);
        assertThat(stored.getClarificationAnswers().get(0))
                .containsEntry("question_id", "q1")
                .containsEntry("field", "scope")
                .containsEntry("answer", "Reporting replica, read-only.");

        // The reanalysis that superseded it is the latest recommendation of any kind, and it
        // failed (AI unreachable in this environment) rather than being silently dropped.
        Recommendation latest = recommendations.findByWorkItemIdOrderByCreatedAtDesc(ticketId).get(0);
        assertThat(latest.getStatus()).isEqualTo(Recommendation.Status.FAILED);
    }

    @Test
    @DisplayName("Someone with no relationship to the ticket cannot answer")
    void strangerCannotAnswer() {
        UUID ticketId = createWaitingForInformationTicket();

        JsonNode refused = client.raw(strangerToken, "POST",
                "/api/v1/work-items/" + ticketId + "/additional-information", """
                {"answers":[{"questionId":"q1","field":"scope","answer":"anything"}]}
                """, HttpStatus.FORBIDDEN);

        assertThat(refused.get("code").asText()).isEqualTo("TRANSITION_NOT_PERMITTED");
    }

    @Test
    @DisplayName("Answering is rejected when there is no analysis on the item at all")
    void rejectsWhenNoAnalysisExists() {
        UUID ticketId = UUID.fromString(client.create(requesterToken, """
                {"workItemType":"REQUEST","summary":"A routine ticket, never analyzed"}
                """).get("id").asText());

        JsonNode refused = client.raw(requesterToken, "POST",
                "/api/v1/work-items/" + ticketId + "/additional-information", """
                {"answers":[{"questionId":"q1","field":"scope","answer":"anything"}]}
                """, HttpStatus.BAD_REQUEST);

        assertThat(refused.get("code").asText()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("Answering is rejected when the latest analysis never asked for more information")
    void rejectsWhenAnalysisDidNotRequestInformation() {
        UUID ticketId = UUID.fromString(client.create(requesterToken, """
                {"workItemType":"REQUEST","summary":"A ticket with a settled analysis"}
                """).get("id").asText());

        Recommendation analysis = new Recommendation(organizationId, ticketId, Recommendation.Type.ANALYSIS);
        analysis.setStatus(Recommendation.Status.AWAITING_APPROVAL);
        analysis.setAnalysisPayload(Map.of("requires_more_information", false));
        recommendations.save(analysis);

        JsonNode refused = client.raw(requesterToken, "POST",
                "/api/v1/work-items/" + ticketId + "/additional-information", """
                {"answers":[{"questionId":"q1","field":"scope","answer":"anything"}]}
                """, HttpStatus.BAD_REQUEST);

        assertThat(refused.get("code").asText()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("An empty answer list is rejected by request validation")
    void emptyAnswersRejected() {
        UUID ticketId = createWaitingForInformationTicket();

        client.raw(requesterToken, "POST",
                "/api/v1/work-items/" + ticketId + "/additional-information",
                "{\"answers\":[]}", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("A freshly created item leaves NEW as soon as POST /analyze runs")
    void newItemMovesOutOfNewOnAnalyze() {
        UUID ticketId = UUID.fromString(client.create(requesterToken, """
                {"workItemType":"REQUEST","summary":"A brand new ticket, never analyzed"}
                """).get("id").asText());
        assertThat(workItems.findById(ticketId).orElseThrow().getStatus()).isEqualTo("NEW");

        client.raw(requesterToken, "POST", "/api/v1/work-items/" + ticketId + "/analyze", null, HttpStatus.OK);

        // ANALYZE (NEW -> AI_ANALYSIS) fires unconditionally at the start of analyze(), so the
        // item is out of the inbox regardless of whether the AI call itself succeeds.
        assertThat(workItems.findById(ticketId).orElseThrow().getStatus()).isEqualTo("AI_ANALYSIS");
    }

    @Test
    @DisplayName("Pending clarification questions mirrored onto the work item are exposed on its detail response")
    void pendingClarificationQuestionsAppearInDetail() {
        UUID ticketId = UUID.fromString(client.create(requesterToken, """
                {"workItemType":"REQUEST","summary":"Give an intern production database access"}
                """).get("id").asText());

        WorkItem item = workItems.findById(ticketId).orElseThrow();
        item.recordPendingClarification(List.of(Map.of(
                "id", "q1", "question", "Which database and tables?", "field", "scope", "required", true)));
        workItems.save(item);

        JsonNode detail = client.get(requesterToken, ticketId);

        JsonNode questions = detail.get("pendingClarificationQuestions");
        assertThat(questions).hasSize(1);
        assertThat(questions.get(0).get("id").asText()).isEqualTo("q1");
        assertThat(questions.get(0).get("question").asText()).isEqualTo("Which database and tables?");
        assertThat(questions.get(0).get("field").asText()).isEqualTo("scope");
        assertThat(questions.get(0).get("required").asBoolean()).isTrue();
    }

    // ------------------------------------------------------------------------------- helpers

    /**
     * Builds a ticket positioned as the flow expects: a live {@code ANALYSIS} recommendation
     * that requested more information, and the item in {@code WAITING_INFORMATION}, with
     * {@code requesterEmail} as its reporter (so it holds the REQUESTER relationship).
     */
    private UUID createWaitingForInformationTicket() {
        JsonNode created = client.create(requesterToken, """
                {"workItemType":"REQUEST","summary":"Give an intern production database access"}
                """);
        UUID ticketId = UUID.fromString(created.get("id").asText());

        WorkItem item = workItems.findById(ticketId).orElseThrow();
        item.applyTransition("WAITING_INFORMATION", false, false, false, null, Instant.now());
        workItems.save(item);

        Recommendation analysis = new Recommendation(organizationId, ticketId, Recommendation.Type.ANALYSIS);
        analysis.setStatus(Recommendation.Status.PENDING);
        analysis.setAnalysisPayload(Map.of(
                "requires_more_information", true,
                "clarification_questions", List.of(
                        Map.of("id", "q1", "field", "scope", "question", "Which database and tables?"))));
        recommendations.save(analysis);

        return ticketId;
    }
}
