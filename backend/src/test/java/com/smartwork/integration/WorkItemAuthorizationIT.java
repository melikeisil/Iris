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
 * The four-layer authorization model applied to work items.
 *
 * <p>The recurring assertion is 404 rather than 403 for invisible items: a 403 confirms
 * the item exists and leaks the ID space.
 */
@IntegrationTest
@Import({PostgresContainerConfig.class, WorkItemTestClient.class})
@DisplayName("Work item authorization")
class WorkItemAuthorizationIT {

    @Autowired private WorkItemTestClient client;
    @Autowired private TestFixtures fixtures;

    private UUID organizationId;
    private UUID engineeringId;
    private String ownerToken;
    private String strangerToken;
    private String leadToken;
    private String managerToken;
    private UUID ownerId;
    private UUID teamId;

    @BeforeEach
    void seed() {
        organizationId = fixtures.organization("WIAUTH");
        engineeringId = fixtures.department(organizationId, "ENGINEERING").id();
        UUID financeId = fixtures.department(organizationId, "FINANCE").id();

        String ownerEmail = "owner-" + UUID.randomUUID() + "@acme.io";
        String strangerEmail = "stranger-" + UUID.randomUUID() + "@acme.io";
        String leadEmail = "lead-" + UUID.randomUUID() + "@acme.io";
        String managerEmail = "mgr-" + UUID.randomUUID() + "@acme.io";

        ownerId = fixtures.user(organizationId, ownerEmail, Set.of(SystemRole.EMPLOYEE), engineeringId);
        fixtures.user(organizationId, strangerEmail, Set.of(SystemRole.EMPLOYEE), financeId);
        UUID leadId = fixtures.user(organizationId, leadEmail,
                Set.of(SystemRole.EMPLOYEE, SystemRole.TEAM_LEAD), engineeringId);
        fixtures.user(organizationId, managerEmail,
                Set.of(SystemRole.EMPLOYEE, SystemRole.MANAGER), engineeringId);

        teamId = fixtures.team(organizationId, engineeringId, leadId);
        fixtures.addToTeam(organizationId, teamId, ownerId);

        ownerToken = client.tokenFor(ownerEmail);
        strangerToken = client.tokenFor(strangerEmail);
        leadToken = client.tokenFor(leadEmail);
        managerToken = client.tokenFor(managerEmail);
    }

    @Test
    void anUnrelatedEmployeeGetsNotFoundRatherThanForbidden() {
        UUID ticketId = ticket();

        var response = client.getRaw(strangerToken, ticketId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    void searchNeverReturnsInvisibleItemsAndTheTotalAgrees() {
        ticket();
        ticket();

        JsonNode results = client.search(strangerToken, "size=50");

        // Totals must match the visible set. A fetch-then-filter implementation would
        // leak existence through this number even with an empty content array.
        assertThat(results.get("content")).isEmpty();
        assertThat(results.get("page").get("totalElements").asInt()).isZero();
    }

    @Test
    void theRequesterSeesTheirOwnTicket() {
        UUID ticketId = ticket();

        JsonNode detail = client.get(ownerToken, ticketId);

        assertThat(detail.get("relationships")).anyMatch(r -> r.asText().equals("REQUESTER"));
        assertThat(detail.get("permissions").get("canEdit").asBoolean()).isTrue();
        assertThat(detail.get("permissions").get("canAssign").asBoolean()).isFalse();
    }

    @Test
    void aTeamLeadSeesTeamWorkAndMayAssignIt() {
        UUID ticketId = ticket();
        client.transition(leadToken, ticketId, """
                {"transition":"ASSIGN","assigneeId":"%s"}""".formatted(ownerId), HttpStatus.OK);

        JsonNode detail = client.get(leadToken, ticketId);
        assertThat(detail.get("permissions").get("canAssign").asBoolean()).isTrue();
    }

    @Test
    void aManagerSeesTheirDepartment() {
        UUID ticketId = ticket();
        client.transition(leadToken, ticketId, """
                {"transition":"ASSIGN","assigneeId":"%s"}""".formatted(ownerId), HttpStatus.OK);

        assertThat(client.getRaw(managerToken, ticketId).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void onlyAManagerMayClearASensitivityFlag() {
        UUID ticketId = ticket();

        // Anyone with edit rights may raise a flag...
        client.raw(ownerToken, "PUT", "/api/v1/work-items/" + ticketId, """
                {"summary":"Production database credentials appear in the log",
                 "priority":"HIGH",
                 "sensitivity":{"security":true,"production":false,"budget":false,"compliance":false}}
                """, HttpStatus.OK);

        // ...but a flag anyone can remove is not a control.
        JsonNode refused = client.raw(ownerToken, "PUT", "/api/v1/work-items/" + ticketId, """
                {"summary":"Production database credentials appear in the log",
                 "priority":"HIGH",
                 "sensitivity":{"security":false,"production":false,"budget":false,"compliance":false}}
                """, HttpStatus.FORBIDDEN);
        assertThat(refused.get("code").asText()).isEqualTo("FORBIDDEN");
    }

    @Test
    void internalCommentsAreHiddenFromAPureRequester() {
        UUID ticketId = ticket();

        // Assigned to someone else, so the owner is purely the REQUESTER - the case that
        // matters, because a requester is usually outside the delivering department.
        UUID leadId = fixtures.user(organizationId, "assignee-" + UUID.randomUUID() + "@acme.io",
                Set.of(SystemRole.EMPLOYEE), engineeringId);
        fixtures.addToTeam(organizationId, teamId, leadId);
        client.transition(leadToken, ticketId, """
                {"transition":"ASSIGN","assigneeId":"%s"}""".formatted(leadId), HttpStatus.OK);

        client.raw(leadToken, "POST", "/api/v1/work-items/" + ticketId + "/comments", """
                {"body":"Vendor quoted three weeks; do not tell the requester yet.","internal":true}
                """, HttpStatus.CREATED);
        client.raw(leadToken, "POST", "/api/v1/work-items/" + ticketId + "/comments", """
                {"body":"We are looking into this now.","internal":false}
                """, HttpStatus.CREATED);

        JsonNode asRequester = client.raw(ownerToken, "GET",
                "/api/v1/work-items/" + ticketId + "/comments", null, HttpStatus.OK);
        assertThat(asRequester).hasSize(1);
        assertThat(asRequester.get(0).get("internal").asBoolean()).isFalse();

        JsonNode asTeamLead = client.raw(leadToken, "GET",
                "/api/v1/work-items/" + ticketId + "/comments", null, HttpStatus.OK);
        assertThat(asTeamLead).hasSize(2);
    }

    @Test
    void aPureRequesterCannotPostAnInternalComment() {
        UUID ticketId = ticket();
        UUID otherId = fixtures.user(organizationId, "other-" + UUID.randomUUID() + "@acme.io",
                Set.of(SystemRole.EMPLOYEE), engineeringId);
        fixtures.addToTeam(organizationId, teamId, otherId);
        client.transition(leadToken, ticketId, """
                {"transition":"ASSIGN","assigneeId":"%s"}""".formatted(otherId), HttpStatus.OK);

        client.raw(ownerToken, "POST", "/api/v1/work-items/" + ticketId + "/comments", """
                {"body":"Trying to write to the internal thread","internal":true}
                """, HttpStatus.FORBIDDEN);
    }

    @Test
    void anEmployeeCannotEditWorkTheyAreUnrelatedTo() {
        UUID ticketId = ticket();

        client.raw(strangerToken, "PUT", "/api/v1/work-items/" + ticketId, """
                {"summary":"Trying to edit someone else's ticket","priority":"LOW"}
                """, HttpStatus.NOT_FOUND);
    }

    private UUID ticket() {
        return UUID.fromString(client.create(ownerToken, """
                {"workItemType":"REQUEST","summary":"The meeting room projector will not switch on"}
                """).get("id").asText());
    }
}
