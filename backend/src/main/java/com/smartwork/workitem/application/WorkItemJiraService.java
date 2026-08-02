package com.smartwork.workitem.application;

import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.workitem.domain.WorkItem;
import com.smartwork.workitem.infrastructure.JiraProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class WorkItemJiraService {

    private final WorkItemService workItems;
    private final WorkItemAccessEvaluator access;
    private final JiraIssueGateway jira;
    private final JiraProperties properties;
    private final Clock clock;

    public WorkItemJiraService(WorkItemService workItems, WorkItemAccessEvaluator access,
                               JiraIssueGateway jira, JiraProperties properties, Clock clock) {
        this.workItems = workItems;
        this.access = access;
        this.jira = jira;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public JiraLinkView create(AuthPrincipal principal, UUID workItemId) {
        WorkItem item = workItems.requireVisible(principal, workItemId);
        if (!(principal.hasRole("TEAM_LEAD") || principal.isManager())) {
            throw ApiException.forbidden("Only a Team Lead or Manager can create a Jira issue for this work item.");
        }

        JiraLinkView existing = existingLink(item);
        if (existing != null) {
            return existing;
        }

        JiraIssueGateway.Result result = jira.createIssue(new JiraIssueGateway.CreateRequest(
                item.getSummary(),
                item.getDescription() == null || item.getDescription().isBlank()
                        ? "No description was provided in Iris."
                        : item.getDescription(),
                item.getReferenceKey(),
                item.getPriority()));

        return switch (result.status()) {
            case SUCCESS -> persistLink(item, result.issueId(), result.issueKey(), result.issueUrl());
            case NOT_CONFIGURED ->
                    throw new ApiException(ErrorCode.JIRA_NOT_CONFIGURED, result.detail());
            case AUTHENTICATION_FAILED ->
                    throw new ApiException(ErrorCode.JIRA_AUTHENTICATION_FAILED, result.detail());
            case INVALID_CONFIGURATION ->
                    throw new ApiException(ErrorCode.JIRA_INVALID_CONFIGURATION, result.detail());
            case API_FAILURE ->
                    throw new ApiException(ErrorCode.JIRA_UNAVAILABLE, result.detail());
        };
    }

    private JiraLinkView persistLink(WorkItem item, String issueId, String issueKey, String issueUrl) {
        Instant createdAt = Instant.now(clock);
        item.linkToJira(issueId, issueKey, issueUrl, createdAt);
        return JiraLinkView.from(item);
    }

    private JiraLinkView existingLink(WorkItem item) {
        if (item.getJiraIssueKey() == null || item.getJiraIssueKey().isBlank()) {
            return null;
        }
        String issueUrl = item.getJiraIssueUrl();
        if ((issueUrl == null || issueUrl.isBlank()) && properties.isConfigured()) {
            issueUrl = properties.issueBrowseUrl(item.getJiraIssueKey());
        }
        Instant createdAt = item.getJiraCreatedAt() == null ? Instant.now(clock) : item.getJiraCreatedAt();

        if (!createdAt.equals(item.getJiraCreatedAt())
                || !safeEquals(issueUrl, item.getJiraIssueUrl())) {
            item.linkToJira(item.getJiraIssueId(), item.getJiraIssueKey(), issueUrl, createdAt);
        }
        return JiraLinkView.from(item);
    }

    private static boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    public record JiraLinkView(String issueKey, String issueUrl, Instant createdAt) {

        static JiraLinkView from(WorkItem item) {
            return new JiraLinkView(item.getJiraIssueKey(), item.getJiraIssueUrl(), item.getJiraCreatedAt());
        }
    }
}
