package com.smartwork.workitem.infrastructure;

import com.smartwork.workitem.domain.LinkType;
import com.smartwork.workitem.domain.WorkItemLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemLinkRepository extends JpaRepository<WorkItemLink, UUID> {

    @Query("SELECT l FROM WorkItemLink l WHERE l.sourceId = :workItemId ORDER BY l.createdAt")
    List<WorkItemLink> findOutgoing(@Param("workItemId") UUID workItemId);

    @Query("SELECT l FROM WorkItemLink l WHERE l.targetId = :workItemId ORDER BY l.createdAt")
    List<WorkItemLink> findIncoming(@Param("workItemId") UUID workItemId);

    Optional<WorkItemLink> findBySourceIdAndLinkTypeAndTargetId(UUID sourceId, LinkType linkType, UUID targetId);

    @Query("""
            SELECT l FROM WorkItemLink l
             WHERE l.id = :id AND l.organizationId = :organizationId
            """)
    Optional<WorkItemLink> findByIdInOrganization(@Param("id") UUID id,
                                                  @Param("organizationId") UUID organizationId);

    /**
     * Targets this item depends on that are not yet finished.
     *
     * <p>Drives the NO_OPEN_DEPENDENCIES guard, and returns reference keys rather than IDs
     * so the refusal can name the blocking item instead of showing a UUID.
     */
    @Query("""
            SELECT t.referenceKey
              FROM WorkItemLink l
              JOIN WorkItem t ON t.id = l.targetId
              JOIN WorkflowState s ON s.definition.id = t.workflowDefinitionId AND s.code = t.status
             WHERE l.sourceId = :workItemId
               AND l.linkType = com.smartwork.workitem.domain.LinkType.DEPENDS_ON
               AND t.deletedAt IS NULL
               AND s.terminalSuccess = FALSE
            """)
    List<String> findOpenDependencyKeys(@Param("workItemId") UUID workItemId);

    /**
     * Items that resolve this one and are still unfinished.
     *
     * <p>When this returns empty for a ticket in WAITING_DEVELOPMENT, every linked bug is
     * done and the ticket can return to the requester for verification.
     */
    @Query("""
            SELECT COUNT(t)
              FROM WorkItemLink l
              JOIN WorkItem t ON t.id = l.targetId
              JOIN WorkflowState s ON s.definition.id = t.workflowDefinitionId AND s.code = t.status
             WHERE l.sourceId = :workItemId
               AND l.linkType = com.smartwork.workitem.domain.LinkType.RESOLVED_BY
               AND t.deletedAt IS NULL
               AND s.terminalSuccess = FALSE
            """)
    long countUnresolvedResolvers(@Param("workItemId") UUID workItemId);

    @Query("""
            SELECT COUNT(l) FROM WorkItemLink l
             WHERE l.sourceId = :workItemId
               AND l.linkType = com.smartwork.workitem.domain.LinkType.RESOLVED_BY
            """)
    long countResolvers(@Param("workItemId") UUID workItemId);
}
