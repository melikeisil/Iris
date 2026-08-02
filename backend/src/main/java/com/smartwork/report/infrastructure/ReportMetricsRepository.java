package com.smartwork.report.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Every number in a weekly report, read straight from the tables the {@code work_item},
 * {@code assignment} and {@code policy} modules own — never through their entities or
 * services (ADR-028; the same choice {@code reporting} makes for its own read models).
 *
 * <p>Every method takes an optional {@code departmentId}: {@code null} means
 * organization-wide, matching {@code AuthPrincipal.organizationWide()} — a Manager scoped
 * to one department never sees another department's numbers, and the SQL enforces that
 * directly rather than trusting a filter applied after the fact in Java.
 */
@Repository
public class ReportMetricsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /** One row per workflow state category actually in use, with a live count. */
    @SuppressWarnings("unchecked")
    public List<Object[]> statusCounts(UUID organizationId, UUID departmentId) {
        Query query = entityManager.createNativeQuery("""
                SELECT ws.category, COUNT(*)
                FROM work_items wi
                JOIN workflow_states ws
                  ON ws.workflow_definition_id = wi.workflow_definition_id AND ws.code = wi.status
                WHERE wi.organization_id = :organizationId
                  AND wi.deleted_at IS NULL
                  AND (CAST(:departmentId AS uuid) IS NULL OR wi.department_id = CAST(:departmentId AS uuid))
                GROUP BY ws.category
                """);
        query.setParameter("organizationId", organizationId);
        query.setParameter("departmentId", departmentId);
        return query.getResultList();
    }

    /**
     * Completed this week: reference key, summary, who completed it (the assignee at
     * completion time — there is no separate transition log to attribute the DONE
     * transition to a different actor), and when.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> completedThisWeek(
            UUID organizationId, UUID departmentId, Instant periodStart, Instant periodEnd) {
        Query query = entityManager.createNativeQuery("""
                SELECT wi.reference_key, wi.summary, wi.assignee_id, u.full_name,
                       COALESCE(wi.resolved_at, wi.closed_at)
                FROM work_items wi
                JOIN workflow_states ws
                  ON ws.workflow_definition_id = wi.workflow_definition_id AND ws.code = wi.status
                LEFT JOIN users u ON u.id = wi.assignee_id
                WHERE wi.organization_id = :organizationId
                  AND wi.deleted_at IS NULL
                  AND ws.is_terminal_success = TRUE
                  AND COALESCE(wi.resolved_at, wi.closed_at) >= :periodStart
                  AND COALESCE(wi.resolved_at, wi.closed_at) < :periodEnd
                  AND (CAST(:departmentId AS uuid) IS NULL OR wi.department_id = CAST(:departmentId AS uuid))
                ORDER BY COALESCE(wi.resolved_at, wi.closed_at) DESC
                """);
        query.setParameter("organizationId", organizationId);
        query.setParameter("departmentId", departmentId);
        query.setParameter("periodStart", periodStart);
        query.setParameter("periodEnd", periodEnd);
        return query.getResultList();
    }

    /** Active (non-terminal, non-blocked) work grouped by the employee holding it. */
    @SuppressWarnings("unchecked")
    public List<Object[]> activeTasksByEmployee(UUID organizationId, UUID departmentId) {
        Query query = entityManager.createNativeQuery("""
                SELECT u.id, u.full_name, wi.reference_key, wi.summary, wi.priority, wi.due_date
                FROM work_items wi
                JOIN workflow_states ws
                  ON ws.workflow_definition_id = wi.workflow_definition_id AND ws.code = wi.status
                JOIN users u ON u.id = wi.assignee_id
                WHERE wi.organization_id = :organizationId
                  AND wi.deleted_at IS NULL
                  AND ws.is_terminal = FALSE
                  AND ws.is_blocked = FALSE
                  AND (CAST(:departmentId AS uuid) IS NULL OR wi.department_id = CAST(:departmentId AS uuid))
                ORDER BY u.full_name, wi.priority DESC, wi.due_date NULLS LAST
                """);
        query.setParameter("organizationId", organizationId);
        query.setParameter("departmentId", departmentId);
        return query.getResultList();
    }

    /** Blocked work with its reason — never omitted; {@code ck_wi_block_reason} guarantees one exists. */
    @SuppressWarnings("unchecked")
    public List<Object[]> blockedTasks(UUID organizationId, UUID departmentId) {
        Query query = entityManager.createNativeQuery("""
                SELECT wi.reference_key, wi.summary, u.full_name, wi.block_reason, wi.blocked_since
                FROM work_items wi
                LEFT JOIN users u ON u.id = wi.assignee_id
                WHERE wi.organization_id = :organizationId
                  AND wi.deleted_at IS NULL
                  AND wi.blocked_since IS NOT NULL
                  AND (CAST(:departmentId AS uuid) IS NULL OR wi.department_id = CAST(:departmentId AS uuid))
                ORDER BY wi.blocked_since ASC
                """);
        query.setParameter("organizationId", organizationId);
        query.setParameter("departmentId", departmentId);
        return query.getResultList();
    }

    /**
     * Overdue (past due_date, still open) and at-risk (due within {@code riskWindow} of
     * {@code now}, still open) work. Two disjoint buckets in one query, told apart by the
     * boolean in the first column.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> overdueAndAtRisk(
            UUID organizationId, UUID departmentId,
            java.time.LocalDate today, java.time.LocalDate riskWindowEnd) {
        Query query = entityManager.createNativeQuery("""
                SELECT (wi.due_date < :today) AS is_overdue,
                       wi.reference_key, wi.summary, u.full_name, wi.due_date, wi.priority
                FROM work_items wi
                JOIN workflow_states ws
                  ON ws.workflow_definition_id = wi.workflow_definition_id AND ws.code = wi.status
                LEFT JOIN users u ON u.id = wi.assignee_id
                WHERE wi.organization_id = :organizationId
                  AND wi.deleted_at IS NULL
                  AND ws.is_terminal = FALSE
                  AND wi.due_date IS NOT NULL
                  AND wi.due_date < :riskDate
                  AND (CAST(:departmentId AS uuid) IS NULL OR wi.department_id = CAST(:departmentId AS uuid))
                ORDER BY wi.due_date ASC
                """);
        query.setParameter("organizationId", organizationId);
        query.setParameter("departmentId", departmentId);
        query.setParameter("today", today);
        query.setParameter("riskDate", riskWindowEnd);
        return query.getResultList();
    }

    /**
     * Department workload: total weekly capacity vs. open assigned hours, per department.
     *
     * <p>Capacity and open-hours are aggregated in two independent subqueries before being
     * joined to {@code departments} — joining the raw {@code users} and {@code work_items}
     * rows directly would fan them out against each other (every user row repeated once
     * per open work item in the department, and vice versa), inflating both sums.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> departmentWorkload(UUID organizationId, UUID departmentId) {
        Query query = entityManager.createNativeQuery("""
                SELECT d.id, d.name,
                       COALESCE(capacity.total_capacity, 0) AS total_capacity,
                       COALESCE(assigned.open_hours, 0) AS assigned_hours
                FROM departments d
                LEFT JOIN (
                    SELECT u.department_id, SUM(u.weekly_capacity_hours) AS total_capacity
                    FROM users u
                    WHERE u.deleted_at IS NULL
                    GROUP BY u.department_id
                ) capacity ON capacity.department_id = d.id
                LEFT JOIN (
                    SELECT wi.department_id, SUM(wi.remaining_hours) AS open_hours
                    FROM work_items wi
                    JOIN workflow_states ws
                      ON ws.workflow_definition_id = wi.workflow_definition_id AND ws.code = wi.status
                    WHERE wi.deleted_at IS NULL AND ws.is_terminal = FALSE
                    GROUP BY wi.department_id
                ) assigned ON assigned.department_id = d.id
                WHERE d.organization_id = :organizationId
                  AND (CAST(:departmentId AS uuid) IS NULL OR d.id = CAST(:departmentId AS uuid))
                ORDER BY d.name
                """);
        query.setParameter("organizationId", organizationId);
        query.setParameter("departmentId", departmentId);
        return query.getResultList();
    }

    /** Parent tickets (epics/requests/incidents with children) and their rollup progress. */
    @SuppressWarnings("unchecked")
    public List<Object[]> parentTicketProgress(UUID organizationId, UUID departmentId) {
        Query query = entityManager.createNativeQuery("""
                SELECT p.reference_key, p.summary, p.progress_percent,
                       COUNT(c.id) AS child_count,
                       COUNT(c.id) FILTER (WHERE cws.is_terminal_success = TRUE) AS child_done_count
                FROM work_items p
                JOIN work_items c ON c.parent_id = p.id AND c.deleted_at IS NULL
                JOIN workflow_states cws
                  ON cws.workflow_definition_id = c.workflow_definition_id AND cws.code = c.status
                WHERE p.organization_id = :organizationId
                  AND p.deleted_at IS NULL
                  AND (CAST(:departmentId AS uuid) IS NULL OR p.department_id = CAST(:departmentId AS uuid))
                GROUP BY p.id, p.reference_key, p.summary, p.progress_percent
                ORDER BY p.progress_percent ASC
                """);
        query.setParameter("organizationId", organizationId);
        query.setParameter("departmentId", departmentId);
        return query.getResultList();
    }

    /**
     * Reassignments this week: an {@code assignments} row created in the period for a work
     * item that already had at least one earlier assignment — i.e. the item changed hands,
     * not merely got its first owner.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> reassignmentsThisWeek(
            UUID organizationId, UUID departmentId, Instant periodStart, Instant periodEnd) {
        Query query = entityManager.createNativeQuery("""
                SELECT wi.reference_key, wi.summary, u.full_name AS new_assignee,
                       a.source, a.reason, a.assigned_at
                FROM assignments a
                JOIN work_items wi ON wi.id = a.work_item_id
                JOIN users u ON u.id = a.assignee_id
                WHERE a.organization_id = :organizationId
                  AND a.assigned_at >= :periodStart
                  AND a.assigned_at < :periodEnd
                  AND (CAST(:departmentId AS uuid) IS NULL OR wi.department_id = CAST(:departmentId AS uuid))
                  AND EXISTS (
                      SELECT 1 FROM assignments prior
                      WHERE prior.work_item_id = a.work_item_id
                        AND prior.assigned_at < a.assigned_at
                  )
                ORDER BY a.assigned_at DESC
                """);
        query.setParameter("organizationId", organizationId);
        query.setParameter("departmentId", departmentId);
        query.setParameter("periodStart", periodStart);
        query.setParameter("periodEnd", periodEnd);
        return query.getResultList();
    }

    /** Approval decisions (approve/reject/escalate) made this week. */
    @SuppressWarnings("unchecked")
    public List<Object[]> approvalChangesThisWeek(
            UUID organizationId, UUID departmentId, Instant periodStart, Instant periodEnd) {
        Query query = entityManager.createNativeQuery("""
                SELECT wi.reference_key, wi.summary, ar.route, ar.status,
                       u.full_name AS decided_by, ar.decided_at
                FROM approval_requests ar
                JOIN work_items wi ON wi.id = ar.work_item_id
                LEFT JOIN users u ON u.id = ar.decided_by
                WHERE ar.organization_id = :organizationId
                  AND ar.decided_at >= :periodStart
                  AND ar.decided_at < :periodEnd
                  AND ar.status IN ('APPROVED', 'REJECTED', 'ESCALATED')
                  AND (CAST(:departmentId AS uuid) IS NULL OR wi.department_id = CAST(:departmentId AS uuid))
                ORDER BY ar.decided_at DESC
                """);
        query.setParameter("organizationId", organizationId);
        query.setParameter("departmentId", departmentId);
        query.setParameter("periodStart", periodStart);
        query.setParameter("periodEnd", periodEnd);
        return query.getResultList();
    }

    /** Default recipients: this organization's Managers and Admins with an active account. */
    @SuppressWarnings("unchecked")
    public List<String> defaultRecipientEmails(UUID organizationId) {
        Query query = entityManager.createNativeQuery("""
                SELECT DISTINCT u.email
                FROM users u
                JOIN user_roles ur ON ur.user_id = u.id
                JOIN roles r ON r.id = ur.role_id
                WHERE u.organization_id = :organizationId
                  AND u.deleted_at IS NULL
                  AND u.active = TRUE
                  AND r.code IN ('MANAGER', 'ADMIN')
                ORDER BY u.email
                """);
        query.setParameter("organizationId", organizationId);
        return query.getResultList();
    }
}
