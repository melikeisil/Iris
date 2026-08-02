package com.smartwork.process.infrastructure;

import com.smartwork.process.domain.ProcessTemplate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessTemplateRepository extends JpaRepository<ProcessTemplate, UUID> {

    /**
     * Templates with their tasks eagerly loaded. The matcher scores against every active
     * template and the instantiator immediately walks the winner's tasks, so lazy-loading
     * here would be an N+1 on a code path that runs on every incoming ticket.
     */
    @Query("SELECT DISTINCT t FROM ProcessTemplate t LEFT JOIN FETCH t.tasks "
            + "WHERE t.organizationId = :organizationId AND t.active = true")
    List<ProcessTemplate> findActiveWithTasks(@Param("organizationId") UUID organizationId);

    @EntityGraph(attributePaths = "tasks")
    Optional<ProcessTemplate> findByOrganizationIdAndCode(UUID organizationId, String code);

    @EntityGraph(attributePaths = "tasks")
    Optional<ProcessTemplate> findByOrganizationIdAndId(UUID organizationId, UUID id);

    List<ProcessTemplate> findByOrganizationIdOrderByNameAsc(UUID organizationId);
}
