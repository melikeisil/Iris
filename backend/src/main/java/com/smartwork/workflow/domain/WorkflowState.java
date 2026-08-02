package com.smartwork.workflow.domain;

import com.smartwork.common.domain.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * One state in a profile, with the flags the rest of the platform reads.
 *
 * <p>{@code isTerminal} and {@code isTerminalSuccess} are separate on purpose: a SERVICE
 * ticket in {@code RESOLVED} counts as done for progress rollup but still has an outgoing
 * transition to {@code CLOSED}.
 */
@Entity
@Table(name = "workflow_states")
public class WorkflowState {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Ids.newId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    private WorkflowDefinition definition;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 16)
    private StateCategory category;

    @Column(name = "is_initial", nullable = false)
    private boolean initial;

    @Column(name = "is_terminal", nullable = false)
    private boolean terminal;

    /** Counts towards the numerator of parent progress. */
    @Column(name = "is_terminal_success", nullable = false)
    private boolean terminalSuccess;

    /** Hours in this state count against the assignee's capacity. */
    @Column(name = "is_active_work", nullable = false)
    private boolean activeWork;

    @Column(name = "is_blocked", nullable = false)
    private boolean blocked;

    /** Pauses the SLA clock: we are waiting on someone outside the delivering team. */
    @Column(name = "is_waiting_external", nullable = false)
    private boolean waitingExternal;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    protected WorkflowState() {
    }

    public UUID getId() {
        return id;
    }

    public WorkflowDefinition getDefinition() {
        return definition;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public StateCategory getCategory() {
        return category;
    }

    public boolean isInitial() {
        return initial;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean isTerminalSuccess() {
        return terminalSuccess;
    }

    public boolean isActiveWork() {
        return activeWork;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public boolean isWaitingExternal() {
        return waitingExternal;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }
}
