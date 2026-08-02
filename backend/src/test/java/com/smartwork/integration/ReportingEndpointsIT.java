package com.smartwork.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartwork.identity.domain.SystemRole;
import com.smartwork.support.IntegrationTest;
import com.smartwork.support.PostgresContainerConfig;
import com.smartwork.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Import(PostgresContainerConfig.class)
@DisplayName("Reporting endpoints")
class ReportingEndpointsIT {

    @Autowired private TestRestTemplate http;
    @Autowired private TestFixtures fixtures;

    private String employeeToken;
    private String teamLeadToken;
    private String managerToken;
    private UUID teamId;

    @BeforeEach
    void seedUsers() {
        UUID organizationId = fixtures.organization("REPORTS");
        UUID departmentId = fixtures.department(organizationId, "ENG").id();

        String employeeEmail = "emp-" + UUID.randomUUID() + "@acme.io";
        String leadEmail = "lead-" + UUID.randomUUID() + "@acme.io";
        String managerEmail = "mgr-" + UUID.randomUUID() + "@acme.io";

        UUID leadUserId = fixtures.user(
                organizationId, leadEmail, Set.of(SystemRole.EMPLOYEE, SystemRole.TEAM_LEAD), departmentId);
        UUID employeeUserId = fixtures.user(
                organizationId, employeeEmail, Set.of(SystemRole.EMPLOYEE), departmentId);
        fixtures.user(
                organizationId, managerEmail, Set.of(SystemRole.EMPLOYEE, SystemRole.MANAGER), departmentId);

        teamId = fixtures.team(organizationId, departmentId, leadUserId);
        fixtures.addToTeam(organizationId, teamId, employeeUserId);

        employeeToken = tokenFor(employeeEmail);
        teamLeadToken = tokenFor(leadEmail);
        managerToken = tokenFor(managerEmail);
    }

    @Test
    void teamLeadCanReadTheirTeamWorkload() {
        ResponseEntity<JsonNode> response = http.exchange(
                "/api/v1/team-lead/workload?teamId=" + teamId,
                HttpMethod.GET, bearer(teamLeadToken), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("teamId").asText()).isEqualTo(teamId.toString());
        assertThat(response.getBody().get("members").size()).isGreaterThan(0);
    }

    @Test
    void employeeCannotReadTeamWorkload() {
        ResponseEntity<JsonNode> response = http.exchange(
                "/api/v1/team-lead/workload?teamId=" + teamId,
                HttpMethod.GET, bearer(employeeToken), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void managerCanReadCapacityAndAiAnalytics() {
        ResponseEntity<JsonNode> capacity = http.exchange(
                "/api/v1/manager/capacity",
                HttpMethod.GET, bearer(managerToken), JsonNode.class);
        ResponseEntity<JsonNode> analytics = http.exchange(
                "/api/v1/reports/ai",
                HttpMethod.GET, bearer(managerToken), JsonNode.class);

        assertThat(capacity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(capacity.getBody().get("teams")).isNotNull();
        assertThat(analytics.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(analytics.getBody().get("plannedVsUnplanned")).isNotNull();
    }

    private String tokenFor(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + TestFixtures.PASSWORD + "\"}";
        return http.postForEntity("/api/v1/auth/login", new HttpEntity<>(body, headers), JsonNode.class)
                .getBody().get("accessToken").asText();
    }

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }
}
