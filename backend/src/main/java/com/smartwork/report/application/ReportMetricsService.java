package com.smartwork.report.application;

import com.smartwork.report.infrastructure.ReportMetricsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns {@link ReportMetricsRepository}'s raw SQL rows into the typed {@link ReportMetrics}
 * a report preview and history entry are built from.
 *
 * <p>Nothing here ranks, scores, or compares employees. {@link #activeByEmployee} groups
 * each person's own active work under their name — a count of "how many things has X
 * completed" would already be a mild performance signal, so this service does not compute
 * one, not even as an aside. If a future change wants that, it needs a deliberate,
 * separate decision, not a field that quietly appears here because the data was already
 * in scope.
 */
@Service
public class ReportMetricsService {

    private final ReportMetricsRepository repository;

    public ReportMetricsService(ReportMetricsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ReportMetrics compute(
            UUID organizationId, UUID departmentId, String departmentName,
            LocalDate periodStart, LocalDate periodEnd) {
        Instant periodStartInstant = periodStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant periodEndInstant = periodEnd.atStartOfDay(ZoneOffset.UTC).toInstant();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate slaRiskHorizon = today.plusDays(2);

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (Object[] row : repository.statusCounts(organizationId, departmentId)) {
            statusCounts.put((String) row[0], ((Number) row[1]).longValue());
        }

        List<ReportMetrics.CompletedTask> completed = repository
                .completedThisWeek(organizationId, departmentId, periodStartInstant, periodEndInstant)
                .stream()
                .map(row -> new ReportMetrics.CompletedTask(
                        (String) row[0], (String) row[1], (UUID) row[2], (String) row[3], toInstant(row[4])))
                .toList();

        List<ReportMetrics.EmployeeActiveTasks> activeByEmployee = groupActiveTasks(
                repository.activeTasksByEmployee(organizationId, departmentId));

        List<ReportMetrics.BlockedTask> blocked = repository.blockedTasks(organizationId, departmentId)
                .stream()
                .map(row -> new ReportMetrics.BlockedTask(
                        (String) row[0], (String) row[1], (String) row[2], (String) row[3], toInstant(row[4])))
                .toList();

        List<Object[]> dueRows = repository.overdueAndAtRisk(organizationId, departmentId, today, slaRiskHorizon);
        List<ReportMetrics.DueTask> overdue = dueRows.stream()
                .filter(row -> Boolean.TRUE.equals(row[0]))
                .map(this::toDueTask)
                .toList();
        List<ReportMetrics.DueTask> slaRisk = dueRows.stream()
                .filter(row -> !Boolean.TRUE.equals(row[0]))
                .map(this::toDueTask)
                .toList();

        List<ReportMetrics.DepartmentWorkload> workload = repository
                .departmentWorkload(organizationId, departmentId).stream()
                .map(row -> {
                    BigDecimal capacity = toBigDecimal(row[2]);
                    BigDecimal assigned = toBigDecimal(row[3]);
                    return new ReportMetrics.DepartmentWorkload(
                            (UUID) row[0], (String) row[1], capacity, assigned, capacity.subtract(assigned));
                })
                .toList();

        List<ReportMetrics.ParentTicketProgress> parentProgress = repository
                .parentTicketProgress(organizationId, departmentId).stream()
                .map(row -> new ReportMetrics.ParentTicketProgress(
                        (String) row[0], (String) row[1], toBigDecimal(row[2]),
                        ((Number) row[3]).longValue(), ((Number) row[4]).longValue()))
                .toList();

        List<ReportMetrics.ReassignmentChange> reassignments = repository
                .reassignmentsThisWeek(organizationId, departmentId, periodStartInstant, periodEndInstant)
                .stream()
                .map(row -> new ReportMetrics.ReassignmentChange(
                        (String) row[0], (String) row[1], (String) row[2],
                        (String) row[3], (String) row[4], toInstant(row[5])))
                .toList();

        List<ReportMetrics.ApprovalChange> approvalChanges = repository
                .approvalChangesThisWeek(organizationId, departmentId, periodStartInstant, periodEndInstant)
                .stream()
                .map(row -> new ReportMetrics.ApprovalChange(
                        (String) row[0], (String) row[1], (String) row[2],
                        (String) row[3], (String) row[4], toInstant(row[5])))
                .toList();

        return new ReportMetrics(
                periodStart, periodEnd, departmentId, departmentName,
                statusCounts, completed, activeByEmployee, blocked, overdue, slaRisk,
                workload, parentProgress, reassignments, approvalChanges);
    }

    public List<String> defaultRecipients(UUID organizationId) {
        return repository.defaultRecipientEmails(organizationId);
    }

    private List<ReportMetrics.EmployeeActiveTasks> groupActiveTasks(List<Object[]> rows) {
        Map<UUID, ReportMetrics.EmployeeActiveTasks> byEmployee = new LinkedHashMap<>();
        Map<UUID, List<ReportMetrics.ActiveTask>> tasksByEmployee = new LinkedHashMap<>();
        for (Object[] row : rows) {
            UUID employeeId = (UUID) row[0];
            String employeeName = (String) row[1];
            ReportMetrics.ActiveTask task = new ReportMetrics.ActiveTask(
                    (String) row[2], (String) row[3], (String) row[4], toLocalDate(row[5]));
            tasksByEmployee.computeIfAbsent(employeeId, id -> new java.util.ArrayList<>()).add(task);
            byEmployee.putIfAbsent(employeeId, new ReportMetrics.EmployeeActiveTasks(employeeId, employeeName, null));
        }
        return byEmployee.keySet().stream()
                .map(id -> new ReportMetrics.EmployeeActiveTasks(
                        id, byEmployee.get(id).employeeName(), List.copyOf(tasksByEmployee.get(id))))
                .toList();
    }

    private ReportMetrics.DueTask toDueTask(Object[] row) {
        return new ReportMetrics.DueTask(
                (String) row[1], (String) row[2], (String) row[3], toLocalDate(row[4]), (String) row[5]);
    }

    private static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        throw new IllegalStateException("Unexpected temporal type: " + value.getClass());
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        throw new IllegalStateException("Unexpected date type: " + value.getClass());
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
