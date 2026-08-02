package com.smartwork.workitem.api;

import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.common.security.ResourceRole;
import com.smartwork.workflow.application.WorkflowCatalog;
import com.smartwork.workflow.application.WorkflowEngine;
import com.smartwork.workitem.application.WorkItemAccessEvaluator;
import com.smartwork.workitem.domain.WorkItem;
import com.smartwork.workitem.domain.WorkItemComment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Maps entities to DTOs. The only place a {@code WorkItem} is allowed to become JSON. */
@Component
public class WorkItemMapper {

    private final WorkflowCatalog workflows;
    private final WorkItemAccessEvaluator access;

    public WorkItemMapper(WorkflowCatalog workflows, WorkItemAccessEvaluator access) {
        this.workflows = workflows;
        this.access = access;
    }

    public WorkItemDtos.WorkItemSummaryResponse toSummary(WorkItem item) {
        return new WorkItemDtos.WorkItemSummaryResponse(
                item.getId(),
                item.getReferenceKey(),
                item.getWorkItemType().name(),
                item.getWorkFamily().name(),
                item.getOriginType().name(),
                item.getStatus(),
                categoryOf(item),
                item.getSummary(),
                item.getPriority().name(),
                item.getRiskLevel().name(),
                item.getEstimatedHours(),
                item.getStoryPoints(),
                item.getDueDate(),
                item.getProgressPercent(),
                item.getAssigneeId(),
                item.getTeamId(),
                item.getDepartmentId(),
                item.getProjectId(),
                item.getSprintId(),
                item.getParentId(),
                item.getJiraIssueKey(),
                item.isBlocked(),
                item.getUpdatedAt());
    }

    public WorkItemDtos.WorkItemDetailResponse toDetail(
            WorkItem item, AuthPrincipal principal, Optional<WorkItem> parent,
            WorkItemDtos.ChildrenSummary childrenSummary,
            List<WorkflowEngine.AvailableTransition> transitions) {

        WorkflowCatalog.DefinitionView definition = workflows.requireById(item.getWorkflowDefinitionId());

        return new WorkItemDtos.WorkItemDetailResponse(
                item.getId(),
                item.getReferenceKey(),
                item.getWorkItemType().name(),
                item.getWorkFamily().name(),
                item.getOriginType().name(),
                item.getStatus(),
                categoryOf(item),
                definition.code(),
                item.getSummary(),
                item.getDescription(),
                item.getAcceptanceCriteria(),
                item.getPriority().name(),
                item.getPriorityScore(),
                item.getRiskLevel().name(),
                item.getComplexity() == null ? null : item.getComplexity().name(),
                item.getEstimatedHours(),
                item.getRemainingHours(),
                item.getStoryPoints(),
                item.getDueDate(),
                item.getProgressPercent(),
                item.getAiConfidence(),
                new WorkItemDtos.SensitivityResponse(item.isSecuritySensitive(),
                        item.isProductionImpacting(), item.isBudgetSensitive(),
                        item.isComplianceSensitive()),
                item.getClosureType().name(),
                item.getProjectId(),
                item.getSprintId(),
                parent.map(p -> new WorkItemDtos.ParentRef(p.getId(), p.getReferenceKey(),
                        p.getWorkItemType().name(), p.getSummary())).orElse(null),
                item.getDepartmentId(),
                item.getTeamId(),
                item.getAssigneeId(),
                item.getReporterId(),
                item.getProcessOwnerId(),
                item.getReviewerId(),
                childrenSummary,
                item.isBlocked()
                        ? new WorkItemDtos.BlockedInfo(item.getBlockedSince(), item.getBlockReason())
                        : null,
                item.getJiraIssueKey() == null ? null
                        : new WorkItemDtos.JiraRef(item.getJiraIssueKey(), item.getJiraIssueUrl(),
                                item.getJiraCreatedAt()),
                access.relationshipsOf(principal, item).stream().map(ResourceRole::name).toList(),
                new WorkItemDtos.PermissionsResponse(
                        access.canEdit(principal, item),
                        access.canAssign(principal, item),
                        true,
                        access.canEdit(principal, item),
                        access.canSeeInternalComments(principal, item)),
                transitions.stream()
                        .map(t -> new WorkItemDtos.AvailableTransitionResponse(
                                t.code(), t.label(), t.toState(), t.requiresReason(),
                                t.enabled(), t.blockedReasons()))
                        .toList(),
                toClarificationQuestions(item.getPendingClarificationQuestions()),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                item.getVersion());
    }

    /**
     * Defensive by construction: this reads a JSONB blob {@code analysis} wrote, from a
     * shape it controls, not a client-supplied structure - the same posture {@code
     * lib/aiPayload.ts} takes on the frontend. A malformed or missing field degrades to
     * null/false rather than throwing, so a future change to the AI service's question
     * shape cannot turn "waiting on clarification" into a 500 on every read of the item.
     */
    private List<WorkItemDtos.ClarificationQuestionResponse> toClarificationQuestions(
            List<Map<String, Object>> stored) {
        if (stored == null || stored.isEmpty()) {
            return List.of();
        }
        return stored.stream()
                .map(entry -> new WorkItemDtos.ClarificationQuestionResponse(
                        stringOf(entry.get("id")),
                        stringOf(entry.get("question")),
                        stringOf(entry.get("field")),
                        Boolean.TRUE.equals(entry.get("required"))))
                .toList();
    }

    private String stringOf(Object value) {
        return value instanceof String s ? s : null;
    }

    public WorkItemDtos.CommentResponse toComment(WorkItemComment comment) {
        return new WorkItemDtos.CommentResponse(comment.getId(), comment.getAuthorId(),
                comment.getBody(), comment.isInternal(), comment.getCreatedAt(),
                comment.getUpdatedAt());
    }

    public String categoryOf(WorkItem item) {
        return workflows.requireState(item.getWorkflowDefinitionId(), item.getStatus())
                .category().name();
    }

    public boolean isTerminalSuccess(WorkItem item) {
        return workflows.requireState(item.getWorkflowDefinitionId(), item.getStatus())
                .terminalSuccess();
    }

    public boolean isBlockedState(WorkItem item) {
        return workflows.requireState(item.getWorkflowDefinitionId(), item.getStatus())
                .blocked();
    }
}
