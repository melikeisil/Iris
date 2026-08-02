package com.smartwork.process.domain;

import com.smartwork.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A running instance of a template — or of an ad-hoc AI-generated plan, when
 * {@code processTemplateId} is null. docs/08 §5.3 makes "a new non-standard plan was
 * generated" a PROCESS_OWNER_REVIEW trigger, so the null case is a first-class state
 * rather than a data-quality problem.
 */
@Entity
@Table(name = "process_definitions")
public class ProcessDefinition extends TenantEntity {

    public enum Status {
        RUNNING, COMPLETED, CANCELLED
    }

    @Column(name = "process_template_id")
    private UUID processTemplateId;

    @Column(name = "root_work_item_id", nullable = false, updatable = false)
    private UUID rootWorkItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.RUNNING;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ProcessDefinition() {
    }

    public ProcessDefinition(UUID organizationId, UUID rootWorkItemId, UUID processTemplateId) {
        super(organizationId);
        this.rootWorkItemId = rootWorkItemId;
        this.processTemplateId = processTemplateId;
    }

    public void complete(Instant when) {
        this.status = Status.COMPLETED;
        this.completedAt = when;
    }

    public void cancel() {
        this.status = Status.CANCELLED;
    }

    /** True when this plan was generated rather than instantiated from a known template. */
    public boolean isAdHoc() {
        return processTemplateId == null;
    }

    public UUID getProcessTemplateId() {
        return processTemplateId;
    }

    public UUID getRootWorkItemId() {
        return rootWorkItemId;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
