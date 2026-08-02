package com.smartwork.analysis.infrastructure;

import com.smartwork.analysis.domain.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

    List<Recommendation> findByWorkItemIdOrderByCreatedAtDesc(UUID workItemId);

    Optional<Recommendation> findByOrganizationIdAndId(UUID organizationId, UUID id);

    /**
     * The newest live recommendation of a type — what the UI shows and what Phase 6 acts on.
     *
     * <p>The dead statuses are bound as a parameter rather than written as string literals.
     * The column is {@code @Enumerated(STRING)}, so literals happen to work today, but
     * comparing an enum-typed path against strings relies on Hibernate's coercion rather
     * than on the type system — and it silently accepts a misspelled status as "no match"
     * instead of failing.
     */
    @Query("""
            SELECT r FROM Recommendation r
            WHERE r.workItemId = :workItemId
              AND r.recommendationType = :type
              AND r.status NOT IN :deadStatuses
            ORDER BY r.createdAt DESC
            LIMIT 1
            """)
    Optional<Recommendation> findLatestOfType(
            @Param("workItemId") UUID workItemId,
            @Param("type") Recommendation.Type type,
            @Param("deadStatuses") java.util.Collection<Recommendation.Status> deadStatuses);

    default Optional<Recommendation> findLatestLive(UUID workItemId, Recommendation.Type type) {
        return findLatestOfType(workItemId, type, java.util.EnumSet.of(
                Recommendation.Status.SUPERSEDED,
                Recommendation.Status.STALE,
                Recommendation.Status.REJECTED));
    }

    List<Recommendation> findByOrganizationIdAndStatus(UUID organizationId, Recommendation.Status status);
}
