package com.smartwork.workitem.domain;

import com.smartwork.common.domain.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A typed relationship, stored once in the canonical direction.
 *
 * <p>Source and target are UUIDs rather than associations: the link table is traversed in
 * both directions by ID and never needs the loaded entity, so an association would only
 * buy lazy-loading problems.
 */
@Entity
@Table(name = "work_item_links")
public class WorkItemLink {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Ids.newId();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "source_id", nullable = false, updatable = false)
    private UUID sourceId;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, updatable = false, length = 24)
    private LinkType linkType;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected WorkItemLink() {
    }

    public WorkItemLink(UUID organizationId, UUID sourceId, LinkType linkType, UUID targetId,
                        String note, UUID createdBy) {
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("A work item cannot link to itself.");
        }
        this.organizationId = organizationId;
        this.sourceId = sourceId;
        this.linkType = linkType;
        this.targetId = targetId;
        this.note = note;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public LinkType getLinkType() {
        return linkType;
    }

    public String getNote() {
        return note;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
