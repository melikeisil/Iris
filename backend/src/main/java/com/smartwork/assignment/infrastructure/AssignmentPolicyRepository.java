package com.smartwork.assignment.infrastructure;

import com.smartwork.assignment.domain.AssignmentPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AssignmentPolicyRepository extends JpaRepository<AssignmentPolicy, UUID> {

    /**
     * Every candidate policy for a scope, in resolution order (docs/08 §8): most specific
     * first, then lower {@code priority} within a tier.
     *
     * <p>Fetches all three tiers in one round trip rather than probing team, then
     * department, then organization — three queries to answer one question, when the whole
     * candidate set is at most three rows.
     */
    @Query("""
            SELECT p FROM AssignmentPolicy p
            WHERE p.organizationId = :organizationId
              AND p.active = true
              AND (p.teamId IS NULL OR p.teamId = :teamId)
              AND (p.departmentId IS NULL OR p.departmentId = :departmentId)
            ORDER BY
              CASE WHEN p.teamId IS NOT NULL THEN 2
                   WHEN p.departmentId IS NOT NULL THEN 1
                   ELSE 0 END DESC,
              p.priority ASC
            """)
    List<AssignmentPolicy> findResolutionChain(
            @Param("organizationId") UUID organizationId,
            @Param("departmentId") UUID departmentId,
            @Param("teamId") UUID teamId);

    List<AssignmentPolicy> findByOrganizationIdAndActiveTrue(UUID organizationId);
}
