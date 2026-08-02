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

@IntegrationTest
@Import({PostgresContainerConfig.class, WorkItemTestClient.class})
@DisplayName("Work item lifecycles")
class WorkItemLifecycleIT {

    @Autowired private WorkItemTestClient client;
    @Autowired private TestFixtures fixtures;

    private String employeeToken;
    private String leadToken;
    private UUID employeeId;
    private UUID teamId;
    private UUID projectId;

    @BeforeEach
    void seed() {
        UUID organizationId = fixtures.organization("LIFECYCLE");
        UUID departmentId = fixtures.department(organizationId, "ENGINEERING").id();

        String employeeEmail = "emp-" + UUID.randomUUID() + "@acme.io";
        String leadEmail = "lead-" + UUID.randomUUID() + "@acme.io";
        employeeId = fixtures.user(organizationId, employeeEmail, Set.of(SystemRole.EMPLOYEE), departmentId);
        UUID leadId = fixtures.user(organizationId, leadEmail,
                Set.of(SystemRole.EMPLOYEE, SystemRole.TEAM_LEAD), departmentId);

        teamId = fixtures.team(organizationId, departmentId, leadId);
        fixtures.addToTeam(organizationId, teamId, employeeId);
        projectId = fixtures.project(organizationId, departmentId);

        employeeToken = client.tokenFor(employeeEmail);
        leadToken = client.tokenFor(leadEmail);
    }

    @Test
    @DisplayName("A ticket gets a TCK key, the SERVICE profile and no project")
    void ticketCreation() {
        JsonNode ticket = client.create(employeeToken, """
                {"workItemType":"REQUEST","summary":"My laptop will not connect to the VPN"}
                """);

        assertThat(ticket.get("referenceKey").asText()).startsWith("TCK-");
        assertThat(ticket.get("workFamily").asText()).isEqualTo("SERVICE_WORK");
        assertThat(ticket.get("workflowCode").asText()).isEqualTo("SERVICE");
        assertThat(ticket.get("status").asText()).isEqualTo("NEW");
        assertThat(ticket.get("statusCategory").asText()).isEqualTo("TODO");
        // Requester verification, because the person who reported it confirms the fix.
        assertThat(ticket.get("closureType").asText()).isEqualTo("REQUESTER_VERIFICATION");
        assertThat(ticket.get("relationships")).anyMatch(r -> r.asText().equals("REQUESTER"));
    }

    @Test
    @DisplayName("A task needs no ticket, project or parent")
    void standaloneTask() {
        JsonNode task = client.create(employeeToken, """
                {"workItemType":"TASK","summary":"Rotate the staging TLS certificate"}
                """);

        assertThat(task.get("referenceKey").asText()).startsWith("WI-");
        assertThat(task.get("projectId").isNull()).isTrue();
        assertThat(task.get("parent").isNull()).isTrue();
        assertThat(task.get("workFamily").asText()).isEqualTo("GENERAL_OPERATIONAL_WORK");
    }

    @Test
    @DisplayName("A story takes the project prefix and the DEVELOPMENT profile")
    void storyCreation() {
        JsonNode story = client.create(leadToken, """
                {"workItemType":"STORY","summary":"Users must be able to upload a profile photo",
                 "projectId":"%s","storyPoints":8,"estimatedHours":20}
                """.formatted(projectId));

        assertThat(story.get("referenceKey").asText()).startsWith("IDP-");
        assertThat(story.get("workflowCode").asText()).isEqualTo("DEVELOPMENT");
        assertThat(story.get("status").asText()).isEqualTo("BACKLOG");
    }

    @Test
    @DisplayName("Development work cannot skip review and testing")
    void reviewAndTestingAreUnskippable() {
        UUID storyId = UUID.fromString(client.create(leadToken, """
                {"workItemType":"STORY","summary":"Add avatar upload endpoint","projectId":"%s",
                 "acceptanceCriteria":"Given a JPEG under 5MB, when uploaded, then it is stored."}
                """.formatted(projectId)).get("id").asText());

        client.transition(leadToken, storyId, "{\"transition\":\"MARK_READY\"}", HttpStatus.OK);
        client.transition(leadToken, storyId, """
                {"transition":"ADD_TO_SPRINT"}""", HttpStatus.UNPROCESSABLE_ENTITY);

        // No IN_PROGRESS -> DONE edge exists in the profile, so there is nothing for even
        // a manager to invoke.
        JsonNode refused = client.transition(leadToken, storyId,
                "{\"transition\":\"PASS_QA\"}", HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.get("code").asText()).isEqualTo("INVALID_TRANSITION");
        assertThat(refused.get("detail").asText()).contains("Available:");
    }

    @Test
    @DisplayName("Blocking requires a reason and the reason comes back on the item")
    void blockingRequiresAReason() {
        UUID ticketId = assignedTicket();
        client.transition(leadToken, ticketId, "{\"transition\":\"START_WORK\"}", HttpStatus.OK);

        JsonNode refused = client.transition(employeeToken, ticketId,
                "{\"transition\":\"BLOCK\",\"reason\":\"wip\"}", HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.get("code").asText()).isEqualTo("TRANSITION_GUARD_FAILED");
        assertThat(refused.get("errors")).anyMatch(e -> e.get("code").asText().equals("REQUIRE_REASON"));

        client.transition(employeeToken, ticketId, """
                {"transition":"BLOCK","reason":"Waiting for the replacement dock to arrive"}
                """, HttpStatus.OK);

        JsonNode blocked = client.get(employeeToken, ticketId);
        assertThat(blocked.get("statusCategory").asText()).isEqualTo("BLOCKED");
        assertThat(blocked.get("blocked").get("reason").asText()).contains("replacement dock");
    }

    @Test
    @DisplayName("An employee cannot hand their work to a colleague")
    void employeesCannotAssignToOthers() {
        UUID ticketId = assignedTicket();
        UUID colleagueId = fixtures.user(fixtures.organization("LIFECYCLE"),
                "colleague-" + UUID.randomUUID() + "@acme.io", Set.of(SystemRole.EMPLOYEE), null);

        JsonNode refused = client.transition(employeeToken, ticketId, """
                {"transition":"START_WORK","assigneeId":"%s"}
                """.formatted(colleagueId), HttpStatus.FORBIDDEN);

        assertThat(refused.get("code").asText()).isEqualTo("FORBIDDEN");
        assertThat(refused.get("detail").asText()).contains("reassignment");
    }

    @Test
    @DisplayName("availableTransitions reflects this actor on this item")
    void availableTransitionsAreActorSpecific() {
        UUID ticketId = assignedTicket();

        JsonNode asAssignee = client.get(employeeToken, ticketId);
        assertThat(transitionCodes(asAssignee)).contains("START_WORK");
        assertThat(asAssignee.get("permissions").get("canAssign").asBoolean()).isFalse();

        JsonNode asLead = client.get(leadToken, ticketId);
        assertThat(asLead.get("permissions").get("canAssign").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Optimistic locking rejects a stale write")
    void staleWritesAreRejected() {
        UUID ticketId = assignedTicket();

        JsonNode refused = client.transition(leadToken, ticketId,
                "{\"transition\":\"START_WORK\",\"version\":99}", HttpStatus.CONFLICT);

        assertThat(refused.get("code").asText()).isEqualTo("CONCURRENT_MODIFICATION");
    }

    // ------------------------------------------------------------- helpers

    private UUID assignedTicket() {
        UUID ticketId = UUID.fromString(client.create(employeeToken, """
                {"workItemType":"REQUEST","summary":"Replace the failing docking station"}
                """).get("id").asText());

        client.transition(leadToken, ticketId, """
                {"transition":"ASSIGN","assigneeId":"%s"}""".formatted(employeeId), HttpStatus.OK);
        return ticketId;
    }

    private java.util.List<String> transitionCodes(JsonNode detail) {
        java.util.List<String> codes = new java.util.ArrayList<>();
        detail.get("availableTransitions").forEach(t -> codes.add(t.get("code").asText()));
        return codes;
    }
}
