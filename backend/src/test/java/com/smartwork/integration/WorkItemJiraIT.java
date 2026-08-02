package com.smartwork.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartwork.identity.domain.SystemRole;
import com.smartwork.support.IntegrationTest;
import com.smartwork.support.PostgresContainerConfig;
import com.smartwork.support.TestFixtures;
import com.smartwork.support.WorkItemTestClient;
import com.smartwork.workitem.application.JiraIssueGateway;
import com.smartwork.workitem.domain.Priority;
import com.smartwork.workitem.infrastructure.WorkItemRepository;
import com.smartwork.workitem.infrastructure.JiraProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Import({PostgresContainerConfig.class, WorkItemTestClient.class, WorkItemJiraIT.JiraTestConfig.class})
@TestPropertySource(properties = {
        "smartwork.jira.base-url=https://example.atlassian.net",
        "smartwork.jira.email=jira-test@example.com",
        "smartwork.jira.api-token=test-token",
        "smartwork.jira.project-key=IRIS",
        "smartwork.jira.issue-type=Task"
})
@DisplayName("Work item Jira integration")
class WorkItemJiraIT {

    @Autowired private WorkItemTestClient client;
    @Autowired private TestFixtures fixtures;
    @Autowired private FakeJiraGateway jira;
    @Autowired private JiraProperties jiraProperties;
    @Autowired private WorkItemRepository workItems;

    private String employeeToken;
    private String leadToken;
    private String managerToken;
    private UUID employeeId;

    @BeforeEach
    void seed() {
        UUID organizationId = fixtures.organization("JIRAIT");
        UUID departmentId = fixtures.department(organizationId, "ENGINEERING").id();

        String employeeEmail = "emp-" + UUID.randomUUID() + "@acme.io";
        String leadEmail = "lead-" + UUID.randomUUID() + "@acme.io";
        String managerEmail = "mgr-" + UUID.randomUUID() + "@acme.io";

        employeeId = fixtures.user(organizationId, employeeEmail, Set.of(SystemRole.EMPLOYEE), departmentId);
        UUID leadId = fixtures.user(organizationId, leadEmail,
                Set.of(SystemRole.EMPLOYEE, SystemRole.TEAM_LEAD), departmentId);
        fixtures.user(organizationId, managerEmail,
                Set.of(SystemRole.EMPLOYEE, SystemRole.MANAGER), departmentId);

        UUID teamId = fixtures.team(organizationId, departmentId, leadId);
        fixtures.addToTeam(organizationId, teamId, employeeId);

        employeeToken = client.tokenFor(employeeEmail);
        leadToken = client.tokenFor(leadEmail);
        managerToken = client.tokenFor(managerEmail);

        jira.reset();
        jiraProperties.setBaseUrl("https://example.atlassian.net");
        jiraProperties.setEmail("jira-test@example.com");
        jiraProperties.setApiToken("test-token");
        jiraProperties.setProjectKey("IRIS");
        jiraProperties.setIssueType("Task");
    }

    @Test
    void teamLeadCreatesAJiraIssueAndTheLinkPersists() {
        UUID workItemId = ticket();
        jira.success("10001", "IRIS-123", "https://example.atlassian.net/browse/IRIS-123");

        JsonNode link = client.raw(leadToken, "POST",
                "/api/v1/work-items/" + workItemId + "/jira-create", null, HttpStatus.OK);

        assertThat(link.get("issueKey").asText()).isEqualTo("IRIS-123");
        assertThat(link.get("issueUrl").asText()).isEqualTo("https://example.atlassian.net/browse/IRIS-123");
        assertThat(link.get("createdAt").asText()).isNotBlank();
        assertThat(jira.calls()).isEqualTo(1);
        assertThat(jira.lastPriority()).isEqualTo(Priority.HIGH);

        JsonNode detail = client.get(leadToken, workItemId);
        assertThat(detail.get("jira").get("issueKey").asText()).isEqualTo("IRIS-123");
        assertThat(detail.get("jira").get("issueUrl").asText()).isEqualTo("https://example.atlassian.net/browse/IRIS-123");
        assertThat(detail.get("jira").get("createdAt").asText()).isNotBlank();
    }

    @Test
    void repeatedCallsAreIdempotent() {
        UUID workItemId = ticket();
        jira.success("10001", "IRIS-123", "https://example.atlassian.net/browse/IRIS-123");

        JsonNode first = client.raw(leadToken, "POST",
                "/api/v1/work-items/" + workItemId + "/jira-create", null, HttpStatus.OK);
        JsonNode second = client.raw(leadToken, "POST",
                "/api/v1/work-items/" + workItemId + "/jira-create", null, HttpStatus.OK);

        assertThat(second.get("issueKey").asText()).isEqualTo(first.get("issueKey").asText());
        assertThat(second.get("issueUrl").asText()).isEqualTo(first.get("issueUrl").asText());
        assertThat(jira.calls()).isEqualTo(1);
    }

    @Test
    void existingKeyWithoutUrlIsRecoveredWithoutCreatingASecondIssue() {
        UUID workItemId = ticket();
        var item = workItems.findById(workItemId).orElseThrow();
        item.linkToJira("10001", "IRIS-123", null, null);

        JsonNode link = client.raw(leadToken, "POST",
                "/api/v1/work-items/" + workItemId + "/jira-create", null, HttpStatus.OK);

        assertThat(link.get("issueKey").asText()).isEqualTo("IRIS-123");
        assertThat(link.get("issueUrl").asText()).isEqualTo("https://example.atlassian.net/browse/IRIS-123");
        assertThat(link.get("createdAt").asText()).isNotBlank();
        assertThat(jira.calls()).isZero();
    }

    @Test
    void jiraStatusExposesDemoReadinessWithoutSecrets() {
        JsonNode status = client.raw(employeeToken, "GET", "/api/v1/jira/status", null, HttpStatus.OK);

        assertThat(status.get("configured").asBoolean()).isTrue();
        assertThat(status.get("baseUrl").asText()).isEqualTo("https://example.atlassian.net");
        assertThat(status.get("projectKey").asText()).isEqualTo("IRIS");
        assertThat(status.get("issueType").asText()).isEqualTo("Task");
        assertThat(status.toString()).doesNotContain("test-token");
    }

    @Test
    void missingConfigurationReturnsAClearError() {
        UUID workItemId = ticket();
        jiraProperties.setBaseUrl("");

        JsonNode error = client.raw(leadToken, "POST",
                "/api/v1/work-items/" + workItemId + "/jira-create", null,
                HttpStatus.SERVICE_UNAVAILABLE);

        assertThat(error.get("code").asText()).isEqualTo("JIRA_NOT_CONFIGURED");
        assertThat(jira.calls()).isZero();
    }

    @Test
    void jiraAuthenticationFailureIsSurfacedClearly() {
        UUID workItemId = ticket();
        jira.authenticationFailed("Jira credentials were rejected.");

        JsonNode error = client.raw(leadToken, "POST",
                "/api/v1/work-items/" + workItemId + "/jira-create", null,
                HttpStatus.BAD_GATEWAY);

        assertThat(error.get("code").asText()).isEqualTo("JIRA_AUTHENTICATION_FAILED");
    }

    @Test
    void invalidProjectConfigurationIsSurfacedClearly() {
        UUID workItemId = ticket();
        jira.invalidConfiguration("project: Specify a valid project ID or key");

        JsonNode error = client.raw(managerToken, "POST",
                "/api/v1/work-items/" + workItemId + "/jira-create", null,
                HttpStatus.BAD_GATEWAY);

        assertThat(error.get("code").asText()).isEqualTo("JIRA_INVALID_CONFIGURATION");
    }

    @Test
    void anEmployeeCannotCreateAJiraIssue() {
        UUID workItemId = ticket();

        JsonNode error = client.raw(employeeToken, "POST",
                "/api/v1/work-items/" + workItemId + "/jira-create", null,
                HttpStatus.FORBIDDEN);

        assertThat(error.get("code").asText()).isEqualTo("FORBIDDEN");
        assertThat(jira.calls()).isZero();
    }

    private UUID ticket() {
        return UUID.fromString(client.create(employeeToken, """
                {"workItemType":"REQUEST","summary":"Prepare access for the new starter","priority":"HIGH"}
                """).get("id").asText());
    }

    @TestConfiguration
    static class JiraTestConfig {

        @Bean
        @Primary
        FakeJiraGateway fakeJiraGateway() {
            return new FakeJiraGateway();
        }
    }

    static class FakeJiraGateway implements JiraIssueGateway {

        private final AtomicInteger calls = new AtomicInteger();
        private volatile Result next = Result.success("10001", "IRIS-123",
                "https://example.atlassian.net/browse/IRIS-123");
        private volatile Priority lastPriority;

        @Override
        public Result createIssue(CreateRequest request) {
            calls.incrementAndGet();
            lastPriority = request.priority();
            return next;
        }

        void success(String issueId, String issueKey, String issueUrl) {
            next = Result.success(issueId, issueKey, issueUrl);
        }

        void authenticationFailed(String detail) {
            next = Result.authenticationFailed(detail);
        }

        void invalidConfiguration(String detail) {
            next = Result.invalidConfiguration(detail);
        }

        void reset() {
            calls.set(0);
            next = Result.success("10001", "IRIS-123", "https://example.atlassian.net/browse/IRIS-123");
            lastPriority = null;
        }

        int calls() {
            return calls.get();
        }

        Priority lastPriority() {
            return lastPriority;
        }
    }
}
