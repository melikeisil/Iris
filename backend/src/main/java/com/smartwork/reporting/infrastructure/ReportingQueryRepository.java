package com.smartwork.reporting.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class ReportingQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<Object[]> findTeamMembers(UUID organizationId, UUID teamId) {
        Query query = entityManager.createNativeQuery("""
                SELECT t.id,
                       t.name,
                       u.id,
                       u.full_name,
                       u.employee_level,
                       u.weekly_capacity_hours,
                       tm.allocation_percent,
                       COALESCE(COUNT(wi.id) FILTER (WHERE ws.is_terminal = FALSE), 0),
                       COALESCE(COUNT(wi.id) FILTER (
                           WHERE ws.is_terminal = FALSE AND ws.category = 'BLOCKED'), 0),
                       COALESCE(string_agg(DISTINCT s.code, ',' ORDER BY s.code), '')
                FROM teams t
                JOIN team_members tm ON tm.team_id = t.id
                JOIN users u ON u.id = tm.user_id
                LEFT JOIN work_items wi ON wi.assignee_id = u.id AND wi.deleted_at IS NULL
                LEFT JOIN workflow_states ws
                  ON ws.workflow_definition_id = wi.workflow_definition_id
                 AND ws.code = wi.status
                LEFT JOIN user_skills us ON us.user_id = u.id
                LEFT JOIN skills s ON s.id = us.skill_id
                WHERE t.organization_id = :organizationId
                  AND t.id = :teamId
                  AND t.deleted_at IS NULL
                  AND u.deleted_at IS NULL
                GROUP BY t.id, t.name, u.id, u.full_name, u.employee_level,
                         u.weekly_capacity_hours, tm.allocation_percent
                ORDER BY u.full_name
                """);
        query.setParameter("organizationId", organizationId);
        query.setParameter("teamId", teamId);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> findDepartmentPrimaryMembers(UUID organizationId, UUID departmentId) {
        Query query = entityManager.createNativeQuery("""
                SELECT d.id,
                       d.name,
                       t.id,
                       t.name,
                       u.id,
                       u.full_name,
                       u.weekly_capacity_hours,
                       tm.allocation_percent,
                       COALESCE(COUNT(wi.id) FILTER (
                           WHERE ws.is_terminal = FALSE AND ws.category = 'BLOCKED'), 0)
                FROM departments d
                JOIN teams t ON t.department_id = d.id
                JOIN team_members tm ON tm.team_id = t.id AND tm.primary_team = TRUE
                JOIN users u ON u.id = tm.user_id
                LEFT JOIN work_items wi ON wi.assignee_id = u.id AND wi.deleted_at IS NULL
                LEFT JOIN workflow_states ws
                  ON ws.workflow_definition_id = wi.workflow_definition_id
                 AND ws.code = wi.status
                WHERE d.organization_id = :organizationId
                  AND (:departmentId IS NULL OR d.id = :departmentId)
                  AND t.deleted_at IS NULL
                  AND u.deleted_at IS NULL
                GROUP BY d.id, d.name, t.id, t.name, u.id, u.full_name,
                         u.weekly_capacity_hours, tm.allocation_percent
                ORDER BY d.name, t.name, u.full_name
                """);
        query.setParameter("organizationId", organizationId);
        query.setParameter("departmentId", departmentId);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    public Object[] countPlannedVsUnplannedForTeam(UUID organizationId, UUID teamId) {
        Query query = entityManager.createNativeQuery("""
                SELECT COALESCE(COUNT(*) FILTER (
                           WHERE wi.origin_type IN ('PROJECT_PLANNING', 'PROCESS_TEMPLATE')), 0),
                       COALESCE(COUNT(*) FILTER (
                           WHERE wi.origin_type NOT IN ('PROJECT_PLANNING', 'PROCESS_TEMPLATE')), 0)
                FROM work_items wi
                WHERE wi.organization_id = :organizationId
                  AND wi.deleted_at IS NULL
                  AND wi.assignee_id IN (
                      SELECT tm.user_id FROM team_members tm WHERE tm.team_id = :teamId
                  )
                """);
        query.setParameter("organizationId", organizationId);
        query.setParameter("teamId", teamId);
        return (Object[]) query.getSingleResult();
    }

    @SuppressWarnings("unchecked")
    public Object[] countAssignmentDecisionRates(UUID organizationId, UUID departmentId, Instant since) {
        Query query = entityManager.createNativeQuery("""
                SELECT COALESCE(COUNT(*) FILTER (
                           WHERE ar.status = 'APPROVED' AND ar.route <> 'REQUEST_MORE_INFORMATION'), 0),
                       COALESCE(COUNT(*) FILTER (
                           WHERE ar.status IN ('REJECTED', 'ESCALATED')
                             AND ar.route <> 'REQUEST_MORE_INFORMATION'), 0),
                       COALESCE(COUNT(*) FILTER (
                           WHERE ar.status IN ('APPROVED', 'REJECTED', 'ESCALATED')
                             AND ar.route <> 'REQUEST_MORE_INFORMATION'), 0),
                       COALESCE(COUNT(*) FILTER (
                           WHERE r.routing_decision = 'AUTO_ASSIGN'), 0),
                       COALESCE(COUNT(r.id) FILTER (
                           WHERE r.routing_decision IS NOT NULL), 0)
                FROM approval_requests ar
                LEFT JOIN recommendations r ON r.id = ar.recommendation_id
                LEFT JOIN work_items wi ON wi.id = ar.work_item_id
                WHERE ar.organization_id = :organizationId
                  AND ar.created_at >= :since
                  AND (:departmentId IS NULL OR wi.department_id = :departmentId)
                """);
        query.setParameter("organizationId", organizationId);
        query.setParameter("departmentId", departmentId);
        query.setParameter("since", since);
        return (Object[]) query.getSingleResult();
    }

    @SuppressWarnings("unchecked")
    public Long averageDecisionLatencyMinutes(UUID organizationId, UUID departmentId, Instant since) {
        Query query = entityManager.createNativeQuery("""
                SELECT COALESCE(ROUND(AVG(EXTRACT(EPOCH FROM (ar.decided_at - ar.created_at)) / 60.0)), 0)
                FROM approval_requests ar
                LEFT JOIN work_items wi ON wi.id = ar.work_item_id
                WHERE ar.organization_id = :organizationId
                  AND ar.created_at >= :since
                  AND ar.decided_at IS NOT NULL
                  AND (:departmentId IS NULL OR wi.department_id = :departmentId)
                """);
        query.setParameter("organizationId", organizationId);
        query.setParameter("departmentId", departmentId);
        query.setParameter("since", since);
        Number value = (Number) query.getSingleResult();
        return value == null ? 0L : value.longValue();
    }

    @SuppressWarnings("unchecked")
    public Object[] countPlannedVsUnplannedForDepartment(UUID organizationId, UUID departmentId, Instant since) {
        Query query = entityManager.createNativeQuery("""
                SELECT COALESCE(COUNT(*) FILTER (
                           WHERE wi.origin_type IN ('PROJECT_PLANNING', 'PROCESS_TEMPLATE')), 0),
                       COALESCE(COUNT(*) FILTER (
                           WHERE wi.origin_type NOT IN ('PROJECT_PLANNING', 'PROCESS_TEMPLATE')), 0)
                FROM work_items wi
                WHERE wi.organization_id = :organizationId
                  AND wi.deleted_at IS NULL
                  AND wi.created_at >= :since
                  AND (:departmentId IS NULL OR wi.department_id = :departmentId)
                """);
        query.setParameter("organizationId", organizationId);
        query.setParameter("departmentId", departmentId);
        query.setParameter("since", since);
        return (Object[]) query.getSingleResult();
    }

    public long countPolicyExceptions(UUID organizationId, UUID departmentId, Instant since) {
        Query query = entityManager.createNativeQuery("""
                SELECT COALESCE(COUNT(*), 0)
                FROM approval_requests ar
                LEFT JOIN work_items wi ON wi.id = ar.work_item_id
                WHERE ar.organization_id = :organizationId
                  AND ar.policy_exception = TRUE
                  AND ar.created_at >= :since
                  AND (:departmentId IS NULL OR wi.department_id = :departmentId)
                """);
        query.setParameter("organizationId", organizationId);
        query.setParameter("departmentId", departmentId);
        query.setParameter("since", since);
        Number value = (Number) query.getSingleResult();
        return value == null ? 0L : value.longValue();
    }
}
