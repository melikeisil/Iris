package com.smartwork.workitem.application;

import com.smartwork.workitem.infrastructure.JiraProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class JiraStatusService {

    private final JiraProperties properties;

    public JiraStatusService(JiraProperties properties) {
        this.properties = properties;
    }

    public JiraStatusView status() {
        return new JiraStatusView(
                properties.isConfigured(),
                properties.normalizedBaseUrl().isBlank() ? null : properties.normalizedBaseUrl(),
                blankToNull(properties.getProjectKey()),
                blankToNull(properties.getIssueType()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record JiraStatusView(
            boolean configured,
            String baseUrl,
            String projectKey,
            String issueType) {
    }
}
