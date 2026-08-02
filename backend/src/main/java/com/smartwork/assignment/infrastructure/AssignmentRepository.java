package com.smartwork.assignment.infrastructure;

import com.smartwork.assignment.domain.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    Optional<Assignment> findByWorkItemIdAndActiveTrue(UUID workItemId);

    /**
     * Recent assignment counts for the fairness score, for the whole candidate pool in one
     * query. Returns {@code [userId, count]} rows; users with no recent assignments are
     * absent and are treated as zero by the caller.
     */
    @Query("""
            SELECT a.assigneeId, COUNT(a)
            FROM Assignment a
            WHERE a.assigneeId IN :userIds
              AND a.assignedAt >= :since
            GROUP BY a.assigneeId
            """)
    List<Object[]> countAssignedSince(
            @Param("userIds") List<UUID> userIds, @Param("since") Instant since);
}
