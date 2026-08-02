package com.smartwork.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.util.UUID;

/**
 * Base for every entity that belongs to an organization.
 *
 * <p>Tenancy is enforced by an explicit {@code organization_id} predicate in every
 * repository query - see {@code OrganizationContext}. Carrying the column on a shared
 * superclass makes it impossible to define a tenant entity that forgets it, and gives
 * {@code CrossOrganizationIsolationTest} a single place to enumerate what must be checked.
 */
@MappedSuperclass
public abstract class TenantEntity extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    protected TenantEntity() {
    }

    protected TenantEntity(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    protected void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }
}
