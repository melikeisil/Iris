package com.smartwork.workflow.domain;

import com.smartwork.common.domain.Ids;
import com.smartwork.common.security.ResourceRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A permitted edge in a workflow profile.
 *
 * <p>Permission is the intersection of the two authorization axes: a caller may execute a
 * transition if they hold one of {@code allowedRoles} <em>or</em> one of
 * {@code allowedRelationships} on this specific item. An empty {@code allowedRoles} with a
 * non-empty {@code allowedRelationships} means the transition is relationship-only -
 * {@code SUBMIT_FOR_REVIEW} belongs to the assignee, not to anyone senior.
 */
@Entity
@Table(name = "workflow_transitions")
public class WorkflowTransition {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Ids.newId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    private WorkflowDefinition definition;

    @Column(name = "transition_code", nullable = false, length = 64)
    private String transitionCode;

    @Column(name = "label", nullable = false, length = 128)
    private String label;

    @Column(name = "from_state", nullable = false, length = 64)
    private String fromState;

    @Column(name = "to_state", nullable = false, length = 64)
    private String toState;

    @Column(name = "allowed_roles", nullable = false, length = 255)
    private String allowedRoles = "";

    @Column(name = "allowed_relationships", nullable = false, length = 255)
    private String allowedRelationships = "";

    @Column(name = "guards", nullable = false, length = 511)
    private String guards = "";

    @Column(name = "requires_reason", nullable = false)
    private boolean requiresReason;

    /** Fired by the system, never by a user. Audited with actor = SYSTEM. */
    @Column(name = "automatic", nullable = false)
    private boolean automatic;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    protected WorkflowTransition() {
    }

    public Set<String> allowedRoleCodes() {
        return split(allowedRoles);
    }

    public Set<ResourceRole> allowedResourceRoles() {
        return split(allowedRelationships).stream()
                .map(ResourceRole::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public List<String> guardCodes() {
        return List.copyOf(split(guards));
    }

    /**
     * True when the caller's system roles or per-item relationships permit this edge.
     *
     * <p>An automatic transition has neither set, and is never permitted to a human -
     * only {@code WorkflowEngine.applyAutomatic} may execute it.
     */
    public boolean permits(Set<String> actorRoles, Set<ResourceRole> actorRelationships) {
        if (automatic) {
            return false;
        }
        return actorRoles.stream().anyMatch(allowedRoleCodes()::contains)
                || actorRelationships.stream().anyMatch(allowedResourceRoles()::contains);
    }

    private static Set<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public UUID getId() {
        return id;
    }

    public WorkflowDefinition getDefinition() {
        return definition;
    }

    public String getTransitionCode() {
        return transitionCode;
    }

    public String getLabel() {
        return label;
    }

    public String getFromState() {
        return fromState;
    }

    public String getToState() {
        return toState;
    }

    public boolean isRequiresReason() {
        return requiresReason;
    }

    public boolean isAutomatic() {
        return automatic;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }
}
