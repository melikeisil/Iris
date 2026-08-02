package com.smartwork.workitem.infrastructure;

import com.smartwork.workitem.domain.WorkItemComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WorkItemCommentRepository extends JpaRepository<WorkItemComment, UUID> {

    /**
     * @param includeInternal false for a REQUESTER, who should not see the delivering
     *                        team's internal discussion
     */
    @Query("""
            SELECT c FROM WorkItemComment c
             WHERE c.workItemId = :workItemId
               AND (:includeInternal = TRUE OR c.internal = FALSE)
             ORDER BY c.createdAt
            """)
    List<WorkItemComment> findForItem(@Param("workItemId") UUID workItemId,
                                      @Param("includeInternal") boolean includeInternal);
}
