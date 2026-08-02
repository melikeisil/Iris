-- V17: Manager-triggered weekly report email flow.
--
-- Three tables, all owned by the new `report` module:
--
-- `weekly_reports` is the generated artifact itself - the computed metrics, the AI
-- summary, who generated/sent it, the recipient list (editable up to the moment of
-- send) and the delivery outcome. It IS the report history the Manager dashboard reads.
--
-- `report_outbox_events` is the first concrete table for the outbox pattern ADR-006
-- already commits to (`outbox_events` drained by a `@Scheduled` relay using
-- `FOR UPDATE SKIP LOCKED`) - scoped to this feature for now rather than generalized
-- across the platform, since report email is the first thing that needs reliable,
-- retried, at-least-once delivery outside the request/response cycle.
--
-- `report_audit_log` is a minimal, append-only audit trail for the three report actions
-- the brief calls for (generate, preview, send). Like the outbox, this is the first
-- concrete audit table; a platform-wide audit trail is future work.

CREATE TABLE weekly_reports (
    id                      UUID          PRIMARY KEY,
    organization_id         UUID          NOT NULL REFERENCES organizations (id),
    -- NULL means an organization-wide report. Set when an org-scoped Manager (not
    -- organization_wide) generates one - see AuthPrincipal.organizationWide().
    department_id           UUID          NULL REFERENCES departments (id),

    period_start            DATE          NOT NULL,
    period_end              DATE          NOT NULL,

    generated_by            UUID          NOT NULL REFERENCES users (id),
    generated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- The full computed report: status counts, completed-this-week with who completed
    -- each one, active-by-employee, blocked-with-reason, overdue/SLA risk, department
    -- workload/capacity, parent ticket progress, reassignments, approval changes. Shaped
    -- by ReportMetrics (report.application) and never hand-edited.
    metrics                 JSONB         NOT NULL,

    -- The AI's management summary. Persisted separately from metrics because it is
    -- prose, not data, and because ai_provider records whether it is a real model call
    -- or the deterministic persisted-metrics-only fallback (see ReportAiClient).
    ai_summary               TEXT         NULL,
    ai_recommended_actions   JSONB        NULL,
    ai_provider              VARCHAR(32)  NULL,

    -- The recipient list as of generation (defaults to the org's managers/admins) and
    -- as of send (what the Manager actually edited it to before clicking Send).
    default_recipients      JSONB         NOT NULL DEFAULT '[]',
    recipients               JSONB        NULL,

    status                  VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    sender_user_id           UUID         NULL REFERENCES users (id),
    email_status             VARCHAR(16)  NOT NULL DEFAULT 'NOT_SENT',
    email_failure_reason     TEXT         NULL,
    sent_at                  TIMESTAMPTZ  NULL,
    outbox_event_id          UUID         NULL,

    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                  BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_wr_period CHECK (period_end > period_start),
    CONSTRAINT ck_wr_status CHECK (status IN ('DRAFT', 'SENDING', 'SENT', 'SEND_FAILED')),
    CONSTRAINT ck_wr_email_status CHECK (email_status IN ('NOT_SENT', 'PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX ix_wr_org_period ON weekly_reports (organization_id, period_start DESC);
CREATE INDEX ix_wr_generated_by ON weekly_reports (generated_by);

CREATE TRIGGER trg_weekly_reports_updated_at
    BEFORE UPDATE ON weekly_reports
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE report_outbox_events (
    id                 UUID          PRIMARY KEY,
    organization_id    UUID          NOT NULL REFERENCES organizations (id),
    aggregate_type     VARCHAR(32)   NOT NULL,
    aggregate_id       UUID          NOT NULL,
    event_type         VARCHAR(32)   NOT NULL,
    payload            JSONB         NOT NULL,
    status             VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    attempts           INT           NOT NULL DEFAULT 0,
    max_attempts       INT           NOT NULL DEFAULT 5,
    last_error         TEXT          NULL,
    -- The relay only picks up rows due now, so a failed attempt's backoff is just
    -- pushing this forward rather than a separate scheduler concept.
    available_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_roe_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'DEAD_LETTER')),
    -- One outbox event per (report, event type): the send endpoint is the only writer,
    -- and this is what makes a duplicate send request a no-op at the database level
    -- rather than something only the application layer promises to prevent.
    CONSTRAINT uq_roe_aggregate_event UNIQUE (aggregate_type, aggregate_id, event_type)
);

CREATE INDEX ix_roe_relay ON report_outbox_events (status, available_at) WHERE status = 'PENDING';

CREATE TRIGGER trg_report_outbox_events_updated_at
    BEFORE UPDATE ON report_outbox_events
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE weekly_reports
    ADD CONSTRAINT fk_wr_outbox_event FOREIGN KEY (outbox_event_id) REFERENCES report_outbox_events (id);

CREATE TABLE report_audit_log (
    id               UUID          PRIMARY KEY,
    organization_id  UUID          NOT NULL REFERENCES organizations (id),
    actor_user_id    UUID          NOT NULL REFERENCES users (id),
    action           VARCHAR(32)   NOT NULL,
    report_id        UUID          NULL REFERENCES weekly_reports (id),
    detail           JSONB         NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_ral_action CHECK (action IN
        ('REPORT_GENERATED', 'REPORT_PREVIEWED', 'REPORT_SEND_REQUESTED',
         'REPORT_SENT', 'REPORT_SEND_FAILED'))
);

CREATE INDEX ix_ral_org_time ON report_audit_log (organization_id, created_at DESC);
CREATE INDEX ix_ral_report ON report_audit_log (report_id) WHERE report_id IS NOT NULL;
