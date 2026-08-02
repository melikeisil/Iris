package com.smartwork.project.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read contract for projects and components. */
public interface ProjectDirectory {

    Optional<ProjectView> find(UUID organizationId, UUID projectId);

    List<ProjectView> listAll(UUID organizationId);

    List<ComponentView> componentsOf(UUID organizationId, UUID projectId);

    record ProjectView(UUID id, UUID organizationId, UUID departmentId, String code, String name,
                       String keyPrefix, String status, UUID defaultWorkflowDefinitionId,
                       int componentCount, long version) {
    }

    record ComponentView(UUID id, String code, String name, boolean active) {
    }
}
