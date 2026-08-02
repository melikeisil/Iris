package com.smartwork.workitem.domain;

import com.smartwork.common.domain.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Backs the WATCHER relationship and the "Watching" filter on My Work. */
@Entity
@Table(name = "work_item_watchers")
public class WorkItemWatcher {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Ids.newId();

    @Column(name = "work_item_id", nullable = false, updatable = false)
    private UUID workItemId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected WorkItemWatcher() {
    }

    public WorkItemWatcher(UUID workItemId, UUID userId) {
        this.workItemId = workItemId;
        this.userId = userId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkItemId() {
        return workItemId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
