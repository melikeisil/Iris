-- V9: process templates and running process instances.
--
-- A process template is a *reusable plan*, not a workflow. The distinction matters:
-- workflow_definitions (V6) describe the states a single item moves through; a template
-- describes the set of items a request explodes into. Onboarding has nine tasks across
-- three departments, each of which then runs its own SERVICE workflow.

CREATE TABLE process_templates (
    id                        UUID PRIMARY KEY,
    organization_id           UUID          NOT NULL REFERENCES organizations (id),
    code                      VARCHAR(64)   NOT NULL,
    name                      VARCHAR(255)  NOT NULL,
    description               TEXT          NULL,
    category                  VARCHAR(64)   NULL,
    work_family               VARCHAR(32)   NOT NULL DEFAULT 'PROCESS_WORK',
    owner_department_id       UUID          NULL REFERENCES departments (id),

    -- The Process Owner this template's instances default to. Deliberately a user and
    -- not a role: docs/14 seeds the HR Operations Lead as an EMPLOYEE to demonstrate
    -- that PROCESS_OWNER is a resource relationship, not a system role.
    default_process_owner_id  UUID          NULL REFERENCES users (id),

    default_priority          VARCHAR(16)   NOT NULL DEFAULT 'MEDIUM',
    closure_type              VARCHAR(32)   NOT NULL DEFAULT 'PROCESS_OWNER_VERIFICATION',
    sla_policy                JSONB         NULL,
    required_approvals        JSONB         NULL,

    -- Free-text trigger phrases the matcher scores an incoming summary against.
    -- Stored on the template rather than hardcoded so an Admin can tune matching
    -- without a deploy.
    match_keywords            TEXT          NULL,

    active                    BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                   BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT uq_process_templates_code UNIQUE (organization_id, code),
    CONSTRAINT ck_pt_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_pt_family CHECK (work_family IN
        ('SERVICE_WORK', 'PROCESS_WORK', 'DEVELOPMENT_WORK', 'INCIDENT_WORK',
         'GENERAL_OPERATIONAL_WORK')),
    CONSTRAINT ck_pt_priority CHECK (default_priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_pt_closure CHECK (closure_type IN
        ('AUTO_CLOSE', 'REQUESTER_VERIFICATION', 'PROCESS_OWNER_VERIFICATION',
         'TEAM_LEAD_VERIFICATION'))
);

CREATE INDEX ix_process_templates_org ON process_templates (organization_id) WHERE active;

CREATE TRIGGER trg_process_templates_updated_at
    BEFORE UPDATE ON process_templates
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE process_template_tasks (
    id                    UUID PRIMARY KEY,
    process_template_id   UUID          NOT NULL REFERENCES process_templates (id) ON DELETE CASCADE,

    -- Sequence is the template-local identifier that depends_on_sequences references.
    -- Deliberately not the PK: a template author reorders steps by editing numbers,
    -- and UUID references would make that unreadable in the seed files.
    sequence              SMALLINT      NOT NULL,

    title                 VARCHAR(255)  NOT NULL,
    description           TEXT          NULL,
    work_item_type        VARCHAR(24)   NOT NULL DEFAULT 'TASK',
    department_id         UUID          NULL REFERENCES departments (id),
    team_id               UUID          NULL REFERENCES teams (id),
    estimated_hours       NUMERIC(6, 2) NULL,
    priority              VARCHAR(16)   NULL,

    -- CSV of canonical skill codes. Not a join table: a template task's skill list is
    -- read as a whole, never queried across, and CSV keeps the seed files reviewable.
    required_skill_codes  VARCHAR(512)  NULL,

    -- CSV of sibling `sequence` values this step depends on. Resolved into real
    -- work_item_links at instantiation time (ProcessInstantiator).
    depends_on_sequences  VARCHAR(128)  NULL,

    requires_approval     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_ptt_sequence UNIQUE (process_template_id, sequence),
    CONSTRAINT ck_ptt_sequence_positive CHECK (sequence > 0),
    CONSTRAINT ck_ptt_type CHECK (work_item_type IN (
        'REQUEST', 'INCIDENT', 'EPIC', 'FEATURE', 'STORY', 'BUG', 'TASK',
        'TECHNICAL_DEBT', 'RESEARCH', 'TEST', 'DOCUMENTATION', 'SUBTASK')),
    CONSTRAINT ck_ptt_priority CHECK (priority IS NULL OR priority IN
        ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_ptt_hours CHECK (estimated_hours IS NULL OR estimated_hours > 0)
);

CREATE INDEX ix_ptt_template ON process_template_tasks (process_template_id, sequence);

-- A running instance of a template (or of an ad-hoc AI-generated plan, when
-- process_template_id is null - "a new non-standard plan was generated" in docs/08 §5.3).
CREATE TABLE process_definitions (
    id                    UUID PRIMARY KEY,
    organization_id       UUID         NOT NULL REFERENCES organizations (id),
    process_template_id   UUID         NULL REFERENCES process_templates (id),
    root_work_item_id     UUID         NOT NULL REFERENCES work_items (id) ON DELETE CASCADE,
    status                VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',
    started_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at          TIMESTAMPTZ  NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_pd_status CHECK (status IN ('RUNNING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_pd_completed CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL) OR
        (status <> 'COMPLETED' AND completed_at IS NULL))
);

-- One live process per root item; re-instantiating requires cancelling the old one.
CREATE UNIQUE INDEX uq_pd_active_root
    ON process_definitions (root_work_item_id) WHERE status = 'RUNNING';
CREATE INDEX ix_pd_organization ON process_definitions (organization_id, status);

-- PROCESS_OWNER as a resource-scoped relationship (docs/03), not a system role.
-- SUBTREE scope is what lets the owner of an onboarding request also own its nine
-- generated children without nine rows.
CREATE TABLE process_owners (
    id                     UUID PRIMARY KEY,
    organization_id        UUID         NOT NULL REFERENCES organizations (id),
    work_item_id           UUID         NULL REFERENCES work_items (id) ON DELETE CASCADE,
    process_definition_id  UUID         NULL REFERENCES process_definitions (id) ON DELETE CASCADE,
    user_id                UUID         NOT NULL REFERENCES users (id),
    scope                  VARCHAR(16)  NOT NULL DEFAULT 'SUBTREE',
    assigned_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_po_scope CHECK (scope IN ('WORK_ITEM', 'PROCESS', 'SUBTREE')),
    -- Must point at exactly one of the two targets; a row anchored to neither would be
    -- unreachable, and one anchored to both would have two conflicting scopes.
    CONSTRAINT ck_po_target CHECK (
        (work_item_id IS NOT NULL AND process_definition_id IS NULL) OR
        (work_item_id IS NULL AND process_definition_id IS NOT NULL))
);

CREATE UNIQUE INDEX uq_po_work_item ON process_owners (work_item_id, user_id)
    WHERE work_item_id IS NOT NULL;
CREATE UNIQUE INDEX uq_po_process ON process_owners (process_definition_id, user_id)
    WHERE process_definition_id IS NOT NULL;
CREATE INDEX ix_po_user ON process_owners (user_id);
