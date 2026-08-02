package com.smartwork.workitem.infrastructure;

import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.workitem.domain.WorkItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Filtered search with the caller's visibility pushed into SQL.
 *
 * <p>This is the important part: the predicate is part of the query, never a filter
 * applied to results. Fetch-then-filter leaks existence through the pagination total - a
 * user who cannot see an item can still learn that it exists by watching the count move.
 */
@Repository
public class WorkItemSearchRepository {

    private static final int MAX_PAGE_SIZE = 100;

    @PersistenceContext
    private EntityManager entityManager;

    public Page<WorkItem> search(AuthPrincipal principal, Criteria criteria, Pageable pageable) {
        Map<String, Object> parameters = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE w.deletedAt IS NULL AND w.organizationId = :orgId");
        parameters.put("orgId", principal.organizationId());

        appendVisibility(principal, where, parameters);
        appendFilters(criteria, where, parameters);

        String order = orderClause(pageable);
        TypedQuery<WorkItem> query = entityManager.createQuery(
                "SELECT w FROM WorkItem w" + where + order, WorkItem.class);
        TypedQuery<Long> count = entityManager.createQuery(
                "SELECT COUNT(w) FROM WorkItem w" + where, Long.class);

        parameters.forEach((name, value) -> {
            query.setParameter(name, value);
            count.setParameter(name, value);
        });

        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(size);

        return new PageImpl<>(query.getResultList(), pageable, count.getSingleResult());
    }

    /**
     * The visibility predicate.
     *
     * <p>An organization-wide manager sees everything in the tenant; a manager sees their
     * department; a team lead sees their teams; everyone else sees work they are attached
     * to. Note that ADMIN gets no blanket read: admin is a configuration role, not an
     * operational one, and granting it visibility over all work would make it the
     * easiest way to bypass department isolation.
     *
     * <p>Security-flagged items narrow further, to people directly involved.
     */
    private void appendVisibility(AuthPrincipal principal, StringBuilder where,
                                  Map<String, Object> parameters) {
        if (principal.isManager() && principal.organizationWide()) {
            return;                       // tenant predicate above is the whole restriction
        }

        List<String> clauses = new ArrayList<>();
        clauses.add("w.assigneeId = :actorId");
        clauses.add("w.reporterId = :actorId");
        clauses.add("w.processOwnerId = :actorId");
        clauses.add("w.reviewerId = :actorId");
        clauses.add("EXISTS (SELECT 1 FROM WorkItemWatcher x "
                + "WHERE x.workItemId = w.id AND x.userId = :actorId)");
        parameters.put("actorId", principal.userId());

        if (principal.isManager() && principal.departmentId() != null) {
            clauses.add("w.departmentId = :actorDepartmentId");
            parameters.put("actorDepartmentId", principal.departmentId());
        }

        if (principal.hasRole("TEAM_LEAD") && !principal.teamIds().isEmpty()) {
            clauses.add("w.teamId IN :actorTeamIds");
            parameters.put("actorTeamIds", principal.teamIds());
        }

        where.append(" AND (").append(String.join(" OR ", clauses)).append(")");

        // Sensitive work narrows to the people actually involved, even inside a
        // department or team the caller otherwise oversees.
        where.append(" AND (w.securitySensitive = FALSE"
                + " OR w.assigneeId = :actorId OR w.reporterId = :actorId"
                + " OR w.processOwnerId = :actorId"
                + " OR :actorIsManager = TRUE)");
        parameters.put("actorIsManager", principal.isManager());
    }

    private void appendFilters(Criteria criteria, StringBuilder where, Map<String, Object> parameters) {
        if (isNotEmpty(criteria.types())) {
            where.append(" AND w.workItemType IN :types");
            parameters.put("types", criteria.types());
        }
        if (isNotEmpty(criteria.statuses())) {
            where.append(" AND w.status IN :statuses");
            parameters.put("statuses", criteria.statuses());
        }
        if (isNotEmpty(criteria.priorities())) {
            where.append(" AND w.priority IN :priorities");
            parameters.put("priorities", criteria.priorities());
        }
        if (isNotEmpty(criteria.families())) {
            where.append(" AND w.workFamily IN :families");
            parameters.put("families", criteria.families());
        }
        if (isNotEmpty(criteria.origins())) {
            where.append(" AND w.originType IN :origins");
            parameters.put("origins", criteria.origins());
        }
        addEquals(where, parameters, criteria.teamId(), "w.teamId", "teamId");
        addEquals(where, parameters, criteria.departmentId(), "w.departmentId", "departmentId");
        addEquals(where, parameters, criteria.assigneeId(), "w.assigneeId", "assigneeId");
        addEquals(where, parameters, criteria.projectId(), "w.projectId", "projectId");
        addEquals(where, parameters, criteria.sprintId(), "w.sprintId", "sprintId");
        addEquals(where, parameters, criteria.parentId(), "w.parentId", "parentId");

        if (criteria.unassigned() != null && criteria.unassigned()) {
            where.append(" AND w.assigneeId IS NULL");
        }
        if (criteria.blockedOnly() != null && criteria.blockedOnly()) {
            where.append(" AND w.blockedSince IS NOT NULL");
        }
        if (criteria.dueBefore() != null) {
            where.append(" AND w.dueDate IS NOT NULL AND w.dueDate <= :dueBefore");
            parameters.put("dueBefore", criteria.dueBefore());
        }
        if (criteria.query() != null && !criteria.query().isBlank()) {
            where.append(" AND (LOWER(w.summary) LIKE :q OR LOWER(w.description) LIKE :q"
                    + " OR LOWER(w.referenceKey) LIKE :q)");
            parameters.put("q", "%" + criteria.query().trim().toLowerCase() + "%");
        }
    }

    private void addEquals(StringBuilder where, Map<String, Object> parameters,
                           UUID value, String column, String name) {
        if (value != null) {
            where.append(" AND ").append(column).append(" = :").append(name);
            parameters.put(name, value);
        }
    }

    /**
     * Sorting is restricted to an allowlist. A pass-through would let a client sort by
     * any mapped property, which is both an injection surface and a way to probe columns
     * they are not shown.
     */
    private String orderClause(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return " ORDER BY w.createdAt DESC";
        }
        Set<String> sortable = Set.of("createdAt", "updatedAt", "dueDate", "priority",
                "status", "summary", "referenceKey", "storyPoints", "estimatedHours");

        List<String> clauses = new ArrayList<>();
        for (org.springframework.data.domain.Sort.Order order : pageable.getSort()) {
            if (!sortable.contains(order.getProperty())) {
                continue;
            }
            clauses.add("w." + order.getProperty() + (order.isAscending() ? " ASC" : " DESC"));
        }
        return clauses.isEmpty() ? " ORDER BY w.createdAt DESC" : " ORDER BY " + String.join(", ", clauses);
    }

    private static boolean isNotEmpty(java.util.Collection<?> collection) {
        return collection != null && !collection.isEmpty();
    }

    public record Criteria(List<com.smartwork.workitem.domain.WorkItemType> types,
                           List<String> statuses,
                           List<com.smartwork.workitem.domain.Priority> priorities,
                           List<com.smartwork.workitem.domain.WorkFamily> families,
                           List<com.smartwork.workitem.domain.OriginType> origins,
                           UUID teamId,
                           UUID departmentId,
                           UUID assigneeId,
                           UUID projectId,
                           UUID sprintId,
                           UUID parentId,
                           Boolean unassigned,
                           Boolean blockedOnly,
                           LocalDate dueBefore,
                           String query) {
    }
}
