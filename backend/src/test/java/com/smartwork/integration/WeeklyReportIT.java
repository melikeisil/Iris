package com.smartwork.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartwork.identity.domain.SystemRole;
import com.smartwork.report.infrastructure.ReportOutboxEventRepository;
import com.smartwork.support.IntegrationTest;
import com.smartwork.support.PostgresContainerConfig;
import com.smartwork.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage for the Manager weekly report flow: generate, preview, send, and
 * history, against a real Postgres (Testcontainers) — real migrations, real JSONB
 * round-trip, real authorization.
 *
 * <p>No SMTP server runs in this test environment, so the outbox relay's actual delivery
 * attempt is expected to fail and retry (see {@code ReportOutboxRelay}); what this test
 * verifies is everything up to and including that attempt being scheduled exactly once per
 * send — the duplicate-click guard, not whether MailHog received the message (that is
 * verified manually against the docker-compose stack, see the runbook note in the PR).
 */
@IntegrationTest
@Import(PostgresContainerConfig.class)
@DisplayName("Manager weekly report flow")
class WeeklyReportIT {

    @Autowired private TestRestTemplate http;
    @Autowired private TestFixtures fixtures;
    @Autowired private ReportOutboxEventRepository outboxEvents;

    private String employeeToken;
    private String managerToken;

    @BeforeEach
    void seedUsers() {
        UUID organizationId = fixtures.organization("WKRPT");
        UUID departmentId = fixtures.department(organizationId, "ENG").id();

        String employeeEmail = "emp-" + UUID.randomUUID() + "@acme.io";
        String managerEmail = "mgr-" + UUID.randomUUID() + "@acme.io";

        fixtures.user(organizationId, employeeEmail, Set.of(SystemRole.EMPLOYEE), departmentId);
        fixtures.user(organizationId, managerEmail, Set.of(SystemRole.EMPLOYEE, SystemRole.MANAGER), departmentId);

        employeeToken = tokenFor(employeeEmail);
        managerToken = tokenFor(managerEmail);
    }

    @Test
    @DisplayName("an employee cannot generate a weekly report")
    void employeeForbidden() {
        ResponseEntity<JsonNode> response = http.exchange(
                "/api/v1/manager/reports/weekly/generate", HttpMethod.POST,
                bearer(employeeToken, "{}"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("generate -> preview -> send -> duplicate send rejected -> history")
    void fullFlow() {
        ResponseEntity<JsonNode> generated = http.exchange(
                "/api/v1/manager/reports/weekly/generate", HttpMethod.POST,
                bearer(managerToken, "{}"), JsonNode.class);
        assertThat(generated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = generated.getBody();
        assertThat(body.get("status").asText()).isEqualTo("DRAFT");
        assertThat(body.get("metrics").get("statusCounts")).isNotNull();
        assertThat(body.get("aiSummary").asText()).isNotBlank();
        String reportId = body.get("id").asText();

        ResponseEntity<JsonNode> preview = http.exchange(
                "/api/v1/manager/reports/weekly/" + reportId + "/preview", HttpMethod.GET,
                bearer(managerToken, null), JsonNode.class);
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(preview.getBody().get("id").asText()).isEqualTo(reportId);

        String sendBody = "{\"recipients\":[\"manager@acme.io\",\"vp@acme.io\"]}";
        ResponseEntity<JsonNode> sent = http.exchange(
                "/api/v1/manager/reports/weekly/" + reportId + "/send", HttpMethod.POST,
                bearer(managerToken, sendBody), JsonNode.class);
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sent.getBody().get("status").asText()).isEqualTo("SENDING");
        assertThat(sent.getBody().get("recipients")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("manager@acme.io", "vp@acme.io");

        // Exactly one outbox event for this report — the unique constraint plus the
        // compare-and-swap in ReportService both exist to guarantee this.
        assertThat(outboxEvents.findAll().stream()
                .filter(e -> e.aggregateId().equals(UUID.fromString(reportId)))
                .count()).isEqualTo(1);

        ResponseEntity<JsonNode> duplicateSend = http.exchange(
                "/api/v1/manager/reports/weekly/" + reportId + "/send", HttpMethod.POST,
                bearer(managerToken, sendBody), JsonNode.class);
        assertThat(duplicateSend.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(outboxEvents.findAll().stream()
                .filter(e -> e.aggregateId().equals(UUID.fromString(reportId)))
                .count()).isEqualTo(1);

        ResponseEntity<JsonNode> history = http.exchange(
                "/api/v1/manager/reports/weekly/history", HttpMethod.GET,
                bearer(managerToken, null), JsonNode.class);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody().get("items")).isNotEmpty();
        assertThat(history.getBody().get("items").get(0).get("id").asText()).isEqualTo(reportId);
    }

    @Test
    @DisplayName("sending with an invalid recipient is rejected before anything is enqueued")
    void invalidRecipientRejected() {
        ResponseEntity<JsonNode> generated = http.exchange(
                "/api/v1/manager/reports/weekly/generate", HttpMethod.POST,
                bearer(managerToken, "{}"), JsonNode.class);
        String reportId = generated.getBody().get("id").asText();

        ResponseEntity<JsonNode> sent = http.exchange(
                "/api/v1/manager/reports/weekly/" + reportId + "/send", HttpMethod.POST,
                bearer(managerToken, "{\"recipients\":[\"not-an-email\"]}"), JsonNode.class);

        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(outboxEvents.findAll().stream()
                .filter(e -> e.aggregateId().equals(UUID.fromString(reportId)))
                .count()).isEqualTo(0);
    }

    private String tokenFor(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + TestFixtures.PASSWORD + "\"}";
        return http.postForEntity("/api/v1/auth/login", new HttpEntity<>(body, headers), JsonNode.class)
                .getBody().get("accessToken").asText();
    }

    private HttpEntity<String> bearer(String token, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
