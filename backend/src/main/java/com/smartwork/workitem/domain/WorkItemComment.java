package com.smartwork.workitem.domain;

import com.smartwork.common.domain.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work_item_comments")
public class WorkItemComment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Ids.newId();

    @Column(name = "work_item_id", nullable = false, updatable = false)
    private UUID workItemId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "body", nullable = false)
    private String body;

    /**
     * Hidden from the REQUESTER, who is usually outside the delivering department. The
     * alternative - a second comment system - is how teams end up discussing work
     * somewhere the platform cannot see.
     */
    @Column(name = "internal", nullable = false)
    private boolean internal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected WorkItemComment() {
    }

    public WorkItemComment(UUID workItemId, UUID authorId, String body, boolean internal) {
        this.workItemId = workItemId;
        this.authorId = authorId;
        this.body = body;
        this.internal = internal;
    }

    public void edit(String body) {
        this.body = body;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkItemId() {
        return workItemId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getBody() {
        return body;
    }

    public boolean isInternal() {
        return internal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
