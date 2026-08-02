package com.smartwork.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartwork.identity.domain.SystemRole;
import com.smartwork.support.IntegrationTest;
import com.smartwork.support.PostgresContainerConfig;
import com.smartwork.support.TestFixtures;
import com.smartwork.support.WorkItemTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demo scenario 3, end to end: a support ticket becomes a linked bug, the bug is fixed,
 * and the ticket comes back to the person who reported it.
 *
 * <p>The point of the scenario is that the requester never learns what a bug ticket is.
 * They file a problem and get told it is fixed.
 */
@IntegrationTest
@Import({PostgresContainerConfig.class, WorkItemTestClient.class})
@DisplayName("Ticket to development conversion")
class TicketToBugIT {

    @Autowired private WorkItemTestClient client;
    @Autowired private TestFixtures fixtures;

    private String requesterToken;
    private String leadToken;
    private UUID developerId;
    private UUID projectId;

    @BeforeEach
    void seed() {
        UUID organizationId = fixtures.organization("TICKET2BUG");
        UUID departmentId = fixtures.department(organizationId, "ENGINEERING").id();

        String requesterEmail = "req-" + UUID.randomUUID() + "@acme.io";
        String leadEmail = "lead-" + UUID.randomUUID() + "@acme.io";
        String devEmail = "dev-" + UUID.randomUUID() + "@acme.io";

        fixtures.user(organizationId, requesterEmail, Set.of(SystemRole.EMPLOYEE), departmentId);
        UUID leadId = fixtures.user(organizationId, leadEmail,
                Set.of(SystemRole.EMPLOYEE, SystemRole.TEAM_LEAD), departmentId);
        developerId = fixtures.user(organizationId, devEmail, Set.of(SystemRole.EMPLOYEE), departmentId);

        UUID teamId = fixtures.team(organizationId, departmentId, leadId);
        fixtures.addToTeam(organizationId, teamId, developerId);
        projectId = fixtures.project(organizationId, departmentId);

        requesterToken = client.tokenFor(requesterEmail);
        leadToken = client.tokenFor(leadEmail);
    }

    @Test
    void aTicketIsHandedBackToItsRequesterWhenTheLinkedBugIsVerified() {
        // 1. The requester files a problem in their own words.
        UUID ticketId = id(client.create(requesterToken, """
                {"workItemType":"REQUEST","summary":"Payments fail intermittently during checkout",
                 "description":"About one in twenty checkouts shows a generic error."}
                """));

        // 2. Engineering raises a bug for the defect and links the two. The link is
        //    cross-family, which must stay legal - it is the whole conversion.
        UUID bugId = id(client.create(leadToken, """
                {"workItemType":"BUG","summary":"Idempotency key collision on retried checkout",
                 "projectId":"%s","estimatedHours":6}
                """.formatted(projectId)));

        client.link(leadToken, ticketId, """
                {"linkType":"RESOLVED_BY","targetId":"%s"}""".formatted(bugId), HttpStatus.CREATED);

        JsonNode ticketLinks = client.links(requesterToken, ticketId);
        assertThat(ticketLinks.get("outgoing")).anySatisfy(link ->
                assertThat(link.get("linkType").asText()).isEqualTo("RESOLVED_BY"));

        // The bug end reads naturally too, without a second stored row.
        JsonNode bugLinks = client.links(leadToken, bugId);
        assertThat(bugLinks.get("incoming")).anySatisfy(link ->
                assertThat(link.get("linkType").asText()).isEqualTo("RESOLVES"));

        // 3. The ticket parks while development happens. Its SLA clock is paused here.
        client.transition(leadToken, ticketId, """
                {"transition":"ASSIGN","assigneeId":"%s"}""".formatted(developerId), HttpStatus.OK);
        client.transition(leadToken, ticketId, "{\"transition\":\"START_WORK\"}", HttpStatus.OK);
        client.transition(leadToken, ticketId, """
                {"transition":"CONVERT_TO_DEV","reason":"Confirmed defect in the payment gateway"}
                """, HttpStatus.OK);

        assertThat(client.get(requesterToken, ticketId).get("status").asText())
                .isEqualTo("WAITING_DEVELOPMENT");

        // 4. The bug runs its own workflow, which is not the ticket's.
        driveBugToVerified(bugId);

        // 5. The ticket is released automatically. Nobody had to remember to do this.
        JsonNode ticket = client.get(requesterToken, ticketId);
        assertThat(ticket.get("status").asText()).isEqualTo("RESOLVED");

        // 6. The requester - not the developer, not the lead - confirms the fix.
        client.transition(requesterToken, ticketId, "{\"transition\":\"VERIFY\"}", HttpStatus.OK);
        assertThat(client.get(requesterToken, ticketId).get("status").asText()).isEqualTo("CLOSED");
    }

    @Test
    void aTicketIsNotReleasedWhileAnyLinkedBugIsOutstanding() {
        UUID ticketId = id(client.create(requesterToken, """
                {"workItemType":"REQUEST","summary":"Checkout is broken in two different ways"}
                """));
        UUID firstBug = id(client.create(leadToken, """
                {"workItemType":"BUG","summary":"Idempotency key collision","projectId":"%s"}
                """.formatted(projectId)));
        UUID secondBug = id(client.create(leadToken, """
                {"workItemType":"BUG","summary":"Currency rounding error on refunds","projectId":"%s"}
                """.formatted(projectId)));

        client.link(leadToken, ticketId, """
                {"linkType":"RESOLVED_BY","targetId":"%s"}""".formatted(firstBug), HttpStatus.CREATED);
        client.link(leadToken, ticketId, """
                {"linkType":"RESOLVED_BY","targetId":"%s"}""".formatted(secondBug), HttpStatus.CREATED);

        client.transition(leadToken, ticketId, """
                {"transition":"ASSIGN","assigneeId":"%s"}""".formatted(developerId), HttpStatus.OK);
        client.transition(leadToken, ticketId, "{\"transition\":\"START_WORK\"}", HttpStatus.OK);
        client.transition(leadToken, ticketId, """
                {"transition":"CONVERT_TO_DEV","reason":"Two separate defects confirmed"}
                """, HttpStatus.OK);

        driveBugToVerified(firstBug);

        // One down, one to go: the requester must not be told it is fixed yet.
        assertThat(client.get(requesterToken, ticketId).get("status").asText())
                .isEqualTo("WAITING_DEVELOPMENT");

        driveBugToVerified(secondBug);

        assertThat(client.get(requesterToken, ticketId).get("status").asText()).isEqualTo("RESOLVED");
    }

    @Test
    void onlyTheRequesterMayVerifyAConvertedTicket() {
        UUID ticketId = id(client.create(requesterToken, """
                {"workItemType":"REQUEST","summary":"Payments fail during checkout every morning"}
                """));
        UUID bugId = id(client.create(leadToken, """
                {"workItemType":"BUG","summary":"Connection pool exhausted at peak","projectId":"%s"}
                """.formatted(projectId)));

        client.link(leadToken, ticketId, """
                {"linkType":"RESOLVED_BY","targetId":"%s"}""".formatted(bugId), HttpStatus.CREATED);
        client.transition(leadToken, ticketId, """
                {"transition":"ASSIGN","assigneeId":"%s"}""".formatted(developerId), HttpStatus.OK);
        client.transition(leadToken, ticketId, "{\"transition\":\"START_WORK\"}", HttpStatus.OK);
        client.transition(leadToken, ticketId, """
                {"transition":"CONVERT_TO_DEV","reason":"Reproduced against the staging gateway"}
                """, HttpStatus.OK);
        driveBugToVerified(bugId);

        // The lead fixed it, so the lead does not get to declare it fixed.
        JsonNode refused = client.transition(leadToken, ticketId,
                "{\"transition\":\"VERIFY\"}", HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.get("errors"))
                .anyMatch(e -> e.get("code").asText().equals("REQUIRE_VERIFICATION_ACTOR"));

        client.transition(requesterToken, ticketId, "{\"transition\":\"VERIFY\"}", HttpStatus.OK);
    }

    private void driveBugToVerified(UUID bugId) {
        client.transition(leadToken, bugId, """
                {"transition":"START_ANALYSIS","assigneeId":"%s"}""".formatted(developerId), HttpStatus.OK);
        client.transition(leadToken, bugId, "{\"transition\":\"ROOT_CAUSE_FOUND\"}", HttpStatus.OK);
        client.transition(leadToken, bugId, "{\"transition\":\"START_WORK\"}", HttpStatus.OK);
        client.transition(leadToken, bugId, "{\"transition\":\"SUBMIT_FOR_REVIEW\"}", HttpStatus.OK);
        client.transition(leadToken, bugId, "{\"transition\":\"APPROVE_REVIEW\"}", HttpStatus.OK);
        client.transition(leadToken, bugId, "{\"transition\":\"QA_PASSED\"}", HttpStatus.OK);
    }

    private static UUID id(JsonNode created) {
        return UUID.fromString(created.get("id").asText());
    }
}
