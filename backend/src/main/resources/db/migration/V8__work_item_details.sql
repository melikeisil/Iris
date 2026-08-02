-- V8: typed relationships, required skills, discussion, attachments, watchers.

-- Twelve link types, six inverse pairs. Stored once in the canonical direction
-- (PARENT_OF, BLOCKS, RESOLVED_BY) and rendered in both directions by the read model,
-- which is what makes the uniqueness constraint enforceable.
CREATE TABLE work_item_links (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id),
    source_id       UUID        NOT NULL REFERENCES work_items (id) ON DELETE CASCADE,
    target_id       UUID        NOT NULL REFERENCES work_items (id) ON DELETE CASCADE,
    link_type       VARCHAR(24) NOT NULL,
    note            VARCHAR(500) NULL,
    created_by      UUID        NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_wil UNIQUE (source_id, link_type, target_id),
    CONSTRAINT ck_wil_not_self CHECK (source_id <> target_id),
    CONSTRAINT ck_wil_type CHECK (link_type IN (
        'PARENT_OF', 'CHILD_OF', 'BLOCKS', 'DEPENDS_ON', 'DUPLICATES', 'RELATES_TO',
        'RESOLVES', 'RESOLVED_BY', 'CAUSED_BY', 'IMPLEMENTS', 'GENERATED_FROM', 'TESTS'))
);

CREATE INDEX ix_wil_source ON work_item_links (source_id, link_type);
CREATE INDEX ix_wil_target ON work_item_links (target_id, link_type);

-- Cycle prevention for PARENT_OF, BLOCKS and DEPENDS_ON cannot be a CHECK constraint;
-- LinkCycleDetector runs a recursive CTE before insert.

CREATE TABLE work_item_skills (
    id              UUID PRIMARY KEY,
    work_item_id    UUID     NOT NULL REFERENCES work_items (id) ON DELETE CASCADE,
    skill_id        UUID     NOT NULL REFERENCES skills (id),
    min_proficiency SMALLINT NOT NULL DEFAULT 1,
    mandatory       BOOLEAN  NOT NULL DEFAULT FALSE,
    source          VARCHAR(16) NOT NULL DEFAULT 'MANUAL',

    CONSTRAINT uq_wis UNIQUE (work_item_id, skill_id),
    CONSTRAINT ck_wis_proficiency CHECK (min_proficiency BETWEEN 1 AND 5),
    CONSTRAINT ck_wis_source CHECK (source IN ('AI', 'MANUAL', 'TEMPLATE'))
);

CREATE INDEX ix_wis_item ON work_item_skills (work_item_id);
CREATE INDEX ix_wis_skill ON work_item_skills (skill_id);

CREATE TABLE work_item_comments (
    id           UUID PRIMARY KEY,
    work_item_id UUID        NOT NULL REFERENCES work_items (id) ON DELETE CASCADE,
    author_id    UUID        NOT NULL REFERENCES users (id),
    body         TEXT        NOT NULL,
    -- Internal comments are hidden from the REQUESTER, who is often outside the
    -- delivering department.
    internal     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_wic_body CHECK (length(body) BETWEEN 1 AND 10000)
);

CREATE INDEX ix_wic_item ON work_item_comments (work_item_id, created_at DESC);

CREATE TRIGGER trg_work_item_comments_updated_at
    BEFORE UPDATE ON work_item_comments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE work_item_attachments (
    id            UUID PRIMARY KEY,
    work_item_id  UUID         NOT NULL REFERENCES work_items (id) ON DELETE CASCADE,
    uploaded_by   UUID         NOT NULL REFERENCES users (id),
    filename      VARCHAR(255) NOT NULL,
    content_type  VARCHAR(128) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    -- Generated name; the original filename is metadata only, never a path.
    storage_path  VARCHAR(512) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_wia_size CHECK (size_bytes > 0 AND size_bytes <= 26214400)   -- 25 MiB
);

CREATE INDEX ix_wia_item ON work_item_attachments (work_item_id);

-- The WATCHER relationship - axis 2 of authorization, and the "Watching" filter on My Work.
CREATE TABLE work_item_watchers (
    id           UUID PRIMARY KEY,
    work_item_id UUID        NOT NULL REFERENCES work_items (id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_wiw UNIQUE (work_item_id, user_id)
);

CREATE INDEX ix_wiw_user ON work_item_watchers (user_id);
