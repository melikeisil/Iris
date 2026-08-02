package com.smartwork.organization.infrastructure;

import com.smartwork.organization.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    @Query("SELECT d FROM Department d WHERE d.organizationId = :organizationId ORDER BY d.code")
    List<Department> findAllInOrganization(@Param("organizationId") UUID organizationId);

    @Query("SELECT d FROM Department d WHERE d.id = :id AND d.organizationId = :organizationId")
    Optional<Department> findByIdInOrganization(@Param("id") UUID id,
                                                @Param("organizationId") UUID organizationId);

    @Query("SELECT d FROM Department d WHERE d.code = :code AND d.organizationId = :organizationId")
    Optional<Department> findByCode(@Param("code") String code,
                                    @Param("organizationId") UUID organizationId);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);
}
