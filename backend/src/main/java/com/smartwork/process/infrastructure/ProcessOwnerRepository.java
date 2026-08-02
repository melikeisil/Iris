package com.smartwork.process.infrastructure;

import com.smartwork.process.domain.ProcessOwnerAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProcessOwnerRepository extends JpaRepository<ProcessOwnerAssignment, UUID> {

    List<ProcessOwnerAssignment> findByWorkItemId(UUID workItemId);

    List<ProcessOwnerAssignment> findByProcessDefinitionId(UUID processDefinitionId);

    List<ProcessOwnerAssignment> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);
}
