package com.smartwork.identity.domain;

import com.smartwork.common.domain.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Proficiency in a skill, 1-5.
 *
 * <p>{@code lastUsedAt} exists because the scorer awards a recency bonus: a skill last
 * exercised three years ago is not the same asset as one used last month.
 */
@Entity
@Table(name = "user_skills")
public class UserSkill {

    public static final int MIN_PROFICIENCY = 1;
    public static final int MAX_PROFICIENCY = 5;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Ids.newId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "proficiency", nullable = false)
    private short proficiency;

    @Column(name = "years_experience", precision = 4, scale = 1)
    private BigDecimal yearsExperience;

    @Column(name = "last_used_at")
    private LocalDate lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserSkill() {
    }

    public UserSkill(User user, Skill skill, int proficiency,
                     BigDecimal yearsExperience, LocalDate lastUsedAt) {
        this.user = user;
        this.skill = skill;
        this.proficiency = requireValidProficiency(proficiency);
        this.yearsExperience = yearsExperience;
        this.lastUsedAt = lastUsedAt;
    }

    private static short requireValidProficiency(int proficiency) {
        if (proficiency < MIN_PROFICIENCY || proficiency > MAX_PROFICIENCY) {
            throw new IllegalArgumentException(
                    "Proficiency must be between %d and %d, got %d"
                            .formatted(MIN_PROFICIENCY, MAX_PROFICIENCY, proficiency));
        }
        return (short) proficiency;
    }

    public void update(int proficiency, BigDecimal yearsExperience, LocalDate lastUsedAt) {
        this.proficiency = requireValidProficiency(proficiency);
        this.yearsExperience = yearsExperience;
        this.lastUsedAt = lastUsedAt;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Skill getSkill() {
        return skill;
    }

    public short getProficiency() {
        return proficiency;
    }

    public BigDecimal getYearsExperience() {
        return yearsExperience;
    }

    public LocalDate getLastUsedAt() {
        return lastUsedAt;
    }
}
