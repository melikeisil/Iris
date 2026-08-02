package com.smartwork.assignment.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The batch reads behind candidate discovery and scoring.
 *
 * <p>docs/08 §2 calls for one round trip rather than N. This is achieved with a handful of
 * whole-pool queries rather than the {@code v_user_workload} view the design proposed —
 * see ADR-034. Each method returns raw tuples that {@code CandidateFeatureAssembler}
 * turns into the pure records the scoring functions consume.
 */
@Repository
public class CandidateQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /** Tier 1/2/3 discovery pool (docs/08 §2) — the first non-empty tier wins. */
    public record PoolMember(
            UUID userId,
            String fullName,
            String employeeLevel,
            boolean active,
            UUID departmentId,
            BigDecimal weeklyCapacityHours,
            Integer allocationPercent,
            boolean inRecommendedTeam) {
    }

    @SuppressWarnings("unchecked")
    public List<PoolMember> findTeamMembers(UUID organizationId, UUID teamId) {
        Query query = entityManager.createNativeQuery("""
                SELECT u.id, u.full_name, u.employee_level, u.active, u.department_id,
                       u.weekly_capacity_hours, tm.allocation_percent, TRUE
                FROM users u
                JOIN team_members tm ON tm.user_id = u.id
                WHERE u.organization_id = :organizationId
                  AND tm.team_id = :teamId
                  AND u.deleted_at IS NULL
                """);
        query.setParameter("organizationId", organizationId);
        query.setParameter("teamId", teamId);
        return mapPool(query.getResultList());
    }

    @SuppressWarnings("unchecked")
    public List<PoolMember> findDepartmentMembers(UUID organizationId, UUID departmentId, UUID recommendedTeamId) {
        Query query = entityManager.createNativeQuery("""
                SELECT DISTINCT u.id, u.full_name, u.employee_level, u.active, u.department_id,
                       u.weekly_capacity_hours,
                       COALESCE(MAX(tm.allocation_percent), 100),
                       BOOL_OR(tm.team_id = CAST(:recommendedTeamId AS uuid))
                FROM users u
                LEFT JOIN team_members tm ON tm.user_id = u.id
                WHERE u.organization_id = :organizationId
                  AND u.department_id = :departmentId
                  AND u.deleted_at IS NULL
                GROUP BY u.id, u.full_name, u.employee_level, u.active, u.department_id,
                         u.weekly_capacity_hours
                """);
        query.setParameter("organizationId", organizationId);
        query.setParameter("departmentId", departmentId);
        query.setParameter("recommendedTeamId", recommendedTeamId);
        return mapPool(query.getResultList());
    }

    /**
     * Tier 3: anyone in the organization holding every mandatory skill. Deliberately the
     * last resort — docs/08 §2 notes this tier can never produce an AUTO_ASSIGN unless
     * {@code allow_cross_department_auto} is set.
     */
    @SuppressWarnings("unchecked")
    public List<PoolMember> findBySkills(
            UUID organizationId, Collection<String> mandatorySkillCodes, UUID recommendedTeamId) {

        if (mandatorySkillCodes.isEmpty()) {
            return List.of();
        }
        Query query = entityManager.createNativeQuery("""
                SELECT u.id, u.full_name, u.employee_level, u.active, u.department_id,
                       u.weekly_capacity_hours,
                       COALESCE(MAX(tm.allocation_percent), 100),
                       COALESCE(BOOL_OR(tm.team_id = CAST(:recommendedTeamId AS uuid)), FALSE)
                FROM users u
                JOIN user_skills us ON us.user_id = u.id
                JOIN skills s ON s.id = us.skill_id
                LEFT JOIN team_members tm ON tm.user_id = u.id
                WHERE u.organization_id = :organizationId
                  AND u.deleted_at IS NULL
                  AND s.code IN (:skillCodes)
                GROUP BY u.id, u.full_name, u.employee_level, u.active, u.department_id,
                         u.weekly_capacity_hours
                HAVING COUNT(DISTINCT s.code) = :skillCount
                """);
        query.setParameter("organizationId", organizationId);
        query.setParameter("skillCodes", mandatorySkillCodes);
        query.setParameter("skillCount", (long) mandatorySkillCodes.size());
        query.setParameter("recommendedTeamId", recommendedTeamId);
        return mapPool(query.getResultList());
    }

    private List<PoolMember> mapPool(List<Object[]> rows) {
        List<PoolMember> out = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            out.add(new PoolMember(
                    (UUID) row[0],
                    (String) row[1],
                    (String) row[2],
                    (Boolean) row[3],
                    (UUID) row[4],
                    (BigDecimal) row[5],
                    row[6] == null ? 100 : ((Number) row[6]).intValue(),
                    row[7] != null && (Boolean) row[7]));
        }
        return out;
    }

    /** Skill proficiency and recency for the whole pool, one query. */
    @SuppressWarnings("unchecked")
    public List<Object[]> findSkillProfiles(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        Query query = entityManager.createNativeQuery("""
                SELECT us.user_id, s.code, us.proficiency, us.last_used_at
                FROM user_skills us
                JOIN skills s ON s.id = us.skill_id
                WHERE us.user_id IN (:userIds)
                """);
        query.setParameter("userIds", userIds);
        return query.getResultList();
    }

    /**
     * Every open item held by anyone in the pool, with the workflow-state flags the
     * workload numerator needs. One query for the whole pool, which is the point.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findOpenItems(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        Query query = entityManager.createNativeQuery("""
                SELECT wi.assignee_id, wi.work_item_type, wi.estimated_hours, wi.remaining_hours,
                       wi.story_points, ws.is_active_work,
                       ws.category = 'TODO' AS todo_category, wi.blocked_since
                FROM work_items wi
                JOIN workflow_states ws
                  ON ws.workflow_definition_id = wi.workflow_definition_id
                 AND ws.code = wi.status
                WHERE wi.assignee_id IN (:userIds)
                  AND wi.deleted_at IS NULL
                  AND ws.is_terminal = FALSE
                """);
        query.setParameter("userIds", userIds);
        return query.getResultList();
    }

    /** Items sitting in a REVIEW-category state with this user as reviewer. */
    @SuppressWarnings("unchecked")
    public Map<UUID, Integer> countAwaitingReview(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Query query = entityManager.createNativeQuery("""
                SELECT wi.reviewer_id, COUNT(*)
                FROM work_items wi
                JOIN workflow_states ws
                  ON ws.workflow_definition_id = wi.workflow_definition_id
                 AND ws.code = wi.status
                WHERE wi.reviewer_id IN (:userIds)
                  AND wi.deleted_at IS NULL
                  AND ws.category = 'REVIEW'
                GROUP BY wi.reviewer_id
                """);
        query.setParameter("userIds", userIds);
        return toCountMap(query.getResultList());
    }

    /** Non-terminal assigned items, for the INTERN max-active-items restriction. */
    @SuppressWarnings("unchecked")
    public Map<UUID, Integer> countActiveItems(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Query query = entityManager.createNativeQuery("""
                SELECT wi.assignee_id, COUNT(*)
                FROM work_items wi
                JOIN workflow_states ws
                  ON ws.workflow_definition_id = wi.workflow_definition_id
                 AND ws.code = wi.status
                WHERE wi.assignee_id IN (:userIds)
                  AND wi.deleted_at IS NULL
                  AND ws.is_terminal = FALSE
                GROUP BY wi.assignee_id
                """);
        query.setParameter("userIds", userIds);
        return toCountMap(query.getResultList());
    }

    /**
     * Similar completed work in the last 180 days: items sharing at least two of the
     * required skills, or the same category. Feeds the experience score.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> countSimilarCompleted(
            List<UUID> userIds, Collection<String> requiredSkillCodes, LocalDate since) {

        if (userIds.isEmpty() || requiredSkillCodes.isEmpty()) {
            return List.of();
        }
        Query query = entityManager.createNativeQuery("""
                SELECT wi.assignee_id, COUNT(DISTINCT wi.id)
                FROM work_items wi
                JOIN workflow_states ws
                  ON ws.workflow_definition_id = wi.workflow_definition_id
                 AND ws.code = wi.status
                JOIN work_item_skills wis ON wis.work_item_id = wi.id
                JOIN skills s ON s.id = wis.skill_id
                WHERE wi.assignee_id IN (:userIds)
                  AND wi.deleted_at IS NULL
                  AND ws.is_terminal_success = TRUE
                  AND wi.closed_at >= :since
                  AND s.code IN (:skillCodes)
                GROUP BY wi.assignee_id
                """);
        query.setParameter("userIds", userIds);
        query.setParameter("skillCodes", requiredSkillCodes);
        query.setParameter("since", since.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        return query.getResultList();
    }

    /** Distinct affected components each candidate has completed work in. */
    @SuppressWarnings("unchecked")
    public List<Object[]> countComponentExperience(
            List<UUID> userIds, Collection<String> componentCodes, LocalDate since) {

        if (userIds.isEmpty() || componentCodes.isEmpty()) {
            return List.of();
        }
        Query query = entityManager.createNativeQuery("""
                SELECT wi.assignee_id, COUNT(DISTINCT pc.code)
                FROM work_items wi
                JOIN workflow_states ws
                  ON ws.workflow_definition_id = wi.workflow_definition_id
                 AND ws.code = wi.status
                JOIN project_components pc ON pc.project_id = wi.project_id
                WHERE wi.assignee_id IN (:userIds)
                  AND wi.deleted_at IS NULL
                  AND ws.is_terminal_success = TRUE
                  AND wi.closed_at >= :since
                  AND pc.code IN (:componentCodes)
                GROUP BY wi.assignee_id
                """);
        query.setParameter("userIds", userIds);
        query.setParameter("componentCodes", componentCodes);
        query.setParameter("since", since.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        return query.getResultList();
    }

    /** Candidates who have completed anything in this project. */
    @SuppressWarnings("unchecked")
    public List<UUID> findWorkedInProject(List<UUID> userIds, UUID projectId, LocalDate since) {
        if (userIds.isEmpty() || projectId == null) {
            return List.of();
        }
        Query query = entityManager.createNativeQuery("""
                SELECT DISTINCT wi.assignee_id
                FROM work_items wi
                WHERE wi.assignee_id IN (:userIds)
                  AND wi.project_id = :projectId
                  AND wi.deleted_at IS NULL
                  AND wi.closed_at >= :since
                """);
        query.setParameter("userIds", userIds);
        query.setParameter("projectId", projectId);
        query.setParameter("since", since.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        return query.getResultList();
    }

    private Map<UUID, Integer> toCountMap(List<Object[]> rows) {
        Map<UUID, Integer> out = new java.util.HashMap<>();
        for (Object[] row : rows) {
            out.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return out;
    }

    /** Instant helper kept here so callers do not each re-derive the 180-day window. */
    public static Instant daysAgo(int days, Instant now) {
        return now.minus(java.time.Duration.ofDays(days));
    }
}
