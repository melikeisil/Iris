package com.smartwork.workitem.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartwork.jira")
public class JiraProperties {

    private String baseUrl = "";
    private String email = "";
    private String apiToken = "";
    private String projectKey = "";
    private String issueType = "Task";

    public boolean isConfigured() {
        return hasText(baseUrl)
                && hasText(email)
                && hasText(apiToken)
                && hasText(projectKey);
    }

    public String issueBrowseUrl(String issueKey) {
        return normalizedBaseUrl() + "/browse/" + issueKey;
    }

    public String normalizedBaseUrl() {
        return baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }
}
