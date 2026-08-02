-- V4: canonical skill vocabulary, aliases for AI output normalization, user proficiency.

CREATE TABLE skills (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id),
    code             VARCHAR(64)  NOT NULL,
    name             VARCHAR(255) NOT NULL,
    category         VARCHAR(32)  NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ  NULL,
    CONSTRAINT uq_skills_org_code UNIQUE (organization_id, code),
    CONSTRAINT ck_skills_category CHECK (category IN
        ('LANGUAGE', 'FRAMEWORK', 'PLATFORM', 'TOOL', 'DOMAIN', 'PROCESS', 'SOFT')),
    -- Canonical codes are UPPER_SNAKE_CASE. Enforced so the vocabulary cannot drift.
    CONSTRAINT ck_skills_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]*$')
);

CREATE INDEX ix_skills_organization ON skills (organization_id) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_skills_updated_at
    BEFORE UPDATE ON skills
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Maps what an LLM actually returns ("springboot", "spring boot") onto a canonical skill.
-- Unmatched codes are never auto-created; they are surfaced to an Admin for curation.
CREATE TABLE skill_aliases (
    id          UUID PRIMARY KEY,
    skill_id    UUID         NOT NULL REFERENCES skills (id) ON DELETE CASCADE,
    alias       VARCHAR(128) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_skill_aliases UNIQUE (skill_id, alias)
);

-- Alias lookup is case- and separator-insensitive; the normalized form is what we index.
CREATE UNIQUE INDEX uq_skill_aliases_normalized ON skill_aliases (lower(alias));

CREATE TABLE user_skills (
    id                UUID PRIMARY KEY,
    user_id           UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    skill_id          UUID          NOT NULL REFERENCES skills (id) ON DELETE CASCADE,
    proficiency       SMALLINT      NOT NULL,
    years_experience  NUMERIC(4, 1) NULL,
    last_used_at      DATE          NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_skills UNIQUE (user_id, skill_id),
    CONSTRAINT ck_user_skills_proficiency CHECK (proficiency BETWEEN 1 AND 5),
    CONSTRAINT ck_user_skills_years CHECK (years_experience IS NULL OR years_experience >= 0)
);

CREATE INDEX ix_user_skills_user ON user_skills (user_id);
CREATE INDEX ix_user_skills_skill ON user_skills (skill_id, proficiency DESC);

CREATE TRIGGER trg_user_skills_updated_at
    BEFORE UPDATE ON user_skills
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
