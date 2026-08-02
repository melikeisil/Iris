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

/**
 * Maps what a model actually writes ("springboot", "spring boot") onto a canonical skill.
 * Without this, every analysis produces a slightly different vocabulary and the matching
 * quietly degrades.
 */
@Entity
@Table(name = "skill_aliases")
public class SkillAlias {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Ids.newId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "alias", nullable = false, length = 128)
    private String alias;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected SkillAlias() {
    }

    public SkillAlias(Skill skill, String alias) {
        this.skill = skill;
        this.alias = normalize(alias);
    }

    /** Alias lookup is case- and separator-insensitive. */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase();
    }

    public UUID getId() {
        return id;
    }

    public Skill getSkill() {
        return skill;
    }

    public String getAlias() {
        return alias;
    }
}
