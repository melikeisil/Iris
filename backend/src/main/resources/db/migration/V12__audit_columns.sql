-- V12: the audit columns BaseEntity mandates, on the five tables that were missing them.
--
-- Every persisted entity extends BaseEntity, which maps `created_at` and `updated_at`.
-- Five tables introduced in V9 and V10 were written with only a domain-specific timestamp
-- (`assigned_at`) or with `created_at` alone, so Hibernate's schema validation refused to
-- start: "missing column [created_at] in table [assignments]".
--
-- Fixing it in the schema rather than by exempting the entities keeps one rule -- every
-- row records when it was written and when it last changed -- instead of a per-table
-- exception list that the next entity would have to consult.

-- assignments ---------------------------------------------------------------
-- Note that `assigned_at` is NOT redundant with `created_at`, and both are kept.
-- `created_at` is when this row was written; `assigned_at` is when the assignment took
-- effect. They coincide today, but diverge as soon as Phase 8 imports assignment history
-- from Jira, where the effective date comes from Jira's changelog and the row is written
-- now. The fairness score reads `assigned_at`, which is the correct one for that question.
ALTER TABLE assignments
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TRIGGER trg_assignments_updated_at
    BEFORE UPDATE ON assignments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- process_owners ------------------------------------------------------------
-- Same distinction: `assigned_at` is when someone became the owner.
ALTER TABLE process_owners
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TRIGGER trg_process_owners_updated_at
    BEFORE UPDATE ON process_owners
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- recommendation_candidates -------------------------------------------------
-- Candidate rows are rewritten wholesale when a ranking is recomputed, so updated_at
-- rarely differs from created_at. It is still required by the entity contract.
ALTER TABLE recommendation_candidates
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TRIGGER trg_recommendation_candidates_updated_at
    BEFORE UPDATE ON recommendation_candidates
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- process_definitions -------------------------------------------------------
ALTER TABLE process_definitions
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TRIGGER trg_process_definitions_updated_at
    BEFORE UPDATE ON process_definitions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- process_template_tasks ----------------------------------------------------
ALTER TABLE process_template_tasks
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TRIGGER trg_process_template_tasks_updated_at
    BEFORE UPDATE ON process_template_tasks
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- employee_level_policies.max_active_work_items -----------------------------
-- SMALLINT in V10, but the entity maps it as Integer, which Hibernate binds as
-- Types#INTEGER - schema validation rejects the pair. Widening the column rather than
-- narrowing the field: this is a human-configured count (3 for interns, 6 for juniors)
-- that flows into LevelRestrictions and is compared against an int, and `Short` would
-- push a needless cast into every one of those call sites to save two bytes on a table
-- holding five rows per organization.
ALTER TABLE employee_level_policies
    ALTER COLUMN max_active_work_items TYPE INTEGER;
