package com.smartwork.workitem.infrastructure;

import com.smartwork.workitem.domain.WorkItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemRepository extends JpaRepository<WorkItem, UUID> {

    @Query("""
            SELECT w FROM WorkItem w
             WHERE w.id = :id AND w.organizationId = :organizationId AND w.deletedAt IS NULL
            """)
    Optional<WorkItem> findByIdInOrganization(@Param("id") UUID id,
                                              @Param("organizationId") UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT w FROM WorkItem w
             WHERE w.id = :id AND w.organizationId = :organizationId AND w.deletedAt IS NULL
            """)
    Optional<WorkItem> findByIdInOrganizationForUpdate(@Param("id") UUID id,
                                                       @Param("organizationId") UUID organizationId);

    @Query("""
            SELECT w FROM WorkItem w
             WHERE w.referenceKey = :referenceKey AND w.organizationId = :organizationId
               AND w.deletedAt IS NULL
            """)
    Optional<WorkItem> findByReferenceKey(@Param("referenceKey") String referenceKey,
                                          @Param("organizationId") UUID organizationId);

    @Query("""
            SELECT w FROM WorkItem w
             WHERE w.parentId = :parentId AND w.deletedAt IS NULL
             ORDER BY w.createdAt
            """)
    List<WorkItem> findChildren(@Param("parentId") UUID parentId);

    @Query("""
            SELECT w FROM WorkItem w
             WHERE w.id IN :ids AND w.organizationId = :organizationId AND w.deletedAt IS NULL
            """)
    List<WorkItem> findAllInOrganization(@Param("organizationId") UUID organizationId,
                                         @Param("ids") List<UUID> ids);

    /**
     * Items assigned to one person, board-shaped.
     *
     * <p>Ordered by priority then due date, because that is the order the person should
     * work in - not by creation date, which is the order the work happened to arrive.
     */
    @Query("""
            SELECT w FROM WorkItem w
             WHERE w.assigneeId = :userId AND w.organizationId = :organizationId
               AND w.deletedAt IS NULL
             ORDER BY CASE w.priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1
                                      WHEN 'MEDIUM' THEN 2 ELSE 3 END,
                      w.dueDate NULLS LAST, w.createdAt
            """)
    List<WorkItem> findAssignedTo(@Param("userId") UUID userId,
                                  @Param("organizationId") UUID organizationId);

    @Query("""
            SELECT w FROM WorkItem w
             WHERE w.reporterId = :userId AND w.organizationId = :organizationId
               AND w.deletedAt IS NULL
             ORDER BY w.createdAt DESC
            """)
    Page<WorkItem> findReportedBy(@Param("userId") UUID userId,
                                  @Param("organizationId") UUID organizationId,
                                  Pageable pageable);

    @Query("""
            SELECT w FROM WorkItem w
             WHERE w.organizationId = :organizationId AND w.deletedAt IS NULL
               AND EXISTS (SELECT 1 FROM WorkItemWatcher x
                            WHERE x.workItemId = w.id AND x.userId = :userId)
             ORDER BY w.updatedAt DESC
            """)
    Page<WorkItem> findWatchedBy(@Param("userId") UUID userId,
                                 @Param("organizationId") UUID organizationId,
                                 Pageable pageable);

    /**
     * Possible duplicates by trigram similarity on the summary.
     *
     * <p>Native because {@code %} and {@code similarity()} are PostgreSQL operators with
     * no JPQL equivalent. Good enough for the "printer broken again" class, which is most
     * of them; semantic duplicates need embeddings and are post-MVP (ADR-025).
     */
    @Query(value = """
            SELECT * FROM work_items w
             WHERE w.organization_id = :organizationId
               AND w.deleted_at IS NULL
               AND w.id <> :excludeId
               AND w.work_item_type = :workItemType
               AND similarity(w.summary, :summary) > :threshold
             ORDER BY similarity(w.summary, :summary) DESC
             LIMIT :limit
            """, nativeQuery = true)
    List<WorkItem> findSimilar(@Param("organizationId") UUID organizationId,
                               @Param("excludeId") UUID excludeId,
                               @Param("workItemType") String workItemType,
                               @Param("summary") String summary,
                               @Param("threshold") double threshold,
                               @Param("limit") int limit);

    @Query("SELECT COUNT(w) FROM WorkItem w WHERE w.parentId = :parentId AND w.deletedAt IS NULL")
    long countChildren(@Param("parentId") UUID parentId);
}
