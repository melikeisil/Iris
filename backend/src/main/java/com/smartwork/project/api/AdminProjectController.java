package com.smartwork.project.api;

import com.smartwork.common.error.ApiException;
import com.smartwork.common.tenancy.OrganizationContext;
import com.smartwork.project.application.ProjectDirectory;
import com.smartwork.project.application.ProjectService;
import com.smartwork.project.domain.ProjectStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/projects")
@Tag(name = "admin")
public class AdminProjectController {

    private final ProjectService projects;

    public AdminProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping
    @Operation(summary = "List projects")
    public List<ProjectDirectory.ProjectView> list() {
        return projects.listAll(OrganizationContext.require());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one project")
    public ProjectDirectory.ProjectView get(@PathVariable UUID id) {
        return projects.find(OrganizationContext.require(), id)
                .orElseThrow(() -> ApiException.notFound("Project", id));
    }

    @PostMapping
    @Operation(summary = "Create a project",
            description = "`keyPrefix` backs locally generated reference keys (IDP-1, IDP-2...) "
                    + "and is independent of any Jira key.")
    public ResponseEntity<ProjectDirectory.ProjectView> create(@Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.status(201).body(projects.create(OrganizationContext.require(),
                request.code(), request.name(), request.keyPrefix(),
                request.departmentId(), request.defaultWorkflowDefinitionId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a project")
    public ProjectDirectory.ProjectView update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateProjectRequest request) {
        return projects.update(OrganizationContext.require(), id, request.name(),
                request.status(), request.departmentId(), request.defaultWorkflowDefinitionId());
    }

    @GetMapping("/{id}/components")
    @Operation(summary = "List a project's components")
    public List<ProjectDirectory.ComponentView> components(@PathVariable UUID id) {
        return projects.componentsOf(OrganizationContext.require(), id);
    }

    @PostMapping("/{id}/components")
    @Operation(summary = "Add a component")
    public ResponseEntity<ProjectDirectory.ComponentView> addComponent(
            @PathVariable UUID id, @Valid @RequestBody AddComponentRequest request) {
        return ResponseEntity.status(201).body(projects.addComponent(
                OrganizationContext.require(), id, request.code(), request.name()));
    }

    @DeleteMapping("/{id}/components/{code}")
    @Operation(summary = "Remove a component")
    public ResponseEntity<Void> removeComponent(@PathVariable UUID id, @PathVariable String code) {
        projects.removeComponent(OrganizationContext.require(), id, code);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Archive a project")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        projects.archive(OrganizationContext.require(), id);
        return ResponseEntity.noContent().build();
    }

    public record CreateProjectRequest(@NotBlank @Size(max = 64) String code,
                                       @NotBlank @Size(max = 255) String name,
                                       @NotBlank @Size(min = 2, max = 10) String keyPrefix,
                                       UUID departmentId,
                                       UUID defaultWorkflowDefinitionId) {
    }

    public record UpdateProjectRequest(@NotBlank @Size(max = 255) String name,
                                       @NotNull ProjectStatus status,
                                       UUID departmentId,
                                       UUID defaultWorkflowDefinitionId) {
    }

    public record AddComponentRequest(@NotBlank @Size(max = 64) String code,
                                      @NotBlank @Size(max = 255) String name) {
    }
}
