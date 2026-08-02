package com.smartwork.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Thin HTTP helper so the lifecycle tests read as scenarios rather than plumbing. */
@TestComponent
public class WorkItemTestClient {

    private final TestRestTemplate http;

    public WorkItemTestClient(TestRestTemplate http) {
        this.http = http;
    }

    public String tokenFor(String email) {
        String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, TestFixtures.PASSWORD);
        ResponseEntity<JsonNode> response = http.postForEntity(
                "/api/v1/auth/login", json(body, null), JsonNode.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("login for %s failed: %s", email, response.getBody())
                .isTrue();
        return response.getBody().get("accessToken").asText();
    }

    public JsonNode create(String token, String body) {
        ResponseEntity<JsonNode> response = http.exchange("/api/v1/work-items",
                HttpMethod.POST, json(body, token), JsonNode.class);
        assertThat(response.getStatusCode().value())
                .as("create failed: %s", response.getBody())
                .isEqualTo(201);
        return response.getBody();
    }

    public JsonNode get(String token, UUID id) {
        ResponseEntity<JsonNode> response = http.exchange("/api/v1/work-items/" + id,
                HttpMethod.GET, bearer(token), JsonNode.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("get failed: %s", response.getBody())
                .isTrue();
        return response.getBody();
    }

    public ResponseEntity<JsonNode> getRaw(String token, UUID id) {
        return http.exchange("/api/v1/work-items/" + id, HttpMethod.GET, bearer(token), JsonNode.class);
    }

    public JsonNode transition(String token, UUID id, String body, HttpStatusCode expected) {
        ResponseEntity<JsonNode> response = http.exchange("/api/v1/work-items/" + id + "/transition",
                HttpMethod.POST, json(body, token), JsonNode.class);
        assertThat(response.getStatusCode())
                .as("transition %s returned %s: %s", body, response.getStatusCode(), response.getBody())
                .isEqualTo(expected);
        return response.getBody();
    }

    public JsonNode link(String token, UUID id, String body, HttpStatusCode expected) {
        ResponseEntity<JsonNode> response = http.exchange("/api/v1/work-items/" + id + "/links",
                HttpMethod.POST, json(body, token), JsonNode.class);
        assertThat(response.getStatusCode())
                .as("link %s returned %s: %s", body, response.getStatusCode(), response.getBody())
                .isEqualTo(expected);
        return response.getBody();
    }

    public JsonNode links(String token, UUID id) {
        return http.exchange("/api/v1/work-items/" + id + "/links",
                HttpMethod.GET, bearer(token), JsonNode.class).getBody();
    }

    public JsonNode search(String token, String queryString) {
        return http.exchange("/api/v1/work-items?" + queryString,
                HttpMethod.GET, bearer(token), JsonNode.class).getBody();
    }

    /** Escape hatch for endpoints without a dedicated helper. */
    public JsonNode raw(String token, String method, String path, String body,
                        HttpStatusCode expected) {
        ResponseEntity<JsonNode> response = http.exchange(path, HttpMethod.valueOf(method),
                body == null ? bearer(token) : json(body, token), JsonNode.class);
        assertThat(response.getStatusCode())
                .as("%s %s returned %s: %s", method, path, response.getStatusCode(), response.getBody())
                .isEqualTo(expected);
        return response.getBody();
    }

    private HttpEntity<String> json(String body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }
}
