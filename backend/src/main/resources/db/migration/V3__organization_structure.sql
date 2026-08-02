-- V3: departments, teams, memberships.
-- A user belongs to exactly one department (assumption A2) but may join several teams.

CREATE TABLE departments (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id),
    code             VARCHAR(64)  NOT NULL,
    name             VARCHAR(255) NOT NULL,
    lead_user_id     UUID         NULL REFERENCES users (id),
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_departments_org_code UNIQUE (organization_id, code)
);

CREATE INDEX ix_departments_organization ON departments (organization_id);

CREATE TRIGGER trg_departments_updated_at
    BEFORE UPDATE ON departments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Deferred from V2: users.department_id could not reference a table that did not exist yet.
ALTER TABLE users
    ADD CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES departments (id);

CREATE TABLE teams (
    id                     UUID PRIMARY KEY,
    organization_id        UUID          NOT NULL REFERENCES organizations (id),
    department_id          UUID          NOT NULL REFERENCES departments (id),
    code                   VARCHAR(64)   NOT NULL,
    name                   VARCHAR(255)  NOT NULL,
    lead_user_id           UUID          NULL REFERENCES users (id),
    weekly_capacity_hours  NUMERIC(7, 2) NULL,
    active                 BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ   NULL,
    CONSTRAINT uq_teams_org_code UNIQUE (organization_id, code)
);

CREATE INDEX ix_teams_organization ON teams (organization_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_teams_department ON teams (department_id) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_teams_updated_at
    BEFORE UPDATE ON teams
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE team_members (
    id                  UUID PRIMARY KEY,
    team_id             UUID          NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    user_id             UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    allocation_percent  SMALLINT      NOT NULL DEFAULT 100,
    primary_team        BOOLEAN       NOT NULL DEFAULT TRUE,
    joined_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_team_members UNIQUE (team_id, user_id),
    CONSTRAINT ck_team_members_allocation CHECK (allocation_percent BETWEEN 1 AND 100)
);

CREATE INDEX ix_team_members_user ON team_members (user_id);
CREATE INDEX ix_team_members_team ON team_members (team_id);

-- A user may sit in several teams but only one of them is their primary team,
-- which is what department/team scoring and workload attribution use.
CREATE UNIQUE INDEX uq_team_members_primary ON team_members (user_id) WHERE primary_team;
