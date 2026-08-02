package com.smartwork.policy.infrastructure;

import com.smartwork.policy.domain.ApprovalRequest;
import com.smartwork.policy.domain.ApprovalRoute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    Optional<ApprovalRequest> findByOrganizationIdAndId(UUID organizationId, UUID id);

    List<ApprovalRequest> findByWorkItemIdOrderByCreatedAtDesc(UUID workItemId);

    Optional<ApprovalRequest> findByWorkItemIdAndRouteAndStatus(
            UUID workItemId, ApprovalRoute route, ApprovalRequest.Status status);

    /**
     * The caller's queue.
     *
     * <p>Visibility is pushed into SQL rather than fetched and filtered, the same rule the
     * work item search follows: a reviewer must not be able to infer the existence of a
     * request outside their scope from a page total. The scope strings are supplied
     * pre-built by the service, so the index on {@code assigned_to_scope} is usable and
     * this stays a single indexed lookup rather than a scan with string parsing.
     */
    // The OPEN status is bound as a parameter rather than written as a JPQL enum literal.
    // A nested enum has no spellable literal form in JPQL - `ApprovalRequest$Status.OPEN`
    // is a JVM binary name, not something the query parser accepts - and the failure would
    // surface at context startup rather than at compile time.
    @Query("""
            SELECT r FROM ApprovalRequest r
            WHERE r.organizationId = :organizationId
              AND r.status = :status
              AND (r.assignedToUserId = :userId OR r.assignedToScope IN :scopes)
            ORDER BY r.dueAt ASC NULLS LAST, r.createdAt ASC
            """)
    Page<ApprovalRequest> findForActor(
            @Param("organizationId") UUID organizationId,
            @Param("status") ApprovalRequest.Status status,
            @Param("userId") UUID userId,
            @Param("scopes") Collection<String> scopes,
            Pageable pageable);

    default Page<ApprovalRequest> findOpenForActor(
            UUID organizationId, UUID userId, Collection<String> scopes, Pageable pageable) {
        return findForActor(organizationId, ApprovalRequest.Status.OPEN, userId, scopes, pageable);
    }

    @Query("""
            SELECT r FROM ApprovalRequest r
            WHERE r.organizationId = :organizationId
              AND r.status = :status
              AND r.route = :route
              AND (r.assignedToUserId = :userId OR r.assignedToScope IN :scopes)
            ORDER BY r.dueAt ASC NULLS LAST, r.createdAt ASC
            """)
    List<ApprovalRequest> findForActorByRoute(
            @Param("organizationId") UUID organizationId,
            @Param("status") ApprovalRequest.Status status,
            @Param("route") ApprovalRoute route,
            @Param("userId") UUID userId,
            @Param("scopes") Collection<String> scopes);

    default List<ApprovalRequest> findOpenForActorByRoute(
            UUID organizationId, ApprovalRoute route, UUID userId, Collection<String> scopes) {
        return findForActorByRoute(
                organizationId, ApprovalRequest.Status.OPEN, route, userId, scopes);
    }

    /** Escalations awaiting a manager. Managers see the whole organization. */
    List<ApprovalRequest> findByOrganizationIdAndRouteAndStatusOrderByDueAtAsc(
            UUID organizationId, ApprovalRoute route, ApprovalRequest.Status status);

    List<ApprovalRequest> findByOrganizationIdAndStatusOrderByDueAtAsc(
            UUID organizationId, ApprovalRequest.Status status);

    @Query("""
            SELECT r FROM ApprovalRequest r
            WHERE r.organizationId = :organizationId
              AND r.status = :status
              AND r.dueAt IS NOT NULL AND r.dueAt < :now
            ORDER BY r.dueAt ASC
            """)
    List<ApprovalRequest> findPastDue(
            @Param("organizationId") UUID organizationId,
            @Param("status") ApprovalRequest.Status status,
            @Param("now") Instant now);

    default List<ApprovalRequest> findOverdue(UUID organizationId, Instant now) {
        return findPastDue(organizationId, ApprovalRequest.Status.OPEN, now);
    }

    /** Route distribution for the AI metrics dashboard (docs/06 §11). */
    @Query("""
            SELECT r.route, COUNT(r) FROM ApprovalRequest r
            WHERE r.organizationId = :organizationId
            GROUP BY r.route
            """)
    List<Object[]> countByRoute(@Param("organizationId") UUID organizationId);
}
