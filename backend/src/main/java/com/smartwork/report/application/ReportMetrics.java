package com.smartwork.report.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything a weekly report contains, computed once from persisted data. This is the
 * exact shape stored as {@code weekly_reports.metrics} (via Jackson) and returned by the
 * preview endpoint — one shape, not two that could drift apart.
 *
 * <p>{@link #activeByEmployee} is a grouping, not a ranking: entries are ordered
 * alphabetically by name and carry no comparison, count-vs-count, or score across
 * employees. Nothing in this record computes or stores a performance figure for a person —
 * see docs on {@code ReportMetricsService} for why that is a hard constraint, not an
 * oversight.
 */
public record ReportMetrics(
        LocalDate periodStart,
        LocalDate periodEnd,
        UUID departmentId,
        String departmentName,
        Map<String, Long> statusCounts,
        List<CompletedTask> completedThisWeek,
        List<EmployeeActiveTasks> activeByEmployee,
        List<BlockedTask> blockedTasks,
        List<DueTask> overdueTasks,
        List<DueTask> slaRiskTasks,
        List<DepartmentWorkload> departmentWorkload,
        List<ParentTicketProgress> parentTicketProgress,
        List<ReassignmentChange> reassignments,
        List<ApprovalChange> approvalChanges) {

    public record CompletedTask(
            String referenceKey, String summary, UUID completedBy, String completedByName, Instant completedAt) {
    }

    public record EmployeeActiveTasks(UUID employeeId, String employeeName, List<ActiveTask> tasks) {
    }

    public record ActiveTask(String referenceKey, String summary, String priority, LocalDate dueDate) {
    }

    public record BlockedTask(
            String referenceKey, String summary, String assigneeName, String blockReason, Instant blockedSince) {
    }

    public record DueTask(String referenceKey, String summary, String assigneeName, LocalDate dueDate, String priority) {
    }

    public record DepartmentWorkload(
            UUID departmentId, String departmentName, BigDecimal totalCapacityHours,
            BigDecimal assignedHours, BigDecimal remainingCapacityHours) {
    }

    public record ParentTicketProgress(
            String referenceKey, String summary, BigDecimal progressPercent, long childCount, long childDoneCount) {
    }

    public record ReassignmentChange(
            String referenceKey, String summary, String newAssigneeName, String source, String reason, Instant assignedAt) {
    }

    public record ApprovalChange(
            String referenceKey, String summary, String route, String status, String decidedByName, Instant decidedAt) {
    }
}
