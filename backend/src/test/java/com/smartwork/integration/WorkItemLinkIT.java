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
@DisplayName("Typed links, cycles and dependency guards")
class WorkItemLinkIT {

    @Autowired private WorkItemTestClient client;
    @Autowired private TestFixtures fixtures;

    private String leadToken;
    private UUID developerId;
    private UUID projectId;

    @BeforeEach
    void seed() {
        UUID organizationId = fixtures.organization("LINKS");
        UUID departmentId = fixtures.department(organizationId, "ENGINEERING").id();

        String leadEmail = "lead-" + UUID.randomUUID() + "@acme.io";
        UUID leadId = fixtures.user(organizationId, leadEmail,
                Set.of(SystemRole.EMPLOYEE, SystemRole.TEAM_LEAD), departmentId);
        developerId = fixtures.user(organizationId, "dev-" + UUID.randomUUID() + "@acme.io",
                Set.of(SystemRole.EMPLOYEE), departmentId);

        UUID teamId = fixtures.team(organizationId, departmentId, leadId);
        fixtures.addToTeam(organizationId, teamId, developerId);
        projectId = fixtures.project(organizationId, departmentId);

        leadToken = client.tokenFor(leadEmail);
    }

    @Test
    void aDirectCycleIsRefused() {
        UUID first = task("Provision the storage bucket");
        UUID second = task("Add the avatar upload endpoint");

        client.link(leadToken, second, """
                {"linkType":"DEPENDS_ON","targetId":"%s"}""".formatted(first), HttpStatus.CREATED);

        JsonNode refused = client.link(leadToken, first, """
                {"linkType":"DEPENDS_ON","targetId":"%s"}""".formatted(second), HttpStatus.BAD_REQUEST);

        assertThat(refused.get("code").asText()).isEqualTo("LINK_CYCLE_DETECTED");
    }

    @Test
    void anIndirectCycleIsRefused() {
        // A -> B -> C, then C -> A. Without the recursive check this is accepted and
        // every later traversal either loops or silently truncates.
        UUID a = task("Design the migration");
        UUID b = task("Write the migration");
        UUID c = task("Run the migration in staging");

        client.link(leadToken, b, """
                {"linkType":"DEPENDS_ON","targetId":"%s"}""".formatted(a), HttpStatus.CREATED);
        client.link(leadToken, c, """
                {"linkType":"DEPENDS_ON","targetId":"%s"}""".formatted(b), HttpStatus.CREATED);

        JsonNode refused = client.link(leadToken, a, """
                {"linkType":"DEPENDS_ON","targetId":"%s"}""".formatted(c), HttpStatus.BAD_REQUEST);

        assertThat(refused.get("code").asText()).isEqualTo("LINK_CYCLE_DETECTED");
    }

    @Test
    void anItemCannotLinkToItself() {
        UUID task = task("Rotate the signing key");

        client.link(leadToken, task, """
                {"linkType":"RELATES_TO","targetId":"%s"}""".formatted(task), HttpStatus.BAD_REQUEST);
    }

    @Test
    void theSameLinkCannotBeCreatedTwice() {
        UUID first = task("Update the schema");
        UUID second = task("Update the client");

        client.link(leadToken, second, """
                {"linkType":"DEPENDS_ON","targetId":"%s"}""".formatted(first), HttpStatus.CREATED);
        client.link(leadToken, second, """
                {"linkType":"DEPENDS_ON","targetId":"%s"}""".formatted(first), HttpStatus.CONFLICT);
    }

    @Test
    void inverseRequestsCollapseOntoTheSameStoredRow() {
        // A BLOCKS B and B DEPENDS_ON A are the same fact. Storing both would let the two
        // halves drift apart.
        UUID blocker = task("Provision the bucket");
        UUID blocked = task("Upload avatars to the bucket");

        client.link(leadToken, blocker, """
                {"linkType":"BLOCKS","targetId":"%s"}""".formatted(blocked), HttpStatus.CREATED);
        client.link(leadToken, blocked, """
                {"linkType":"DEPENDS_ON","targetId":"%s"}""".formatted(blocker), HttpStatus.CONFLICT);
    }

    @Test
    void workCannotBecomeReadyWhileADependencyIsOpenAndTheRefusalNamesIt() {
        UUID blocker = task("Provision the storage bucket");
        UUID dependent = task("Add the avatar upload endpoint");

        client.link(leadToken, dependent, """
                {"linkType":"DEPENDS_ON","targetId":"%s"}""".formatted(blocker), HttpStatus.CREATED);

        JsonNode refused = client.transition(leadToken, dependent,
                "{\"transition\":\"MARK_READY\"}", HttpStatus.UNPROCESSABLE_ENTITY);

        assertThat(refused.get("code").asText()).isEqualTo("TRANSITION_GUARD_FAILED");
        assertThat(refused.get("errors"))
                .anyMatch(e -> e.get("code").asText().equals("NO_OPEN_DEPENDENCIES"));
        // Naming the blocking item is the difference between an actionable refusal and a
        // user hunting through the board for whatever is in the way.
        assertThat(refused.get("detail").asText()).contains(referenceKeyOf(blocker));
    }

    @Test
    void theDependencyClearsOnceTheBlockerReachesATerminalSuccessState() {
        // Deliberately mixed profiles: the blocker is operational work on the SERVICE
        // flow, the dependent is development work. The guard asks each item's own
        // workflow whether it is finished, so it never needs to know either profile.
        UUID blocker = operationalTask("Provision the storage bucket");
        UUID dependent = developmentTask("Add the avatar upload endpoint");

        client.link(leadToken, dependent, """
                {"linkType":"DEPENDS_ON","targetId":"%s"}""".formatted(blocker), HttpStatus.CREATED);

        client.transition(leadToken, dependent,
                "{\"transition\":\"MARK_READY\"}", HttpStatus.UNPROCESSABLE_ENTITY);

        client.transition(leadToken, blocker, """
                {"transition":"ASSIGN","assigneeId":"%s"}""".formatted(developerId), HttpStatus.OK);
        client.transition(leadToken, blocker, "{\"transition\":\"START_WORK\"}", HttpStatus.OK);
        client.transition(leadToken, blocker, "{\"transition\":\"RESOLVE\"}", HttpStatus.OK);

        client.transition(leadToken, dependent, "{\"transition\":\"MARK_READY\"}", HttpStatus.OK);
    }

    private String referenceKeyOf(UUID workItemId) {
        return client.get(leadToken, workItemId).get("referenceKey").asText();
    }

    @Test
    void aParentLinkKeepsTheHierarchyColumnInStep() {
        UUID story = id(client.create(leadToken, """
                {"workItemType":"STORY","summary":"Profile photo upload","projectId":"%s"}
                """.formatted(projectId)));
        UUID child = task("Add the upload endpoint");

        client.link(leadToken, story, """
                {"linkType":"PARENT_OF","targetId":"%s"}""".formatted(child), HttpStatus.CREATED);

        // work_items.parent_id is denormalized alongside the link row; both must agree.
        assertThat(client.get(leadToken, child).get("parent").get("id").asText())
                .isEqualTo(story.toString());
    }

    @Test
    void aSubtaskCannotBeGivenChildren() {
        UUID story = id(client.create(leadToken, """
                {"workItemType":"STORY","summary":"Profile photo upload","projectId":"%s"}
                """.formatted(projectId)));
        UUID subtask = id(client.create(leadToken, """
                {"workItemType":"SUBTASK","summary":"Add the MIME type check","parentId":"%s"}
                """.formatted(story)));
        UUID other = task("Something else entirely");

        JsonNode refused = client.link(leadToken, subtask, """
                {"linkType":"PARENT_OF","targetId":"%s"}""".formatted(other), HttpStatus.BAD_REQUEST);

        assertThat(refused.get("code").asText()).isEqualTo("INVALID_LINK_TYPE");
    }

    /**
     * A task on the DEVELOPMENT profile. Origin decides the family: PROJECT_PLANNING makes
     * this development work, whereas a bare USER_CREATED task with no parent would be
     * general operational work on the SERVICE flow.
     */
    private UUID developmentTask(String summary) {
        return id(client.create(leadToken, """
                {"workItemType":"TASK","summary":"%s","projectId":"%s","estimatedHours":4,
                 "originType":"PROJECT_PLANNING"}
                """.formatted(summary, projectId)));
    }

    /** A standalone task, which lands on the SERVICE flow. */
    private UUID operationalTask(String summary) {
        return id(client.create(leadToken, """
                {"workItemType":"TASK","summary":"%s","estimatedHours":4}
                """.formatted(summary)));
    }

    private UUID task(String summary) {
        return developmentTask(summary);
    }

    private static UUID id(JsonNode created) {
        return UUID.fromString(created.get("id").asText());
    }
}
