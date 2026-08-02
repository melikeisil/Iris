-- V6: configurable workflow profiles.
-- Workflows are data, not code. Adding a team-specific profile is an admin action,
-- not a deployment. Rows with organization_id IS NULL are system defaults available
-- to every tenant; an organization may override a code with its own row.

CREATE TABLE workflow_definitions (
    id                 UUID PRIMARY KEY,
    organization_id    UUID         NULL REFERENCES organizations (id),
    code               VARCHAR(64)  NOT NULL,
    name               VARCHAR(255) NOT NULL,
    applies_to_family  VARCHAR(32)  NOT NULL,
    initial_state      VARCHAR(64)  NOT NULL,
    is_default         BOOLEAN      NOT NULL DEFAULT FALSE,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_wfd_family CHECK (applies_to_family IN
        ('SERVICE_WORK', 'PROCESS_WORK', 'DEVELOPMENT_WORK', 'INCIDENT_WORK', 'GENERAL_OPERATIONAL_WORK'))
);

-- One system default per code, and one override per organization per code.
CREATE UNIQUE INDEX uq_wfd_system_code ON workflow_definitions (code) WHERE organization_id IS NULL;
CREATE UNIQUE INDEX uq_wfd_org_code ON workflow_definitions (organization_id, code) WHERE organization_id IS NOT NULL;

CREATE TRIGGER trg_workflow_definitions_updated_at
    BEFORE UPDATE ON workflow_definitions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE projects
    ADD CONSTRAINT fk_projects_workflow
        FOREIGN KEY (default_workflow_definition_id) REFERENCES workflow_definitions (id);

CREATE TABLE workflow_states (
    id                      UUID PRIMARY KEY,
    workflow_definition_id  UUID         NOT NULL REFERENCES workflow_definitions (id) ON DELETE CASCADE,
    code                    VARCHAR(64)  NOT NULL,
    name                    VARCHAR(128) NOT NULL,
    category                VARCHAR(16)  NOT NULL,
    is_initial              BOOLEAN      NOT NULL DEFAULT FALSE,
    is_terminal             BOOLEAN      NOT NULL DEFAULT FALSE,
    is_terminal_success     BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active_work          BOOLEAN      NOT NULL DEFAULT FALSE,
    is_blocked              BOOLEAN      NOT NULL DEFAULT FALSE,
    is_waiting_external     BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order           SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uq_workflow_states UNIQUE (workflow_definition_id, code),
    CONSTRAINT ck_wfs_category CHECK (category IN
        ('TODO', 'IN_PROGRESS', 'BLOCKED', 'REVIEW', 'TESTING', 'DONE'))
);

CREATE INDEX ix_workflow_states_definition ON workflow_states (workflow_definition_id);

-- Exactly one initial state per definition.
CREATE UNIQUE INDEX uq_workflow_states_initial
    ON workflow_states (workflow_definition_id) WHERE is_initial;

CREATE TABLE workflow_transitions (
    id                      UUID PRIMARY KEY,
    workflow_definition_id  UUID         NOT NULL REFERENCES workflow_definitions (id) ON DELETE CASCADE,
    transition_code         VARCHAR(64)  NOT NULL,
    label                   VARCHAR(128) NOT NULL,
    from_state              VARCHAR(64)  NOT NULL,
    to_state                VARCHAR(64)  NOT NULL,
    allowed_roles           VARCHAR(255) NOT NULL DEFAULT '',
    allowed_relationships   VARCHAR(255) NOT NULL DEFAULT '',
    guards                  VARCHAR(511) NOT NULL DEFAULT '',
    requires_reason         BOOLEAN      NOT NULL DEFAULT FALSE,
    automatic               BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order           SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uq_workflow_transitions UNIQUE (workflow_definition_id, transition_code, from_state),
    CONSTRAINT ck_wft_not_self CHECK (from_state <> to_state OR automatic = FALSE)
);

CREATE INDEX ix_workflow_transitions_definition ON workflow_transitions (workflow_definition_id);
CREATE INDEX ix_workflow_transitions_from ON workflow_transitions (workflow_definition_id, from_state);
