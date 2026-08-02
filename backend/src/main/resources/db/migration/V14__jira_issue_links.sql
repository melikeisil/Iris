ALTER TABLE work_items
    ADD COLUMN jira_issue_url VARCHAR(512) NULL,
    ADD COLUMN jira_created_at TIMESTAMPTZ NULL;

CREATE INDEX ix_wi_jira_created_at
    ON work_items (jira_created_at)
    WHERE jira_created_at IS NOT NULL;
