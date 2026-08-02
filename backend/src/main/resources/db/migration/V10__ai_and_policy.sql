-- V10: AI recommendations, candidate ranking, assignment/level policies, approvals.
--
-- Phase 5 populates recommendations, recommendation_candidates, assignment_policies
-- (weights only) and employee_level_policies. The routing columns and the
-- approval_requests / assignments tables are created here because they belong to the
-- same logical schema, and are written to in Phase 6.

CREATE TABLE recommendations (
    id                    UUID          PRIMARY KEY,
    organization_id       UUID          NOT NULL REFERENCES organizations (id),
    work_item_id          UUID          NOT NULL REFERENCES work_items (id) ON DELETE CASCADE,
    recommendation_type   VARCHAR(16)   NOT NULL,
    status                VARCHAR(24)   NOT NULL DEFAULT 'PENDING',

    analysis_payload      JSONB         NULL,
    suggested_plan        JSONB         NULL,
    confidence            NUMERIC(3, 2) NULL,

    routing_decision      VARCHAR(32)   NULL,
    routing_reason_codes  VARCHAR(512)  NULL,

    llm_provider          VARCHAR(32)   NULL,
    llm_model             VARCHAR(64)   NULL,
    llm_latency_ms        INTEGER       NULL,
    prompt_version        VARCHAR(16)   NULL,
    is_mock               BOOLEAN       NOT NULL DEFAULT FALSE,

    -- The work item's updated_at at the moment of generation. A recommendation whose
    -- source has since changed is STALE: applying it would silently overwrite edits the
    -- reviewer never saw. This column is what makes that detectable rather than a race.
    source_updated_at     TIMESTAMPTZ   NULL,
    superseded_by         UUID          NULL REFERENCES recommendations (id),

    correlation_id        VARCHAR(64)   NULL,
    error_detail          TEXT          NULL,

    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version               BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ck_rec_type CHECK (recommendation_type IN ('ANALYSIS', 'PLAN', 'ASSIGNMENT')),
    CONSTRAINT ck_rec_status CHECK (status IN
        ('PENDING', 'AWAITING_APPROVAL', 'APPROVED', 'REJECTED', 'APPLIED',
         'STALE', 'SUPERSEDED', 'FAILED')),
    CONSTRAINT ck_rec_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    CONSTRAINT ck_rec_not_self_superseded CHECK (superseded_by IS NULL OR superseded_by <> id),
    -- A FAILED recommendation must say why. Silent failures are how an analysis pipeline
    -- becomes unexplainable six months later.
    CONSTRAINT ck_rec_failed_has_detail CHECK (status <> 'FAILED' OR error_detail IS NOT NULL)
);

CREATE INDEX ix_rec_work_item ON recommendations (work_item_id, created_at DESC);
CREATE INDEX ix_rec_status    ON recommendations (organization_id, status);

CREATE TRIGGER trg_recommendations_updated_at
    BEFORE UPDATE ON recommendations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Every candidate considered, including the ones ruled out. Storing the exclusions is
-- what makes "why was the obvious person skipped?" answerable in the UI (docs/08 §3).
CREATE TABLE recommendation_candidates (
    id                 UUID         PRIMARY KEY,
    recommendation_id  UUID         NOT NULL REFERENCES recommendations (id) ON DELETE CASCADE,
    candidate_user_id  UUID         NOT NULL REFERENCES users (id),

    rank               SMALLINT     NULL,
    total_score        SMALLINT     NULL,
    skill_score        SMALLINT     NULL,
    team_score         SMALLINT     NULL,
    capacity_score     SMALLINT     NULL,
    experience_score   SMALLINT     NULL,
    component_score    SMALLINT     NULL,
    deadline_score     SMALLINT     NULL,
    fairness_score     SMALLINT     NULL,

    workload_ratio     NUMERIC(4, 2) NULL,
    excluded           BOOLEAN      NOT NULL DEFAULT FALSE,
    exclusion_reason   VARCHAR(64)  NULL,
    explanation        TEXT         NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_rc_candidate UNIQUE (recommendation_id, candidate_user_id),
    CONSTRAINT ck_rc_rank CHECK (rank IS NULL OR rank BETWEEN 1 AND 3),

    -- An excluded candidate has a reason and no rank; a ranked one has scores and no
    -- reason. Enforcing the pairing here means the UI never has to handle a half-state.
    CONSTRAINT ck_rc_exclusion_pairing CHECK (
        (excluded AND exclusion_reason IS NOT NULL AND rank IS NULL) OR
        (NOT excluded AND exclusion_reason IS NULL AND total_score IS NOT NULL)),

    CONSTRAINT ck_rc_scores CHECK (
        (total_score      IS NULL OR total_score      BETWEEN 0 AND 100) AND
        (skill_score      IS NULL OR skill_score      BETWEEN 0 AND 100) AND
        (team_score       IS NULL OR team_score       BETWEEN 0 AND 100) AND
        (capacity_score   IS NULL OR capacity_score   BETWEEN 0 AND 100) AND
        (experience_score IS NULL OR experience_score BETWEEN 0 AND 100) AND
        (component_score  IS NULL OR component_score  BETWEEN 0 AND 100) AND
        (deadline_score   IS NULL OR deadline_score   BETWEEN 0 AND 100) AND
        (fairness_score   IS NULL OR fairness_score   BETWEEN 0 AND 100))
);

CREATE INDEX ix_rc_recommendation ON recommendation_candidates (recommendation_id, rank);
CREATE INDEX ix_rc_user ON recommendation_candidates (candidate_user_id);

-- Routing thresholds and score weights. Resolved most-specific-first:
-- team -> department -> organization -> built-in Java defaults (docs/08 §8).
CREATE TABLE assignment_policies (
    id                          UUID          PRIMARY KEY,
    organization_id             UUID          NOT NULL REFERENCES organizations (id),
    department_id               UUID          NULL REFERENCES departments (id),
    team_id                     UUID          NULL REFERENCES teams (id),
    name                        VARCHAR(255)  NOT NULL,
    priority                    SMALLINT      NOT NULL DEFAULT 100,

    min_auto_assign_confidence  NUMERIC(3, 2) NOT NULL DEFAULT 0.85,
    max_auto_assign_workload    NUMERIC(3, 2) NOT NULL DEFAULT 0.80,
    max_auto_assign_hours       SMALLINT      NOT NULL DEFAULT 8,
    auto_assign_max_risk        VARCHAR(16)   NOT NULL DEFAULT 'LOW',
    min_auto_assign_score       SMALLINT      NOT NULL DEFAULT 60,
    min_score_gap               SMALLINT      NOT NULL DEFAULT 5,
    allow_cross_department_auto BOOLEAN       NOT NULL DEFAULT FALSE,

    weight_overrides            JSONB         NULL,
    route_rules                 JSONB         NULL,

    active                      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                     BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ck_ap_risk CHECK (auto_assign_max_risk IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_ap_confidence CHECK (min_auto_assign_confidence BETWEEN 0 AND 1),
    CONSTRAINT ck_ap_workload CHECK (max_auto_assign_workload > 0),
    CONSTRAINT ck_ap_score CHECK (min_auto_assign_score BETWEEN 0 AND 100),
    CONSTRAINT ck_ap_gap CHECK (min_score_gap >= 0),
    -- A team-scoped policy must also name the department it sits under, so resolution
    -- can walk the specificity ladder without a join back to teams.
    CONSTRAINT ck_ap_scope CHECK (team_id IS NULL OR department_id IS NOT NULL)
);

-- At most one active policy per exact scope; `priority` breaks ties only within a tier.
CREATE UNIQUE INDEX uq_ap_org_scope ON assignment_policies (organization_id)
    WHERE active AND department_id IS NULL AND team_id IS NULL;
CREATE UNIQUE INDEX uq_ap_dept_scope ON assignment_policies (organization_id, department_id)
    WHERE active AND department_id IS NOT NULL AND team_id IS NULL;
CREATE UNIQUE INDEX uq_ap_team_scope ON assignment_policies (organization_id, team_id)
    WHERE active AND team_id IS NOT NULL;

CREATE TRIGGER trg_assignment_policies_updated_at
    BEFORE UPDATE ON assignment_policies
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Level restrictions. Enforced twice on purpose (docs/08 §7): in candidate filtering so
-- a restricted person never appears, and in the LEVEL_RESTRICTION transition guard so a
-- manual assignment is stopped too.
CREATE TABLE employee_level_policies (
    id                        UUID          PRIMARY KEY,
    organization_id           UUID          NOT NULL REFERENCES organizations (id),
    employee_level            VARCHAR(16)   NOT NULL,

    allow_production_access   BOOLEAN       NOT NULL DEFAULT TRUE,
    allow_financial_approval  BOOLEAN       NOT NULL DEFAULT TRUE,
    allow_security_sensitive  BOOLEAN       NOT NULL DEFAULT TRUE,
    max_priority              VARCHAR(16)   NOT NULL DEFAULT 'CRITICAL',
    max_active_work_items     SMALLINT      NULL,
    max_estimated_hours       NUMERIC(6, 2) NULL,
    require_mentor            BOOLEAN       NOT NULL DEFAULT FALSE,
    allowed_categories        VARCHAR(512)  NULL,   -- CSV; empty/null means all

    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                   BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT uq_elp_level UNIQUE (organization_id, employee_level),
    CONSTRAINT ck_elp_level CHECK (employee_level IN
        ('INTERN', 'JUNIOR', 'MID', 'SENIOR', 'LEAD')),
    CONSTRAINT ck_elp_priority CHECK (max_priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_elp_max_active CHECK (max_active_work_items IS NULL OR max_active_work_items > 0),
    CONSTRAINT ck_elp_max_hours CHECK (max_estimated_hours IS NULL OR max_estimated_hours > 0)
);

CREATE TRIGGER trg_employee_level_policies_updated_at
    BEFORE UPDATE ON employee_level_policies
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Created in Phase 5, written in Phase 6.
CREATE TABLE approval_requests (
    id                   UUID          PRIMARY KEY,
    organization_id      UUID          NOT NULL REFERENCES organizations (id),
    recommendation_id    UUID          NULL REFERENCES recommendations (id) ON DELETE CASCADE,
    work_item_id         UUID          NOT NULL REFERENCES work_items (id) ON DELETE CASCADE,
    route                VARCHAR(32)   NOT NULL,
    status               VARCHAR(16)   NOT NULL DEFAULT 'OPEN',

    assigned_to_user_id  UUID          NULL REFERENCES users (id),
    -- Addressed to a scope rather than a person wherever possible, so work does not
    -- stall behind one individual's absence (docs/08 §5.5).
    assigned_to_scope    VARCHAR(64)   NULL,

    reason_codes         VARCHAR(512)  NULL,
    policy_exception     BOOLEAN       NOT NULL DEFAULT FALSE,
    decided_by           UUID          NULL REFERENCES users (id),
    decision_note        TEXT          NULL,
    decided_at           TIMESTAMPTZ   NULL,
    due_at               TIMESTAMPTZ   NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_ar_route CHECK (route IN
        ('AUTO_ASSIGN', 'TEAM_LEAD_REVIEW', 'PROCESS_OWNER_REVIEW', 'MANAGER_APPROVAL',
         'REQUEST_MORE_INFORMATION', 'MANUAL_EXCEPTION_REVIEW')),
    CONSTRAINT ck_ar_status CHECK (status IN
        ('OPEN', 'APPROVED', 'REJECTED', 'ESCALATED', 'EXPIRED')),
    CONSTRAINT ck_ar_addressed CHECK (assigned_to_user_id IS NOT NULL OR assigned_to_scope IS NOT NULL),
    CONSTRAINT ck_ar_decided CHECK (
        (status IN ('OPEN', 'EXPIRED')) OR (decided_by IS NOT NULL AND decided_at IS NOT NULL))
);

-- One open request per work item and route; re-routing closes the previous one.
CREATE UNIQUE INDEX uq_ar_open ON approval_requests (work_item_id, route) WHERE status = 'OPEN';
CREATE INDEX ix_ar_queue ON approval_requests (organization_id, status, route);
CREATE INDEX ix_ar_assignee ON approval_requests (assigned_to_user_id) WHERE status = 'OPEN';

CREATE TRIGGER trg_approval_requests_updated_at
    BEFORE UPDATE ON approval_requests
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Assignment history, distinct from work_items.assignee_id (the current holder).
-- Needed by the fairness score, which counts assignments in the last 14 days - a
-- reassigned-away item still consumed that person's attention.
CREATE TABLE assignments (
    id                 UUID         PRIMARY KEY,
    organization_id    UUID         NOT NULL REFERENCES organizations (id),
    work_item_id       UUID         NOT NULL REFERENCES work_items (id) ON DELETE CASCADE,
    assignee_id        UUID         NOT NULL REFERENCES users (id),
    source             VARCHAR(16)  NOT NULL,
    recommendation_id  UUID         NULL REFERENCES recommendations (id),
    assigned_by        UUID         NULL REFERENCES users (id),
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    reason             TEXT         NULL,
    assigned_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ended_at           TIMESTAMPTZ  NULL,

    CONSTRAINT ck_as_source CHECK (source IN
        ('AUTO', 'TEAM_LEAD', 'PROCESS_OWNER', 'MANAGER', 'SELF')),
    CONSTRAINT ck_as_active_open CHECK ((active AND ended_at IS NULL) OR (NOT active))
);

-- At most one active assignment per item, mirroring the single assignee_id column.
CREATE UNIQUE INDEX uq_as_active ON assignments (work_item_id) WHERE active;
CREATE INDEX ix_as_fairness ON assignments (assignee_id, assigned_at DESC);
CREATE INDEX ix_as_work_item ON assignments (work_item_id, assigned_at DESC);
