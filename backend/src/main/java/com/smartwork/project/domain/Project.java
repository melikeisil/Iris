package com.smartwork.project.domain;

import com.smartwork.common.domain.TenantEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "projects")
public class Project extends TenantEntity {

    private static final Pattern KEY_PREFIX = Pattern.compile("^[A-Z][A-Z0-9]{1,9}$");

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * Prefix for locally generated reference keys (IDP-1, IDP-2...). Independent of any
     * Jira key so the platform stays usable before, during and after a Jira outage.
     */
    @Column(name = "key_prefix", nullable = false, length = 10)
    private String keyPrefix;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    @Column(name = "default_workflow_definition_id")
    private UUID defaultWorkflowDefinitionId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<ProjectComponent> components = new LinkedHashSet<>();

    protected Project() {
    }

    public Project(UUID organizationId, String code, String name, String keyPrefix) {
        super(organizationId);
        this.code = code.trim().toUpperCase();
        this.name = name;
        this.keyPrefix = requireValidPrefix(keyPrefix);
    }

    private static String requireValidPrefix(String prefix) {
        String normalized = prefix == null ? "" : prefix.trim().toUpperCase();
        if (!KEY_PREFIX.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Key prefix must be 2-10 uppercase alphanumerics starting with a letter, got: '"
                            + prefix + "'");
        }
        return normalized;
    }

    public ProjectComponent addComponent(String componentCode, String componentName) {
        boolean exists = components.stream()
                .anyMatch(component -> component.getCode().equalsIgnoreCase(componentCode));
        if (exists) {
            throw new IllegalStateException("Component '" + componentCode + "' already exists.");
        }
        ProjectComponent component = new ProjectComponent(this, componentCode, componentName);
        components.add(component);
        return component;
    }

    public boolean removeComponent(String componentCode) {
        return components.removeIf(component -> component.getCode().equalsIgnoreCase(componentCode));
    }

    public void update(String name, ProjectStatus status, UUID departmentId,
                       UUID defaultWorkflowDefinitionId) {
        this.name = name;
        this.status = status;
        this.departmentId = departmentId;
        this.defaultWorkflowDefinitionId = defaultWorkflowDefinitionId;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.status = ProjectStatus.ARCHIVED;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public UUID getDefaultWorkflowDefinitionId() {
        return defaultWorkflowDefinitionId;
    }

    public long getVersion() {
        return version;
    }

    public Set<ProjectComponent> getComponents() {
        return Set.copyOf(components);
    }
}
