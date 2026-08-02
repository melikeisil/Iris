-- Storage for answers to AI-generated clarification questions.
--
-- Lives on the recommendation, not the work item: an answer is meaningful only in
-- relation to the specific analysis that asked the question. If the item is edited and
-- re-analyzed before the questions are answered, that analysis is superseded and its
-- questions go with it — a stale set of answers should never be readable as current.
ALTER TABLE recommendations
    ADD COLUMN clarification_answers JSONB NULL;
