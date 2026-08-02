package com.smartwork.assignment.infrastructure;

import com.smartwork.assignment.domain.EmployeeLevelPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmployeeLevelPolicyRepository extends JpaRepository<EmployeeLevelPolicy, UUID> {

    List<EmployeeLevelPolicy> findByOrganizationId(UUID organizationId);
}
