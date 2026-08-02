package com.smartwork.project.application;

import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import com.smartwork.organization.application.DepartmentDirectory;
import com.smartwork.project.domain.Project;
import com.smartwork.project.domain.ProjectStatus;
import com.smartwork.project.infrastructure.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProjectService implements ProjectDirectory {

    private final ProjectRepository projects;
    private final DepartmentDirectory departments;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, DepartmentDirectory departments, Clock clock) {
        this.projects = projects;
        this.departments = departments;
        this.clock = clock;
    }

    @Override
    public Optional<ProjectView> find(UUID organizationId, UUID projectId) {
        return projects.findByIdInOrganization(projectId, organizationId).map(ProjectService::toView);
    }

    @Override
    public List<ProjectView> listAll(UUID organizationId) {
        return projects.findAllInOrganization(organizationId).stream().map(ProjectService::toView).toList();
    }

    @Override
    public List<ComponentView> componentsOf(UUID organizationId, UUID projectId) {
        return requireProject(organizationId, projectId).getComponents().stream()
                .map(component -> new ComponentView(component.getId(), component.getCode(),
                        component.getName(), component.isActive()))
                .sorted(java.util.Comparator.comparing(ComponentView::code))
                .toList();
    }

    @Transactional
    public ProjectView create(UUID organizationId, String code, String name, String keyPrefix,
                              UUID departmentId, UUID defaultWorkflowDefinitionId) {
        String normalizedCode = code.trim().toUpperCase();
        String normalizedPrefix = keyPrefix.trim().toUpperCase();

        if (projects.existsByOrganizationIdAndCode(organizationId, normalizedCode)) {
            throw ApiException.duplicate("A project with code '" + normalizedCode + "' already exists.");
        }
        if (projects.existsByOrganizationIdAndKeyPrefix(organizationId, normalizedPrefix)) {
            throw ApiException.duplicate("Key prefix '" + normalizedPrefix + "' is already in use.");
        }
        if (departmentId != null) {
            departments.require(organizationId, departmentId);
        }

        Project project;
        try {
            project = new Project(organizationId, normalizedCode, name, normalizedPrefix);
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, invalid.getMessage());
        }
        project.update(name, ProjectStatus.ACTIVE, departmentId, defaultWorkflowDefinitionId);
        return toView(projects.save(project));
    }

    @Transactional
    public ProjectView update(UUID organizationId, UUID projectId, String name, ProjectStatus status,
                              UUID departmentId, UUID defaultWorkflowDefinitionId) {
        Project project = requireProject(organizationId, projectId);
        if (departmentId != null) {
            departments.require(organizationId, departmentId);
        }
        project.update(name, status, departmentId, defaultWorkflowDefinitionId);
        return toView(project);
    }

    @Transactional
    public ComponentView addComponent(UUID organizationId, UUID projectId, String code, String name) {
        Project project = requireProject(organizationId, projectId);
        try {
            var component = project.addComponent(code, name);
            return new ComponentView(component.getId(), component.getCode(),
                    component.getName(), component.isActive());
        } catch (IllegalStateException duplicate) {
            throw ApiException.duplicate(duplicate.getMessage());
        }
    }

    @Transactional
    public void removeComponent(UUID organizationId, UUID projectId, String code) {
        if (!requireProject(organizationId, projectId).removeComponent(code)) {
            throw ApiException.notFound("Component", code);
        }
    }

    @Transactional
    public void archive(UUID organizationId, UUID projectId) {
        requireProject(organizationId, projectId).softDelete(clock.instant());
    }

    private Project requireProject(UUID organizationId, UUID projectId) {
        return projects.findByIdInOrganization(projectId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Project", projectId));
    }

    private static ProjectView toView(Project project) {
        return new ProjectView(project.getId(), project.getOrganizationId(), project.getDepartmentId(),
                project.getCode(), project.getName(), project.getKeyPrefix(),
                project.getStatus().name(), project.getDefaultWorkflowDefinitionId(),
                project.getComponents().size(), project.getVersion());
    }
}
