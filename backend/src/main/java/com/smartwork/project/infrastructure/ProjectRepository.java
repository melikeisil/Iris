package com.smartwork.project.infrastructure;

import com.smartwork.project.domain.Project;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("""
            SELECT p FROM Project p
             WHERE p.organizationId = :organizationId AND p.deletedAt IS NULL
             ORDER BY p.code
            """)
    List<Project> findAllInOrganization(@Param("organizationId") UUID organizationId);

    @EntityGraph(attributePaths = "components")
    @Query("""
            SELECT p FROM Project p
             WHERE p.id = :id AND p.organizationId = :organizationId AND p.deletedAt IS NULL
            """)
    Optional<Project> findByIdInOrganization(@Param("id") UUID id,
                                             @Param("organizationId") UUID organizationId);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);

    boolean existsByOrganizationIdAndKeyPrefix(UUID organizationId, String keyPrefix);
}
