package com.smartwork.workitem.infrastructure;

import com.smartwork.workitem.domain.LinkType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Refuses links that would create a cycle in the hierarchy or the dependency graph.
 *
 * <p>Not expressible as a CHECK constraint, so it runs as a recursive CTE before insert.
 * Without it, "A depends on B, B depends on A" is accepted and every subsequent traversal
 * - progress rollup, dependency guards, the blocked-work investigation - either loops
 * forever or silently truncates.
 */
@Component
public class LinkCycleDetector {

    /**
     * PostgreSQL's {@code CYCLE} clause would also work, but an explicit visited-set gives
     * the same guarantee on any version and is easier to reason about.
     */
    private static final String REACHABILITY = """
            WITH RECURSIVE reachable(id, depth) AS (
                SELECT l.target_id, 1
                  FROM work_item_links l
                 WHERE l.source_id = CAST(:startId AS uuid)
                   AND l.link_type = :linkType
                UNION
                SELECT l.target_id, r.depth + 1
                  FROM work_item_links l
                  JOIN reachable r ON l.source_id = r.id
                 WHERE l.link_type = :linkType
                   AND r.depth < 100
            )
            SELECT COUNT(*) FROM reachable WHERE id = CAST(:targetId AS uuid)
            """;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * @return true when adding {@code source -> target} would close a loop, because
     *         {@code source} is already reachable from {@code target}
     */
    public boolean wouldCreateCycle(UUID sourceId, LinkType linkType, UUID targetId) {
        if (!linkType.requiresCycleCheck()) {
            return false;
        }
        if (sourceId.equals(targetId)) {
            return true;
        }

        Number reachableCount = (Number) entityManager.createNativeQuery(REACHABILITY)
                .setParameter("startId", targetId.toString())
                .setParameter("targetId", sourceId.toString())
                .setParameter("linkType", linkType.name())
                .getSingleResult();

        return reachableCount.longValue() > 0;
    }

    /** Depth of the parent chain, used to keep hierarchies from becoming unnavigable. */
    public int ancestorDepth(UUID workItemId) {
        Number depth = (Number) entityManager.createNativeQuery("""
                WITH RECURSIVE ancestors(id, parent_id, depth) AS (
                    SELECT w.id, w.parent_id, 0
                      FROM work_items w
                     WHERE w.id = CAST(:workItemId AS uuid)
                    UNION ALL
                    SELECT w.id, w.parent_id, a.depth + 1
                      FROM work_items w
                      JOIN ancestors a ON w.id = a.parent_id
                     WHERE a.depth < 100
                )
                SELECT COALESCE(MAX(depth), 0) FROM ancestors
                """)
                .setParameter("workItemId", workItemId.toString())
                .getSingleResult();

        return depth.intValue();
    }
}
