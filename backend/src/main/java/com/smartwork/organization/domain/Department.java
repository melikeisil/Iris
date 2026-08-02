package com.smartwork.organization.domain;

import com.smartwork.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "departments")
public class Department extends TenantEntity {

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * The department lead. A UUID rather than a {@code User} association: a JPA edge into
     * the identity module would create a cycle, since identity already depends on this one.
     */
    @Column(name = "lead_user_id")
    private UUID leadUserId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Department() {
    }

    public Department(UUID organizationId, String code, String name) {
        super(organizationId);
        this.code = code.trim().toUpperCase();
        this.name = name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void assignLead(UUID leadUserId) {
        this.leadUserId = leadUserId;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public UUID getLeadUserId() {
        return leadUserId;
    }

    public boolean isActive() {
        return active;
    }
}
