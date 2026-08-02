-- V7: the Work Item table.
--
-- One table for tickets, incidents, epics, features, stories, bugs, tasks and subtasks
-- (ADR-001). The alternative - separate tables - makes the typed link
-- "Ticket RESOLVED_BY Bug" impossible to constrain in PostgreSQL, and duplicates the
-- analysis, decomposition, ranking and sync machinery per entity type.
--
-- Note what is absent: there is no ticket_id column (ADR-002). A task may originate from
-- eight different sources; ticket linkage, where it exists, is a typed row in
-- work_item_links.

-- Reference keys are generated locally and are independent of any Jira key, so the
-- platform stays fully usable before, during and after a Jira outage (ADR-020).
-- Replaces project_key_counters from V5: tickets have no project, so the counter must be
-- keyed by prefix rather than by project.
DROP TABLE IF EXISTS project_key_counters;

CREATE TABLE reference_key_counters (
    organization_id UUID        NOT NULL REFERENCES organizations (id),
    prefix          VARCHAR(10) NOT NULL,
    next_value      BIGINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (organization_id, prefix)
);

CREATE TABLE work_items (
    id                     UUID PRIMARY KEY,
    organization_id        UUID          NOT NULL REFERENCES organizations (id),
    reference_key          VARCHAR(32)   NOT NULL,

    -- All nullable on purpose. A task may hang off a project, an epic, a sprint or
    -- nothing at all; requiring any of them would break six of the eight origin types.
    project_id             UUID          NULL REFERENCES projects (id),
    sprint_id              UUID          NULL REFERENCES sprints (id),
    parent_id              UUID          NULL REFERENCES work_items (id),

    work_item_type         VARCHAR(24)   NOT NULL,
    work_family            VARCHAR(32)   NOT NULL,
    origin_type            VARCHAR(24)   NOT NULL,

    workflow_definition_id UUID          NOT NULL REFERENCES workflow_definitions (id),
    status                 VARCHAR(64)   NOT NULL,

    summary                VARCHAR(255)  NOT NULL,
    description            TEXT          NULL,
    acceptance_criteria    TEXT          NULL,

    priority               VARCHAR(16)   NOT NULL DEFAULT 'MEDIUM',
    priority_score         SMALLINT      NULL,
    risk_level             VARCHAR(16)   NOT NULL DEFAULT 'LOW',
    complexity             VARCHAR(16)   NULL,

    department_id          UUID          NULL REFERENCES departments (id),
    team_id                UUID          NULL REFERENCES teams (id),
    assignee_id            UUID          NULL REFERENCES users (id),
    reporter_id            UUID          NOT NULL REFERENCES users (id),
    process_owner_id       UUID          NULL REFERENCES users (id),
    reviewer_id            UUID          NULL REFERENCES users (id),

    estimated_hours        NUMERIC(6, 2) NULL,
    remaining_hours        NUMERIC(6, 2) NULL,
    story_points           SMALLINT      NULL,
    due_date               DATE          NULL,

    ai_confidence          NUMERIC(3, 2) NULL,

    -- Sensitivity signals. Inputs to the policy engine in Phase 6; never decisions.
    security_sensitive     BOOLEAN       NOT NULL DEFAULT FALSE,
    production_impacting   BOOLEAN       NOT NULL DEFAULT FALSE,
    budget_sensitive       BOOLEAN       NOT NULL DEFAULT FALSE,
    compliance_sensitive   BOOLEAN       NOT NULL DEFAULT FALSE,

    closure_type           VARCHAR(32)   NOT NULL DEFAULT 'AUTO_CLOSE',
    progress_percent       NUMERIC(5, 2) NOT NULL DEFAULT 0,

    blocked_since          TIMESTAMPTZ   NULL,
    block_reason           TEXT          NULL,
    resolved_at            TIMESTAMPTZ   NULL,
    closed_at              TIMESTAMPTZ   NULL,

    jira_issue_id          VARCHAR(64)   NULL,
    jira_issue_key         VARCHAR(64)   NULL,
    jira_updated_at        TIMESTAMPTZ   NULL,

    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ   NULL,
    version                BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT uq_work_items_reference UNIQUE (organization_id, reference_key),

    -- The database refuses a status that does not exist in this item's workflow profile.
    -- Possible because workflow_states is unique on (workflow_definition_id, code); it is
    -- also why R__seed_workflow_definitions upserts states rather than replacing them.
    CONSTRAINT fk_work_items_status
        FOREIGN KEY (workflow_definition_id, status)
        REFERENCES workflow_states (workflow_definition_id, code),

    CONSTRAINT ck_wi_type CHECK (work_item_type IN (
        'REQUEST', 'INCIDENT', 'EPIC', 'FEATURE', 'STORY', 'BUG', 'TASK',
        'TECHNICAL_DEBT', 'RESEARCH', 'TEST', 'DOCUMENTATION', 'SUBTASK')),
    CONSTRAINT ck_wi_family CHECK (work_family IN (
        'SERVICE_WORK', 'PROCESS_WORK', 'DEVELOPMENT_WORK', 'INCIDENT_WORK',
        'GENERAL_OPERATIONAL_WORK')),
    CONSTRAINT ck_wi_origin CHECK (origin_type IN (
        'USER_CREATED', 'JIRA_IMPORTED', 'TICKET_GENERATED', 'AI_GENERATED',
        'PROCESS_TEMPLATE', 'PROJECT_PLANNING', 'INCIDENT_GENERATED', 'RECURRING')),
    CONSTRAINT ck_wi_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_wi_risk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_wi_complexity CHECK (complexity IS NULL OR complexity IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_wi_closure CHECK (closure_type IN (
        'AUTO_CLOSE', 'REQUESTER_VERIFICATION', 'PROCESS_OWNER_VERIFICATION',
        'TEAM_LEAD_VERIFICATION')),

    CONSTRAINT ck_wi_points CHECK (story_points IS NULL OR story_points IN (1, 2, 3, 5, 8, 13, 21)),
    CONSTRAINT ck_wi_hours CHECK (estimated_hours IS NULL OR (estimated_hours > 0 AND estimated_hours <= 200)),
    CONSTRAINT ck_wi_remaining CHECK (remaining_hours IS NULL OR remaining_hours >= 0),
    CONSTRAINT ck_wi_confidence CHECK (ai_confidence IS NULL OR (ai_confidence >= 0 AND ai_confidence <= 1)),
    CONSTRAINT ck_wi_priority_score CHECK (priority_score IS NULL OR priority_score BETWEEN 0 AND 100),
    CONSTRAINT ck_wi_progress CHECK (progress_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_wi_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id),

    -- Structural rules that hold for every origin type. Conditional rules that depend on
    -- workflow state (acceptance criteria before READY, for instance) live in
    -- WorkItemValidator, which a CHECK constraint cannot express.
    CONSTRAINT ck_wi_parent_rules CHECK (
        (work_item_type = 'SUBTASK' AND parent_id IS NOT NULL)
        OR (work_item_type IN ('REQUEST', 'INCIDENT', 'EPIC') AND parent_id IS NULL)
        OR (work_item_type NOT IN ('SUBTASK', 'REQUEST', 'INCIDENT', 'EPIC'))),
    CONSTRAINT ck_wi_points_by_type CHECK (
        story_points IS NULL
        OR work_item_type IN ('EPIC', 'FEATURE', 'STORY', 'BUG', 'TECHNICAL_DEBT')),
    CONSTRAINT ck_wi_block_reason CHECK (
        blocked_since IS NULL OR block_reason IS NOT NULL)
);

CREATE INDEX ix_wi_org_status        ON work_items (organization_id, status) WHERE deleted_at IS NULL;
CREATE INDEX ix_wi_assignee_active   ON work_items (assignee_id, status)     WHERE deleted_at IS NULL;
CREATE INDEX ix_wi_team_status       ON work_items (team_id, status)         WHERE deleted_at IS NULL;
CREATE INDEX ix_wi_department_status ON work_items (department_id, status)   WHERE deleted_at IS NULL;
CREATE INDEX ix_wi_sprint            ON work_items (sprint_id)               WHERE sprint_id IS NOT NULL;
CREATE INDEX ix_wi_parent            ON work_items (parent_id)               WHERE parent_id IS NOT NULL;
CREATE INDEX ix_wi_project_type      ON work_items (project_id, work_item_type);
CREATE INDEX ix_wi_reporter          ON work_items (reporter_id);
CREATE INDEX ix_wi_process_owner     ON work_items (process_owner_id)        WHERE process_owner_id IS NOT NULL;
CREATE INDEX ix_wi_due_open          ON work_items (due_date)                WHERE due_date IS NOT NULL;
CREATE INDEX ix_wi_jira_key          ON work_items (jira_issue_key)          WHERE jira_issue_key IS NOT NULL;
CREATE INDEX ix_wi_blocked           ON work_items (organization_id, blocked_since) WHERE blocked_since IS NOT NULL;

-- Duplicate detection is trigram similarity, not embeddings (ADR-025): no extra
-- infrastructure, and it catches the "printer broken again" class that dominates.
CREATE INDEX ix_wi_summary_trgm ON work_items USING gin (summary gin_trgm_ops);

CREATE TRIGGER trg_work_items_updated_at
    BEFORE UPDATE ON work_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
