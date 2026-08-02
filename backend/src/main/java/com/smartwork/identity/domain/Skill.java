package com.smartwork.identity.domain;

import com.smartwork.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A canonical skill. Codes are UPPER_SNAKE_CASE and controlled: free-text skills make
 * matching unusable, so AI output is mapped onto this vocabulary through
 * {@link SkillAlias} and unmatched codes are surfaced to an Admin rather than invented.
 */
@Entity
@Table(name = "skills")
public class Skill extends TenantEntity {

    private static final Pattern CANONICAL = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private SkillCategory category;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Skill() {
    }

    public Skill(UUID organizationId, String code, String name, SkillCategory category) {
        super(organizationId);
        this.code = requireCanonical(code);
        this.name = name;
        this.category = category;
    }

    public static String requireCanonical(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        if (!CANONICAL.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Skill code must be UPPER_SNAKE_CASE, got: '" + code + "'");
        }
        return normalized;
    }

    public void rename(String name, SkillCategory category) {
        this.name = name;
        this.category = category;
    }

    public void deactivate(Instant now) {
        this.active = false;
        this.deletedAt = now;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public SkillCategory getCategory() {
        return category;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
