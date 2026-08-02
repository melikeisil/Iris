-- V13: align approval scope constraint with the Phase 6 model.
--
-- REQUEST_MORE_INFORMATION is intentionally addressed to a specific person, not a role or
-- a shared queue. The Java model serialises that as USER:<uuid> and both ActorScopesTest
-- and ApprovalRequestTest rely on it. V11's check constraint accidentally permitted only
-- TEAM, DEPT and ROLE scopes, so the first real REQUEST_MORE_INFORMATION route failed at
-- insert time and surfaced as a generic 409.

ALTER TABLE approval_requests
    DROP CONSTRAINT IF EXISTS ck_ar_scope_format;

ALTER TABLE approval_requests
    ADD CONSTRAINT ck_ar_scope_format
        CHECK (assigned_to_scope IS NULL
               OR assigned_to_scope ~ '^(TEAM|DEPT|USER):[0-9a-fA-F-]{36}$'
               OR assigned_to_scope IN ('ROLE:MANAGER', 'ROLE:ADMIN'));
