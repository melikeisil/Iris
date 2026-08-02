package com.smartwork.team.domain;

import com.smartwork.common.domain.TenantEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "teams")
public class Team extends TenantEntity {

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "lead_user_id")
    private UUID leadUserId;

    @Column(name = "weekly_capacity_hours", precision = 7, scale = 2)
    private BigDecimal weeklyCapacityHours;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<TeamMember> members = new LinkedHashSet<>();

    protected Team() {
    }

    public Team(UUID organizationId, UUID departmentId, String code, String name) {
        super(organizationId);
        this.departmentId = departmentId;
        this.code = code.trim().toUpperCase();
        this.name = name;
    }

    public TeamMember addMember(UUID userId, int allocationPercent, boolean primaryTeam) {
        members.stream()
                .filter(member -> member.getUserId().equals(userId))
                .findFirst()
                .ifPresent(existing -> {
                    throw new IllegalStateException("User is already a member of this team.");
                });
        TeamMember member = new TeamMember(this, userId, allocationPercent, primaryTeam);
        members.add(member);
        return member;
    }

    public boolean removeMember(UUID userId) {
        return members.removeIf(member -> member.getUserId().equals(userId));
    }

    public void update(String name, UUID leadUserId, BigDecimal weeklyCapacityHours, boolean active) {
        this.name = name;
        this.leadUserId = leadUserId;
        this.weeklyCapacityHours = weeklyCapacityHours;
        this.active = active;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.active = false;
    }

    public UUID getDepartmentId() {
        return departmentId;
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

    public BigDecimal getWeeklyCapacityHours() {
        return weeklyCapacityHours;
    }

    public boolean isActive() {
        return active;
    }

    public Set<TeamMember> getMembers() {
        return Set.copyOf(members);
    }
}
