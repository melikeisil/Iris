-- V5: projects, components, sprints.
-- Components exist because the assignment scorer awards 10% for component experience.

CREATE TABLE projects (
    id                             UUID PRIMARY KEY,
    organization_id                UUID         NOT NULL REFERENCES organizations (id),
    department_id                  UUID         NULL REFERENCES departments (id),
    code                           VARCHAR(64)  NOT NULL,
    name                           VARCHAR(255) NOT NULL,
    key_prefix                     VARCHAR(10)  NOT NULL,
    status                         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    default_workflow_definition_id UUID         NULL,   -- FK added in V6
    created_at                     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at                     TIMESTAMPTZ  NULL,
    version                        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_projects_org_code UNIQUE (organization_id, code),
    CONSTRAINT uq_projects_org_prefix UNIQUE (organization_id, key_prefix),
    CONSTRAINT ck_projects_status CHECK (status IN ('PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED')),
    CONSTRAINT ck_projects_prefix_format CHECK (key_prefix ~ '^[A-Z][A-Z0-9]{1,9}$')
);

CREATE INDEX ix_projects_organization ON projects (organization_id) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_projects_updated_at
    BEFORE UPDATE ON projects
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Per-project sequence backing reference_key generation (DEV-1, DEV-2, ...).
-- Kept as a table row rather than a PG sequence so it is tenant-scoped and dumpable.
CREATE TABLE project_key_counters (
    project_id  UUID PRIMARY KEY REFERENCES projects (id) ON DELETE CASCADE,
    next_value  BIGINT NOT NULL DEFAULT 1
);

CREATE TABLE project_components (
    id          UUID PRIMARY KEY,
    project_id  UUID         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_components UNIQUE (project_id, code)
);

CREATE INDEX ix_project_components_project ON project_components (project_id);

CREATE TABLE sprints (
    id               UUID PRIMARY KEY,
    organization_id  UUID          NOT NULL REFERENCES organizations (id),
    project_id       UUID          NULL REFERENCES projects (id),
    team_id          UUID          NULL REFERENCES teams (id),
    name             VARCHAR(255)  NOT NULL,
    goal             TEXT          NULL,
    start_date       DATE          NOT NULL,
    end_date         DATE          NOT NULL,
    status           VARCHAR(16)   NOT NULL DEFAULT 'PLANNED',
    capacity_hours   NUMERIC(8, 2) NULL,
    jira_sprint_id   VARCHAR(64)   NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_sprints_status CHECK (status IN ('PLANNED', 'ACTIVE', 'CLOSED')),
    CONSTRAINT ck_sprints_dates CHECK (end_date > start_date)
);

CREATE INDEX ix_sprints_organization ON sprints (organization_id);
CREATE INDEX ix_sprints_team_status ON sprints (team_id, status);

-- At most one ACTIVE sprint per team: the workload denominator assumes a single window.
CREATE UNIQUE INDEX uq_sprints_active_per_team
    ON sprints (team_id) WHERE status = 'ACTIVE' AND team_id IS NOT NULL;

CREATE TRIGGER trg_sprints_updated_at
    BEFORE UPDATE ON sprints
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
