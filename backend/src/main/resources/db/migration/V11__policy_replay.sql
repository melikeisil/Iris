-- V11: what a routing decision needs to be replayable, and how an escalation is linked.
--
-- docs/08 §8 promises a historical routing decision can be replayed "against the policy
-- that was actually in force". Storing only the route and its reason codes does not
-- deliver that: it records the answer, not the question. Reproducing the decision needs
-- the thresholds and weights as they read at the time, which means pinning the policy row
-- *and* its version, since assignment_policies is mutable and versioned.

ALTER TABLE recommendations
    ADD COLUMN assignment_policy_id      UUID   NULL REFERENCES assignment_policies (id),
    ADD COLUMN assignment_policy_version BIGINT NULL,
    -- The engine's own version, bumped when a rule changes rather than a threshold.
    -- A decision replayed under different *code* is not the same decision, and without
    -- this column that difference would be invisible.
    ADD COLUMN policy_engine_version     SMALLINT NOT NULL DEFAULT 1;

COMMENT ON COLUMN recommendations.assignment_policy_id IS
    'The policy row in force at decision time. Null means built-in defaults were used.';
COMMENT ON COLUMN recommendations.policy_engine_version IS
    'PolicyEngine.VERSION at decision time. Replay across versions is not equivalence.';

-- Escalation: docs/06 exposes /team-lead/.../escalate and /manager/escalations, and
-- docs/08 §5.4 makes "Team Lead escalation" a MANAGER_APPROVAL trigger. An escalation is
-- therefore a *new* request at a higher route, not a mutation of the old one - the
-- original must stay readable with its own reason codes so the chain is auditable.
ALTER TABLE approval_requests
    ADD COLUMN escalated_from_id UUID NULL REFERENCES approval_requests (id),
    ADD COLUMN escalation_reason TEXT NULL;

-- An escalation must say why. "Escalated" with no reason is the least useful row in an
-- audit trail: it records that someone passed the decision upward and nothing about the
-- judgement they were exercising.
ALTER TABLE approval_requests
    ADD CONSTRAINT ck_ar_escalation_has_reason
        CHECK (escalated_from_id IS NULL OR escalation_reason IS NOT NULL);

ALTER TABLE approval_requests
    ADD CONSTRAINT ck_ar_not_self_escalated
        CHECK (escalated_from_id IS NULL OR escalated_from_id <> id);

CREATE INDEX ix_ar_escalated_from ON approval_requests (escalated_from_id)
    WHERE escalated_from_id IS NOT NULL;

-- The review-queue queries filter on (organization_id, status, route) and order by
-- created_at. ix_ar_queue from V10 covers the filter; this covers the sort so a Team Lead
-- opening their queue does not sort the whole open set in memory.
CREATE INDEX ix_ar_queue_ordered ON approval_requests (organization_id, route, created_at DESC)
    WHERE status = 'OPEN';

-- Scope addressing is 'TEAM:<uuid>' / 'DEPT:<uuid>' / 'ROLE:MANAGER' (docs/08 §5.5).
-- Constrained here so a typo cannot silently create a request nobody can ever see:
-- an unmatched scope string is invisible to every queue query, and an approval request
-- that reaches no queue is indistinguishable from work that quietly stopped.
ALTER TABLE approval_requests
    ADD CONSTRAINT ck_ar_scope_format
        CHECK (assigned_to_scope IS NULL
               OR assigned_to_scope ~ '^(TEAM|DEPT):[0-9a-fA-F-]{36}$'
               OR assigned_to_scope IN ('ROLE:MANAGER', 'ROLE:ADMIN'));
