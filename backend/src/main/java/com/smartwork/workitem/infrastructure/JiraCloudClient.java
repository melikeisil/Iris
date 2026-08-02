package com.smartwork.workitem.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartwork.workitem.application.JiraIssueGateway;
import com.smartwork.workitem.domain.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@EnableConfigurationProperties(JiraProperties.class)
public class JiraCloudClient implements JiraIssueGateway {

    private static final Logger log = LoggerFactory.getLogger(JiraCloudClient.class);

    private final RestClient restClient;
    private final JiraProperties properties;

    public JiraCloudClient(RestClient.Builder builder, JiraProperties properties) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.normalizedBaseUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(properties.getEmail(), properties.getApiToken()))
                .build();
    }

    @Override
    public Result createIssue(CreateRequest request) {
        if (!properties.isConfigured()) {
            return Result.notConfigured("Jira integration is not configured.");
        }

        try {
            CreateIssueRequest payload = new CreateIssueRequest(new Fields(
                    new ProjectRef(properties.getProjectKey()),
                    request.summary(),
                    AdfDocument.fromText(request.description() + "\n\nIris work item: " + request.referenceKey()),
                    new NamedRef(properties.getIssueType()),
                    safePriority(request.priority())));

            return restClient.post()
                    .uri("/rest/api/3/issue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .exchange((req, res) -> {
                        int status = res.getStatusCode().value();
                        if (res.getStatusCode().is2xxSuccessful()) {
                            JsonNode body = res.bodyTo(JsonNode.class);
                            String issueId = body == null || body.get("id") == null ? null : body.get("id").asText(null);
                            String issueKey = body == null || body.get("key") == null ? null : body.get("key").asText(null);
                            if (issueId == null || issueKey == null) {
                                return Result.apiFailure("Jira returned a malformed success response.");
                            }
                            return Result.success(issueId, issueKey, properties.issueBrowseUrl(issueKey));
                        }
                        JiraErrorResponse error = res.bodyTo(JiraErrorResponse.class);
                        String detail = error == null ? "Jira returned HTTP " + status : error.message();
                        if (status == 401 || status == 403) {
                            return Result.authenticationFailed(detail);
                        }
                        if (status == 400 || status == 404) {
                            return Result.invalidConfiguration(detail);
                        }
                        log.warn("Jira create issue failed with {}: {}", status, detail);
                        return Result.apiFailure(detail);
                    }, false);
        } catch (ResourceAccessException e) {
            log.warn("Jira unreachable at {}: {}", properties.normalizedBaseUrl(), e.getMessage());
            return Result.apiFailure("Jira is unreachable: " + e.getMessage());
        } catch (Exception e) {
            log.warn("Jira issue creation failed: {}", e.getMessage());
            return Result.apiFailure("Jira issue creation failed: " + e.getMessage());
        }
    }

    private static NamedRef safePriority(Priority priority) {
        if (priority == null) {
            return null;
        }
        return switch (priority) {
            case LOW -> new NamedRef("Low");
            case MEDIUM -> new NamedRef("Medium");
            case HIGH -> new NamedRef("High");
            case CRITICAL -> null;
        };
    }

    private record CreateIssueRequest(Fields fields) {
    }

    private record ProjectRef(@JsonProperty("key") String key) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Fields(
            ProjectRef project,
            String summary,
            AdfDocument description,
            @JsonProperty("issuetype") NamedRef issueType,
            NamedRef priority) {
    }

    private record NamedRef(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record JiraErrorResponse(
            @JsonProperty("errorMessages") List<String> errorMessages,
            Map<String, String> errors) {

        String message() {
            List<String> topLevel = errorMessages == null ? List.of() : errorMessages;
            Map<String, String> fieldErrors = errors == null ? Map.of() : errors;
            if (!topLevel.isEmpty()) {
                return String.join(" ", topLevel);
            }
            if (!fieldErrors.isEmpty()) {
                return fieldErrors.entrySet().stream()
                        .map(entry -> entry.getKey() + ": " + entry.getValue())
                        .reduce((left, right) -> left + "; " + right)
                        .orElse("Jira rejected the request.");
            }
            return "Jira rejected the request.";
        }
    }

    private record AdfDocument(String type, int version, List<Map<String, Object>> content) {

        static AdfDocument fromText(String value) {
            return new AdfDocument("doc", 1, List.of(paragraph(value)));
        }

        private static Map<String, Object> paragraph(String text) {
            Map<String, Object> paragraph = new LinkedHashMap<>();
            paragraph.put("type", "paragraph");

            Map<String, Object> textNode = new LinkedHashMap<>();
            textNode.put("type", "text");
            textNode.put("text", text);

            paragraph.put("content", List.of(textNode));
            return paragraph;
        }
    }
}
