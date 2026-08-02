package com.smartwork.identity.domain;

import com.smartwork.common.domain.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A role grant. Roles are additive: holding TEAM_LEAD never removes EMPLOYEE rights. */
@Entity
@Table(name = "user_roles")
public class UserRole {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Ids.newId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt = Instant.now();

    @Column(name = "granted_by")
    private UUID grantedBy;

    protected UserRole() {
    }

    UserRole(User user, Role role, UUID grantedBy) {
        this.user = user;
        this.role = role;
        this.grantedBy = grantedBy;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Role getRole() {
        return role;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }
}
