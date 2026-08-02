package com.smartwork.team.domain;

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

/**
 * Membership of a team, with the share of the person's capacity that this team may claim.
 *
 * <p>Exactly one membership per user is the primary one; workload and department scoring
 * attribute the person there. Enforced by a partial unique index.
 */
@Entity
@Table(name = "team_members")
public class TeamMember {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Ids.newId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "allocation_percent", nullable = false)
    private short allocationPercent = 100;

    @Column(name = "primary_team", nullable = false)
    private boolean primaryTeam = true;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    protected TeamMember() {
    }

    TeamMember(Team team, UUID userId, int allocationPercent, boolean primaryTeam) {
        if (allocationPercent < 1 || allocationPercent > 100) {
            throw new IllegalArgumentException("Allocation must be between 1 and 100.");
        }
        this.team = team;
        this.userId = userId;
        this.allocationPercent = (short) allocationPercent;
        this.primaryTeam = primaryTeam;
    }

    public void changeAllocation(int allocationPercent) {
        if (allocationPercent < 1 || allocationPercent > 100) {
            throw new IllegalArgumentException("Allocation must be between 1 and 100.");
        }
        this.allocationPercent = (short) allocationPercent;
    }

    public void setPrimaryTeam(boolean primaryTeam) {
        this.primaryTeam = primaryTeam;
    }

    public UUID getId() {
        return id;
    }

    public Team getTeam() {
        return team;
    }

    public UUID getUserId() {
        return userId;
    }

    public short getAllocationPercent() {
        return allocationPercent;
    }

    public boolean isPrimaryTeam() {
        return primaryTeam;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
