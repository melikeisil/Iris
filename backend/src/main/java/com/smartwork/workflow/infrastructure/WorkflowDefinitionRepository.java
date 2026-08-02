package com.smartwork.workflow.infrastructure;

import com.smartwork.workflow.domain.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, UUID> {

    /**
     * States and transitions are fetched in two queries rather than one join, because
     * joining two collections would produce a cartesian product.
     */
    @Query("SELECT DISTINCT d FROM WorkflowDefinition d LEFT JOIN FETCH d.states WHERE d.active = TRUE")
    List<WorkflowDefinition> findAllWithStates();

    @Query("SELECT DISTINCT d FROM WorkflowDefinition d LEFT JOIN FETCH d.transitions WHERE d.active = TRUE")
    List<WorkflowDefinition> findAllWithTransitions();

    /**
     * An organization-specific override wins over the system default of the same code.
     */
    @Query("""
            SELECT d FROM WorkflowDefinition d
             WHERE d.code = :code
               AND d.active = TRUE
               AND (d.organizationId = :organizationId OR d.organizationId IS NULL)
             ORDER BY CASE WHEN d.organizationId IS NULL THEN 1 ELSE 0 END
            """)
    List<WorkflowDefinition> findByCodeForOrganization(@Param("code") String code,
                                                       @Param("organizationId") UUID organizationId);

    Optional<WorkflowDefinition> findByIdAndActiveTrue(UUID id);
}
