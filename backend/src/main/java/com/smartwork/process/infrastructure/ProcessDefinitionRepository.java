package com.smartwork.process.infrastructure;

import com.smartwork.process.domain.ProcessDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessDefinitionRepository extends JpaRepository<ProcessDefinition, UUID> {

    Optional<ProcessDefinition> findByRootWorkItemIdAndStatus(
            UUID rootWorkItemId, ProcessDefinition.Status status);

    List<ProcessDefinition> findByOrganizationIdAndStatus(
            UUID organizationId, ProcessDefinition.Status status);
}
