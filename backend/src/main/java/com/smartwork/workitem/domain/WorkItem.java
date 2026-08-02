package com.smartwork.workitem.domain;

import com.smartwork.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The single persisted unit of work.
 *
 * <p>Every ticket, incident, epic, feature, story, bug, task and subtask is one of these
 * (ADR-001). Not every column applies to every type; that is handled by
 * {@link WorkItemValidator}, not by splitting the table.
 *
 * <p>References to other modules are UUIDs rather than JPA associations (ADR-028), and
 * {@code status} is a string validated against this item's workflow profile - both by
 * {@code WorkflowEngine} and by a composite foreign key into {@code workflow_states}.
 */
@Entity
@Table(name = "work_items")
public class WorkItem extends TenantEntity {

    @Column(name = "reference_key", nullable = false, updatable = false, length = 32)
    private String referenceKey;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "sprint_id")
    private UUID sprintId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_item_type", nullable = false, length = 24)
    private WorkItemType workItemType;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_family", nullable = false, length = 32)
    private WorkFamily workFamily;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_type", nullable = false, updatable = false, length = 24)
    private OriginType originType;

    @Column(name = "workflow_definition_id", nullable = false)
    private UUID workflowDefinitionId;

    @Column(name = "status", nullable = false, length = 64)
    private String status;

    @Column(name = "summary", nullable = false, length = 255)
    private String summary;

    @Column(name = "description")
    private String description;

    @Column(name = "acceptance_criteria")
    private String acceptanceCriteria;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private Priority priority = Priority.MEDIUM;

    @Column(name = "priority_score")
    private Short priorityScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    private RiskLevel riskLevel = RiskLevel.LOW;

    @Enumerated(EnumType.STRING)
    @Column(name = "complexity", length = 16)
    private Complexity complexity;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private UUID reporterId;

    @Column(name = "process_owner_id")
    private UUID processOwnerId;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "estimated_hours", precision = 6, scale = 2)
    private BigDecimal estimatedHours;

    @Column(name = "remaining_hours", precision = 6, scale = 2)
    private BigDecimal remainingHours;

    @Column(name = "story_points")
    private Short storyPoints;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "ai_confidence", precision = 3, scale = 2)
    private BigDecimal aiConfidence;

    @Column(name = "security_sensitive", nullable = false)
    private boolean securitySensitive;

    @Column(name = "production_impacting", nullable = false)
    private boolean productionImpacting;

    @Column(name = "budget_sensitive", nullable = false)
    private boolean budgetSensitive;

    @Column(name = "compliance_sensitive", nullable = false)
    private boolean complianceSensitive;

    @Enumerated(EnumType.STRING)
    @Column(name = "closure_type", nullable = false, length = 32)
    private ClosureType closureType = ClosureType.AUTO_CLOSE;

    @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercent = BigDecimal.ZERO;

    @Column(name = "blocked_since")
    private Instant blockedSince;

    @Column(name = "block_reason")
    private String blockReason;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "jira_issue_id", length = 64)
    private String jiraIssueId;

    @Column(name = "jira_issue_key", length = 64)
    private String jiraIssueKey;

    @Column(name = "jira_issue_url", length = 512)
    private String jiraIssueUrl;

    @Column(name = "jira_created_at")
    private Instant jiraCreatedAt;

    @Column(name = "jira_updated_at")
    private Instant jiraUpdatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * The AI's clarification questions, mirrored here from the analysis module's
     * recommendation while this item is waiting on an answer.
     *
     * <p>{@code analysis} writes and clears this - see {@code AnalysisService.analyze} -
     * so that this module never has to read {@code analysis}'s tables to answer "why is
     * this item waiting on me", which its module boundary does not allow.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pending_clarification_questions")
    private List<Map<String, Object>> pendingClarificationQuestions;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected WorkItem() {
    }

    public WorkItem(UUID organizationId, String referenceKey, WorkItemType workItemType,
                    WorkFamily workFamily, OriginType originType, UUID workflowDefinitionId,
                    String initialStatus, String summary, UUID reporterId) {
        super(organizationId);
        this.referenceKey = referenceKey;
        this.workItemType = workItemType;
        this.workFamily = workFamily;
        this.originType = originType;
        this.workflowDefinitionId = workflowDefinitionId;
        this.status = initialStatus;
        this.summary = summary;
        this.reporterId = reporterId;
        this.closureType = workFamily.defaultClosureType();
    }

    // ------------------------------------------------------------- behaviour

    /**
     * Applies a transition already validated by the workflow engine.
     *
     * <p>The engine decides legality; the aggregate applies the consequences - clearing a
     * block, stamping resolution and closure times.
     */
    public void applyTransition(String targetStatus, boolean targetIsBlocked,
                                boolean targetIsTerminal, boolean targetIsTerminalSuccess,
                                String reason, Instant now) {
        this.status = targetStatus;

        if (targetIsBlocked) {
            this.blockedSince = now;
            this.blockReason = reason;
        } else {
            this.blockedSince = null;
            this.blockReason = null;
        }

        if (targetIsTerminalSuccess && this.resolvedAt == null) {
            this.resolvedAt = now;
        }
        if (targetIsTerminal) {
            this.closedAt = now;
        } else {
            this.closedAt = null;
        }
    }

    public void assignTo(UUID newAssigneeId) {
        this.assigneeId = newAssigneeId;
    }

    public void setReviewer(UUID newReviewerId) {
        this.reviewerId = newReviewerId;
    }

    public void updateContent(String summary, String description, String acceptanceCriteria) {
        this.summary = summary;
        this.description = description;
        this.acceptanceCriteria = acceptanceCriteria;
    }

    public void updatePlanning(Priority priority, LocalDate dueDate, BigDecimal estimatedHours,
                               Short storyPoints, Complexity complexity) {
        this.priority = priority;
        this.dueDate = dueDate;
        this.estimatedHours = estimatedHours;
        this.storyPoints = storyPoints;
        this.complexity = complexity;
        if (this.remainingHours == null) {
            this.remainingHours = estimatedHours;
        }
    }

    public void updateRemainingHours(BigDecimal remainingHours) {
        this.remainingHours = remainingHours;
    }

    /**
     * Reclassifying changes which team owns the work, so it is a deliberate act by a Team
     * Lead rather than a side effect of editing a field.
     */
    public void redirect(UUID departmentId, UUID teamId, WorkFamily workFamily) {
        this.departmentId = departmentId;
        this.teamId = teamId;
        if (workFamily != null) {
            this.workFamily = workFamily;
        }
    }

    public void placeIn(UUID projectId, UUID sprintId, UUID parentId) {
        this.projectId = projectId;
        this.sprintId = sprintId;
        this.parentId = parentId;
    }

    public void setProcessOwner(UUID processOwnerId) {
        this.processOwnerId = processOwnerId;
    }

    public void setClosureType(ClosureType closureType) {
        this.closureType = closureType;
    }

    public void setSensitivity(boolean security, boolean production, boolean budget, boolean compliance) {
        this.securitySensitive = security;
        this.productionImpacting = production;
        this.budgetSensitive = budget;
        this.complianceSensitive = compliance;
    }

    /** Written by the AI analysis pipeline in Phase 4; never used for authorization. */
    public void recordAnalysis(BigDecimal confidence, RiskLevel riskLevel, Short priorityScore) {
        this.aiConfidence = confidence;
        this.riskLevel = riskLevel;
        this.priorityScore = priorityScore;
    }

    public void updateProgress(BigDecimal progressPercent) {
        this.progressPercent = progressPercent;
    }

    public void linkToJira(String issueId, String issueKey, String issueUrl, Instant jiraCreatedAt) {
        this.jiraIssueId = issueId;
        this.jiraIssueKey = issueKey;
        this.jiraIssueUrl = issueUrl;
        this.jiraCreatedAt = jiraCreatedAt;
        this.jiraUpdatedAt = jiraCreatedAt;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isBlocked() {
        return blockedSince != null;
    }

    /** Written when analysis asks a question; read by the detail response. */
    public void recordPendingClarification(List<Map<String, Object>> questions) {
        this.pendingClarificationQuestions = questions;
    }

    /** Cleared once the item leaves WAITING_INFORMATION or a fresh analysis is satisfied. */
    public void clearPendingClarification() {
        this.pendingClarificationQuestions = null;
    }

    public List<Map<String, Object>> getPendingClarificationQuestions() {
        return pendingClarificationQuestions;
    }

    // -------------------------------------------------------------- accessors

    public String getReferenceKey() {
        return referenceKey;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getSprintId() {
        return sprintId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public WorkItemType getWorkItemType() {
        return workItemType;
    }

    public WorkFamily getWorkFamily() {
        return workFamily;
    }

    public OriginType getOriginType() {
        return originType;
    }

    public UUID getWorkflowDefinitionId() {
        return workflowDefinitionId;
    }

    public String getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }

    public String getDescription() {
        return description;
    }

    public String getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    public Priority getPriority() {
        return priority;
    }

    public Short getPriorityScore() {
        return priorityScore;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public Complexity getComplexity() {
        return complexity;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    public UUID getProcessOwnerId() {
        return processOwnerId;
    }

    public UUID getReviewerId() {
        return reviewerId;
    }

    public BigDecimal getEstimatedHours() {
        return estimatedHours;
    }

    public BigDecimal getRemainingHours() {
        return remainingHours;
    }

    public Short getStoryPoints() {
        return storyPoints;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getAiConfidence() {
        return aiConfidence;
    }

    public boolean isSecuritySensitive() {
        return securitySensitive;
    }

    public boolean isProductionImpacting() {
        return productionImpacting;
    }

    public boolean isBudgetSensitive() {
        return budgetSensitive;
    }

    public boolean isComplianceSensitive() {
        return complianceSensitive;
    }

    public ClosureType getClosureType() {
        return closureType;
    }

    public BigDecimal getProgressPercent() {
        return progressPercent;
    }

    public Instant getBlockedSince() {
        return blockedSince;
    }

    public String getBlockReason() {
        return blockReason;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getJiraIssueId() {
        return jiraIssueId;
    }

    public String getJiraIssueKey() {
        return jiraIssueKey;
    }

    public String getJiraIssueUrl() {
        return jiraIssueUrl;
    }

    public Instant getJiraCreatedAt() {
        return jiraCreatedAt;
    }

    public Instant getJiraUpdatedAt() {
        return jiraUpdatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersion() {
        return version;
    }
}
