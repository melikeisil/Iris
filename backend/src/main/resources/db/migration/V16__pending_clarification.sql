-- Storage for the clarification questions currently pending on a work item.
--
-- Lives on the work item, not the recommendation: the point of this column is that the
-- workitem module can render "why is this item waiting on me" in the work item detail
-- response without reaching into the analysis module's tables - a cross-module read that
-- the module boundaries (see com.smartwork.workitem's allowedDependencies) do not permit.
-- The analysis module writes a copy here when it asks a question, and clears it once the
-- item leaves WAITING_INFORMATION or a fresh analysis stops needing more information.
ALTER TABLE work_items
    ADD COLUMN pending_clarification_questions JSONB NULL;
