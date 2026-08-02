-- V2: users, system roles, refresh tokens.
-- Deliberately absent: any column describing a protected personal characteristic.
-- ProtectedCharacteristicsSchemaTest fails the build if one is ever added.

CREATE TABLE roles (
    id          UUID PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_roles_code UNIQUE (code),
    CONSTRAINT ck_roles_code CHECK (code IN ('EMPLOYEE', 'TEAM_LEAD', 'MANAGER', 'ADMIN'))
);

CREATE TABLE users (
    id                     UUID PRIMARY KEY,
    organization_id        UUID         NOT NULL REFERENCES organizations (id),
    department_id          UUID         NULL,        -- FK added in V3 (departments not yet created)
    email                  VARCHAR(255) NOT NULL,
    password_hash          VARCHAR(255) NOT NULL,
    full_name              VARCHAR(255) NOT NULL,
    employee_level         VARCHAR(16)  NOT NULL DEFAULT 'MID',
    weekly_capacity_hours  NUMERIC(5, 2) NOT NULL DEFAULT 40.00,
    organization_wide      BOOLEAN      NOT NULL DEFAULT FALSE,
    active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at          TIMESTAMPTZ  NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ  NULL,
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_users_level CHECK (employee_level IN ('INTERN', 'JUNIOR', 'MID', 'SENIOR', 'LEAD')),
    CONSTRAINT ck_users_capacity CHECK (weekly_capacity_hours > 0 AND weekly_capacity_hours <= 80)
);

-- Email is unique per organization, not globally: the same person may exist in two tenants.
CREATE UNIQUE INDEX uq_users_org_email ON users (organization_id, lower(email)) WHERE deleted_at IS NULL;
CREATE INDEX ix_users_organization ON users (organization_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_users_department ON users (department_id) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE user_roles (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id     UUID NOT NULL REFERENCES roles (id),
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by  UUID NULL REFERENCES users (id),
    CONSTRAINT uq_user_roles UNIQUE (user_id, role_id)
);

CREATE INDEX ix_user_roles_user ON user_roles (user_id);

-- Refresh tokens are stored hashed and are single-use. Presenting a consumed token
-- revokes the whole family (see AuthService.refresh) - this is the theft signal.
CREATE TABLE refresh_tokens (
    id            UUID PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id     UUID        NOT NULL,
    token_hash    VARCHAR(64) NOT NULL,
    issued_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL,
    consumed_at   TIMESTAMPTZ NULL,
    revoked_at    TIMESTAMPTZ NULL,
    revoked_reason VARCHAR(64) NULL,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_expiry ON refresh_tokens (expires_at) WHERE revoked_at IS NULL;
