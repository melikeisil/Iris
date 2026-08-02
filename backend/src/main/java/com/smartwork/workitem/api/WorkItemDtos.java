package com.smartwork.workitem.api;

import com.smartwork.workitem.domain.Complexity;
import com.smartwork.workitem.domain.LinkType;
import com.smartwork.workitem.domain.OriginType;
import com.smartwork.workitem.domain.Priority;
import com.smartwork.workitem.domain.RiskLevel;
import com.smartwork.workitem.domain.WorkItemType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response bodies for work items. No JPA entity ever crosses this boundary. */
public final class WorkItemDtos {

    private WorkItemDtos() {
    }

    // ------------------------------------------------------------- requests

    @Schema(name = "CreateWorkItemRequest",
            description = "Server-controlled fields are absent by design: status, workFamily, "
                    + "workflowDefinitionId, assigneeId, referenceKey, riskLevel, aiConfidence and "
                    + "the sensitivity flags cannot be set here because they cannot be expressed.")
    public record CreateWorkItemRequest(
            @NotNull WorkItemType workItemType,
            @NotBlank @Size(min = 5, max = 255) String summary,
            @Size(max = 20000) String description,
            @Size(max = 20000) String acceptanceCriteria,
            UUID projectId,
            UUID sprintId,
            UUID parentId,
            UUID departmentId,
            UUID teamId,
            Priority priority,
            Complexity complexity,
            BigDecimal estimatedHours,
            Short storyPoints,
            LocalDate dueDate,
            OriginType originType) {

        public Priority priorityOrDefault() {
            return priority == null ? Priority.MEDIUM : priority;
        }

        public OriginType originOrDefault() {
            return originType == null ? OriginType.USER_CREATED : originType;
        }
    }

    @Schema(name = "UpdateWorkItemRequest")
    public record UpdateWorkItemRequest(
            @NotBlank @Size(min = 5, max = 255) String summary,
            @Size(max = 20000) String description,
            @Size(max = 20000) String acceptanceCriteria,
            @NotNull Priority priority,
            Complexity complexity,
            BigDecimal estimatedHours,
            Short storyPoints,
            LocalDate dueDate,
            SensitivityRequest sensitivity,
            Long version) {
    }

    public record SensitivityRequest(boolean security, boolean production,
                                     boolean budget, boolean compliance) {
    }

    @Schema(name = "TransitionRequest",
            description = "A transition NAME, never a status. The frontend cannot invent a state.")
    public record TransitionRequest(
            @NotBlank String transition,
            @Size(max = 1000) String reason,
            UUID assigneeId,
            UUID reviewerId,
            Long version) {
    }

    @Schema(name = "BlockRequest")
    public record BlockRequest(
            @NotBlank @Size(min = 10, max = 1000) String reason,
            UUID blockedByWorkItemId,
            Long version) {
    }

    @Schema(name = "CreateLinkRequest")
    public record CreateLinkRequest(
            @NotNull LinkType linkType,
            @NotNull UUID targetId,
            @Size(max = 500) String note) {
    }

    @Schema(name = "CommentRequest")
    public record CommentRequest(
            @NotBlank @Size(min = 1, max = 10000) String body,
            boolean internal) {
    }

    @Schema(name = "RemainingHoursRequest")
    public record RemainingHoursRequest(BigDecimal remainingHours) {
    }

    public record JiraLinkResponse(String issueKey, String issueUrl, Instant createdAt) {
    }

    public record JiraStatusResponse(
            boolean configured,
            String baseUrl,
            String projectKey,
            String issueType) {
    }

    // ------------------------------------------------------------ responses

    /** Card-sized. Everything a board or list needs, nothing it does not. */
    @Schema(name = "WorkItemSummary")
    public record WorkItemSummaryResponse(
            UUID id,
            String referenceKey,
            String workItemType,
            String workFamily,
            String originType,
            String status,
            String statusCategory,
            String summary,
            String priority,
            String riskLevel,
            BigDecimal estimatedHours,
            Short storyPoints,
            LocalDate dueDate,
            BigDecimal progressPercent,
            UUID assigneeId,
            UUID teamId,
            UUID departmentId,
            UUID projectId,
            UUID sprintId,
            UUID parentId,
            String jiraIssueKey,
            boolean blocked,
            Instant updatedAt) {
    }

    @Schema(name = "WorkItemDetail")
    public record WorkItemDetailResponse(
            UUID id,
            String referenceKey,
            String workItemType,
            String workFamily,
            String originType,
            String status,
            String statusCategory,
            String workflowCode,
            String summary,
            String description,
            String acceptanceCriteria,
            String priority,
            Short priorityScore,
            String riskLevel,
            String complexity,
            BigDecimal estimatedHours,
            BigDecimal remainingHours,
            Short storyPoints,
            LocalDate dueDate,
            BigDecimal progressPercent,
            BigDecimal aiConfidence,
            SensitivityResponse sensitivity,
            String closureType,
            UUID projectId,
            UUID sprintId,
            ParentRef parent,
            UUID departmentId,
            UUID teamId,
            UUID assigneeId,
            UUID reporterId,
            UUID processOwnerId,
            UUID reviewerId,
            ChildrenSummary childrenSummary,
            BlockedInfo blocked,
            JiraRef jira,
            List<String> relationships,
            PermissionsResponse permissions,
            List<AvailableTransitionResponse> availableTransitions,
            List<ClarificationQuestionResponse> pendingClarificationQuestions,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }

    /**
     * One AI clarification question this item is waiting on. Mirrored from the analysis
     * module's recommendation onto the work item itself - see {@code WorkItem
     * .pendingClarificationQuestions} - so this response never has to reach into analysis's
     * tables.
     */
    public record ClarificationQuestionResponse(String id, String question, String field, boolean required) {
    }

    public record SensitivityResponse(boolean securitySensitive, boolean productionImpacting,
                                      boolean budgetSensitive, boolean complianceSensitive) {
    }

    public record ParentRef(UUID id, String referenceKey, String workItemType, String summary) {
    }

    public record ChildrenSummary(long total, long done, long blocked, String progressStrategy) {
    }

    public record BlockedInfo(Instant since, String reason) {
    }

    public record JiraRef(String issueKey, String issueUrl, Instant createdAt) {
    }

    /**
     * Per-item permissions travel with the item, so the UI renders exactly the controls
     * the backend will honour rather than guessing from the caller's roles.
     */
    public record PermissionsResponse(boolean canEdit, boolean canAssign, boolean canComment,
                                      boolean canLink, boolean canSeeInternalComments) {
    }

    public record AvailableTransitionResponse(String code, String label, String toState,
                                              boolean requiresReason, boolean enabled,
                                              List<String> blockedReasons) {
    }

    public record CommentResponse(UUID id, UUID authorId, String body, boolean internal,
                                  Instant createdAt, Instant updatedAt) {
    }

    public record TransitionResultResponse(UUID id, String referenceKey, String fromState,
                                           String toState, String statusCategory,
                                           String transition, long version) {
    }

    public record ChildrenResponse(List<WorkItemSummaryResponse> children,
                                   BigDecimal progressPercent,
                                   String progressStrategy,
                                   boolean hasBlockedChildren) {
    }

    public record ManualOverrideRequest(
            UUID departmentId,
            UUID teamId,
            Priority priority,
            RiskLevel riskLevel,
            String route
    ) {}
}
