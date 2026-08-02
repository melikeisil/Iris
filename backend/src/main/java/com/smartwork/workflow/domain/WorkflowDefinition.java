package com.smartwork.workflow.domain;

import com.smartwork.common.domain.Ids;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A workflow profile. Workflows are data, not code (ADR-007): adding a team-specific
 * profile is an admin action, not a deployment.
 *
 * <p>A row with a null {@code organizationId} is a system default available to every
 * tenant; an organization may override a code with its own row.
 */
@Entity
@Table(name = "workflow_definitions")
public class WorkflowDefinition {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Ids.newId();

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "applies_to_family", nullable = false, length = 32)
    private String appliesToFamily;

    @Column(name = "initial_state", nullable = false, length = 64)
    private String initialState;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<WorkflowState> states = new LinkedHashSet<>();

    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<WorkflowTransition> transitions = new LinkedHashSet<>();

    protected WorkflowDefinition() {
    }

    public Optional<WorkflowState> state(String stateCode) {
        return states.stream().filter(state -> state.getCode().equals(stateCode)).findFirst();
    }

    /** Transitions leaving {@code fromState}, ignoring who the actor is. */
    public Set<WorkflowTransition> transitionsFrom(String fromState) {
        return transitions.stream()
                .filter(transition -> transition.getFromState().equals(fromState))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public Optional<WorkflowTransition> transition(String transitionCode, String fromState) {
        return transitions.stream()
                .filter(t -> t.getTransitionCode().equals(transitionCode) && t.getFromState().equals(fromState))
                .findFirst();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getAppliesToFamily() {
        return appliesToFamily;
    }

    public String getInitialState() {
        return initialState;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public boolean isActive() {
        return active;
    }

    public Set<WorkflowState> getStates() {
        return Set.copyOf(states);
    }

    public Set<WorkflowTransition> getTransitions() {
        return Set.copyOf(transitions);
    }

    public void replaceStates(Set<WorkflowState> states) {
        this.states = new LinkedHashSet<>(states);
    }

    public void replaceTransitions(Set<WorkflowTransition> transitions) {
        this.transitions = new LinkedHashSet<>(transitions);
    }
}
