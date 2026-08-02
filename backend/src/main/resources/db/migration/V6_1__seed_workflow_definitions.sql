-- The six system workflow profiles from docs/04-workflows.md.
--
-- Versioned, not repeatable, for two reasons:
--   1. Flyway runs repeatable migrations only after every versioned one, so a repeatable
--      seed would land *after* V900 demo data - which references these definition IDs by
--      foreign key. Ordering has to be explicit.
--   2. work_items carries a composite foreign key (workflow_definition_id, status) into
--      workflow_states. Once work exists, a state is not something to replace silently on
--      a checksum change; changing one is a deliberate act that deserves its own migration.
--
-- Still written idempotently (upserts throughout) so it is safe to re-run against a
-- partially seeded database.

INSERT INTO workflow_definitions (id, organization_id, code, name, applies_to_family, initial_state, is_default) VALUES
    ('00000000-0000-7000-8000-000000000101', NULL, 'SERVICE',     'Service Request Workflow', 'SERVICE_WORK',     'NEW',     TRUE),
    ('00000000-0000-7000-8000-000000000102', NULL, 'PROCESS',     'Process Workflow',         'PROCESS_WORK',     'NEW',     TRUE),
    ('00000000-0000-7000-8000-000000000103', NULL, 'DEVELOPMENT', 'Development Workflow',     'DEVELOPMENT_WORK', 'BACKLOG', TRUE),
    ('00000000-0000-7000-8000-000000000104', NULL, 'BUG',         'Bug Workflow',             'DEVELOPMENT_WORK', 'OPEN',    FALSE),
    ('00000000-0000-7000-8000-000000000105', NULL, 'RESEARCH',    'Research Workflow',        'DEVELOPMENT_WORK', 'BACKLOG', FALSE),
    ('00000000-0000-7000-8000-000000000106', NULL, 'INCIDENT',    'Incident Workflow',        'INCIDENT_WORK',    'OPEN',    TRUE)
ON CONFLICT (id) DO UPDATE
    SET name = EXCLUDED.name,
        applies_to_family = EXCLUDED.applies_to_family,
        initial_state = EXCLUDED.initial_state,
        is_default = EXCLUDED.is_default;

DELETE FROM workflow_transitions WHERE workflow_definition_id IN (
    '00000000-0000-7000-8000-000000000101', '00000000-0000-7000-8000-000000000102',
    '00000000-0000-7000-8000-000000000103', '00000000-0000-7000-8000-000000000104',
    '00000000-0000-7000-8000-000000000105', '00000000-0000-7000-8000-000000000106');

-- ---------------------------------------------------------------------------
-- SERVICE (11 states, 18 transitions)
-- ---------------------------------------------------------------------------
INSERT INTO workflow_states
    (id, workflow_definition_id, code, name, category, is_initial, is_terminal,
     is_terminal_success, is_active_work, is_blocked, is_waiting_external, display_order)
VALUES
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'NEW',                 'New',                  'TODO',        TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE, 10),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'AI_ANALYSIS',         'AI Analysis',          'TODO',        FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 20),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'WAITING_INFORMATION', 'Waiting for Info',     'TODO',        FALSE, FALSE, FALSE, FALSE, FALSE, TRUE,  30),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'ASSIGNED',            'Assigned',             'TODO',        FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 40),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'IN_PROGRESS',         'In Progress',          'IN_PROGRESS', FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, 50),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'BLOCKED',             'Blocked',              'BLOCKED',     FALSE, FALSE, FALSE, FALSE, TRUE,  TRUE,  60),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'WAITING_REQUESTER',   'Waiting for Requester','IN_PROGRESS', FALSE, FALSE, FALSE, FALSE, FALSE, TRUE,  70),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'WAITING_DEVELOPMENT', 'Waiting for Dev Fix',  'IN_PROGRESS', FALSE, FALSE, FALSE, FALSE, FALSE, TRUE,  80),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'RESOLVED',            'Resolved',             'DONE',        FALSE, FALSE, TRUE,  FALSE, FALSE, FALSE, 90),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'CLOSED',              'Closed',               'DONE',        FALSE, TRUE,  TRUE,  FALSE, FALSE, FALSE, 100),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'CANCELLED',           'Cancelled',            'DONE',        FALSE, TRUE,  FALSE, FALSE, FALSE, FALSE, 110)
ON CONFLICT (workflow_definition_id, code) DO UPDATE
    SET name                = EXCLUDED.name,
        category            = EXCLUDED.category,
        is_initial          = EXCLUDED.is_initial,
        is_terminal         = EXCLUDED.is_terminal,
        is_terminal_success = EXCLUDED.is_terminal_success,
        is_active_work      = EXCLUDED.is_active_work,
        is_blocked          = EXCLUDED.is_blocked,
        is_waiting_external = EXCLUDED.is_waiting_external,
        display_order       = EXCLUDED.display_order;

INSERT INTO workflow_transitions
    (id, workflow_definition_id, transition_code, label, from_state, to_state,
     allowed_roles, allowed_relationships, guards, requires_reason, automatic, display_order)
VALUES
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'ANALYZE',            'Analyze',              'NEW',                 'AI_ANALYSIS',         '',                            '',                              '',                                    FALSE, TRUE,  10),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'NEEDS_INFO',         'Request information',  'AI_ANALYSIS',         'WAITING_INFORMATION', '',                            '',                              '',                                    FALSE, TRUE,  20),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'AUTO_ASSIGN',        'Auto-assign',          'AI_ANALYSIS',         'ASSIGNED',            '',                            '',                              'REQUIRE_ASSIGNEE',                    FALSE, TRUE,  30),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'ANALYSIS_FAILED',    'Analysis failed',      'AI_ANALYSIS',         'NEW',                 '',                            '',                              '',                                    FALSE, TRUE,  40),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'INFO_PROVIDED',      'Submit information',   'WAITING_INFORMATION', 'AI_ANALYSIS',         'TEAM_LEAD,MANAGER',           'REQUESTER,ASSIGNEE',            '',                                    FALSE, FALSE, 50),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'ASSIGN',             'Assign',               'NEW',                 'ASSIGNED',            'TEAM_LEAD,MANAGER',           '',                              'REQUIRE_ASSIGNEE,LEVEL_RESTRICTION',  FALSE, FALSE, 60),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'START_WORK',         'Start work',           'ASSIGNED',            'IN_PROGRESS',         'TEAM_LEAD',                   'ASSIGNEE',                      'REQUIRE_ASSIGNEE,NO_OPEN_DEPENDENCIES', FALSE, FALSE, 70),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'BLOCK',              'Block',                'IN_PROGRESS',         'BLOCKED',             'TEAM_LEAD',                   'ASSIGNEE',                      'REQUIRE_REASON',                      TRUE,  FALSE, 80),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'UNBLOCK',            'Unblock',              'BLOCKED',             'IN_PROGRESS',         'TEAM_LEAD',                   'ASSIGNEE',                      '',                                    FALSE, FALSE, 90),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'ASK_REQUESTER',      'Ask requester',        'IN_PROGRESS',         'WAITING_REQUESTER',   'TEAM_LEAD',                   'ASSIGNEE',                      'REQUIRE_REASON',                      TRUE,  FALSE, 100),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'REQUESTER_REPLIED',  'Requester replied',    'WAITING_REQUESTER',   'IN_PROGRESS',         'TEAM_LEAD',                   'REQUESTER,ASSIGNEE',            '',                                    FALSE, FALSE, 110),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'CONVERT_TO_DEV',     'Convert to development','IN_PROGRESS',        'WAITING_DEVELOPMENT', 'TEAM_LEAD,MANAGER',           'ASSIGNEE',                      'REQUIRE_REASON',                      TRUE,  FALSE, 120),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'DEV_COMPLETED',      'Development completed','WAITING_DEVELOPMENT', 'RESOLVED',            '',                            '',                              '',                                    FALSE, TRUE,  130),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'RESOLVE',            'Resolve',              'IN_PROGRESS',         'RESOLVED',            'TEAM_LEAD',                   'ASSIGNEE',                      'ALL_CHILDREN_TERMINAL',               FALSE, FALSE, 140),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'VERIFY',             'Verify and close',     'RESOLVED',            'CLOSED',              'TEAM_LEAD,MANAGER',           'REQUESTER,PROCESS_OWNER',       'REQUIRE_VERIFICATION_ACTOR',          FALSE, FALSE, 150),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'REOPEN',             'Reopen',               'RESOLVED',            'IN_PROGRESS',         'TEAM_LEAD,MANAGER',           'REQUESTER,PROCESS_OWNER',       'REQUIRE_REASON',                      TRUE,  FALSE, 160),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'CANCEL',             'Cancel',               'ASSIGNED',            'CANCELLED',           'TEAM_LEAD,MANAGER',           '',                              'REQUIRE_REASON',                      TRUE,  FALSE, 170),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000101', 'CANCEL',             'Cancel',               'IN_PROGRESS',         'CANCELLED',           'TEAM_LEAD,MANAGER',           '',                              'REQUIRE_REASON',                      TRUE,  FALSE, 180);

-- ---------------------------------------------------------------------------
-- PROCESS (9 states, 13 transitions)
-- ---------------------------------------------------------------------------
INSERT INTO workflow_states
    (id, workflow_definition_id, code, name, category, is_initial, is_terminal,
     is_terminal_success, is_active_work, is_blocked, is_waiting_external, display_order)
VALUES
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'NEW',                          'New',                   'TODO',        TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE, 10),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'AI_ANALYSIS',                  'AI Analysis',           'TODO',        FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 20),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'WAITING_INFORMATION',          'Waiting for Info',      'TODO',        FALSE, FALSE, FALSE, FALSE, FALSE, TRUE,  30),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'PLANNING',                     'Planning',              'IN_PROGRESS', FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, 40),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'IN_PROGRESS',                  'In Progress',           'IN_PROGRESS', FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, 50),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'BLOCKED',                      'Blocked',               'BLOCKED',     FALSE, FALSE, FALSE, FALSE, TRUE,  TRUE,  60),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'WAITING_PROCESS_VERIFICATION', 'Waiting Verification',  'REVIEW',      FALSE, FALSE, FALSE, FALSE, FALSE, TRUE,  70),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'ESCALATED',                    'Escalated',             'BLOCKED',     FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 80),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'CLOSED',                       'Closed',                'DONE',        FALSE, TRUE,  TRUE,  FALSE, FALSE, FALSE, 90)
ON CONFLICT (workflow_definition_id, code) DO UPDATE
    SET name                = EXCLUDED.name,
        category            = EXCLUDED.category,
        is_initial          = EXCLUDED.is_initial,
        is_terminal         = EXCLUDED.is_terminal,
        is_terminal_success = EXCLUDED.is_terminal_success,
        is_active_work      = EXCLUDED.is_active_work,
        is_blocked          = EXCLUDED.is_blocked,
        is_waiting_external = EXCLUDED.is_waiting_external,
        display_order       = EXCLUDED.display_order;

INSERT INTO workflow_transitions
    (id, workflow_definition_id, transition_code, label, from_state, to_state,
     allowed_roles, allowed_relationships, guards, requires_reason, automatic, display_order)
VALUES
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'ANALYZE',              'Analyze',              'NEW',                          'AI_ANALYSIS',                  '',                  '',                        '',                        FALSE, TRUE,  10),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'NEEDS_INFO',           'Request information',  'AI_ANALYSIS',                  'WAITING_INFORMATION',          '',                  '',                        '',                        FALSE, TRUE,  20),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'INFO_PROVIDED',        'Submit information',   'WAITING_INFORMATION',          'AI_ANALYSIS',                  'TEAM_LEAD,MANAGER', 'REQUESTER,PROCESS_OWNER', '',                        FALSE, FALSE, 30),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'PLAN_GENERATED',       'Plan generated',       'AI_ANALYSIS',                  'PLANNING',                     '',                  '',                        '',                        FALSE, TRUE,  40),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'PLAN_APPROVED',        'Approve plan',         'PLANNING',                     'IN_PROGRESS',                  'MANAGER',           'PROCESS_OWNER',           '',                        FALSE, FALSE, 50),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'BLOCK',                'Block',                'IN_PROGRESS',                  'BLOCKED',                      'TEAM_LEAD,MANAGER', 'PROCESS_OWNER',           'REQUIRE_REASON',          TRUE,  FALSE, 60),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'UNBLOCK',              'Unblock',              'BLOCKED',                      'IN_PROGRESS',                  'TEAM_LEAD,MANAGER', 'PROCESS_OWNER',           '',                        FALSE, FALSE, 70),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'ALL_CHILDREN_DONE',    'All tasks complete',   'IN_PROGRESS',                  'WAITING_PROCESS_VERIFICATION', '',                  '',                        'ALL_CHILDREN_TERMINAL',   FALSE, TRUE,  80),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'REJECT_VERIFICATION',  'Reject',               'WAITING_PROCESS_VERIFICATION', 'IN_PROGRESS',                  'MANAGER',           'PROCESS_OWNER',           'REQUIRE_REASON',          TRUE,  FALSE, 90),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'CLOSE_PROCESS',        'Close process',        'WAITING_PROCESS_VERIFICATION', 'CLOSED',                       'MANAGER',           'PROCESS_OWNER',           'ALL_CHILDREN_TERMINAL',   FALSE, FALSE, 100),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'ESCALATE',             'Escalate',             'IN_PROGRESS',                  'ESCALATED',                    'TEAM_LEAD',         'PROCESS_OWNER',           'REQUIRE_REASON',          TRUE,  FALSE, 110),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'ESCALATION_RESOLVED',  'Resolve escalation',   'ESCALATED',                    'IN_PROGRESS',                  'MANAGER',           '',                        'REQUIRE_REASON',          TRUE,  FALSE, 120),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000102', 'PLAN_EDITED',          'Edit plan',            'PLANNING',                     'PLANNING',                     'MANAGER',           'PROCESS_OWNER',           '',                        FALSE, FALSE, 130);

-- ---------------------------------------------------------------------------
-- DEVELOPMENT (10 states, 16 transitions)
-- No IN_PROGRESS -> DONE edge exists. Review and testing are structurally unskippable.
-- ---------------------------------------------------------------------------
INSERT INTO workflow_states
    (id, workflow_definition_id, code, name, category, is_initial, is_terminal,
     is_terminal_success, is_active_work, is_blocked, is_waiting_external, display_order)
VALUES
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'BACKLOG',             'Backlog',          'TODO',        TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE, 10),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'AI_ANALYSIS',         'AI Analysis',      'TODO',        FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 20),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'WAITING_INFORMATION', 'Waiting for Info', 'TODO',        FALSE, FALSE, FALSE, FALSE, FALSE, TRUE,  30),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'READY',               'Ready',            'TODO',        FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 40),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'TODO',                'To Do',            'TODO',        FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 50),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'IN_PROGRESS',         'In Progress',      'IN_PROGRESS', FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, 60),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'BLOCKED',             'Blocked',          'BLOCKED',     FALSE, FALSE, FALSE, FALSE, TRUE,  TRUE,  70),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'CODE_REVIEW',         'Code Review',      'REVIEW',      FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, 80),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'TESTING',             'Testing',          'TESTING',     FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, 90),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'DONE',                'Done',             'DONE',        FALSE, TRUE,  TRUE,  FALSE, FALSE, FALSE, 100)
ON CONFLICT (workflow_definition_id, code) DO UPDATE
    SET name                = EXCLUDED.name,
        category            = EXCLUDED.category,
        is_initial          = EXCLUDED.is_initial,
        is_terminal         = EXCLUDED.is_terminal,
        is_terminal_success = EXCLUDED.is_terminal_success,
        is_active_work      = EXCLUDED.is_active_work,
        is_blocked          = EXCLUDED.is_blocked,
        is_waiting_external = EXCLUDED.is_waiting_external,
        display_order       = EXCLUDED.display_order;

INSERT INTO workflow_transitions
    (id, workflow_definition_id, transition_code, label, from_state, to_state,
     allowed_roles, allowed_relationships, guards, requires_reason, automatic, display_order)
VALUES
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'ANALYZE',             'Analyze',              'BACKLOG',             'AI_ANALYSIS',         '',                  '',         '',                                       FALSE, TRUE,  10),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'NEEDS_INFO',          'Request information',  'AI_ANALYSIS',         'WAITING_INFORMATION', '',                  '',         '',                                       FALSE, TRUE,  20),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'INFO_PROVIDED',       'Submit information',   'WAITING_INFORMATION', 'AI_ANALYSIS',         'TEAM_LEAD,MANAGER', 'REQUESTER,ASSIGNEE,PROCESS_OWNER', '',              FALSE, FALSE, 30),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'ANALYSIS_COMPLETE',   'Analysis complete',    'AI_ANALYSIS',         'BACKLOG',             '',                  '',         '',                                       FALSE, TRUE,  40),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'MARK_READY',          'Mark ready',           'BACKLOG',             'READY',               'TEAM_LEAD,MANAGER', 'PROCESS_OWNER', 'REQUIRE_ACCEPTANCE_CRITERIA,NO_OPEN_DEPENDENCIES', FALSE, FALSE, 50),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'RETURN_TO_BACKLOG',   'Return to backlog',    'READY',               'BACKLOG',             'TEAM_LEAD,MANAGER', '',         'REQUIRE_REASON',                         TRUE,  FALSE, 60),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'ADD_TO_SPRINT',       'Add to sprint',        'READY',               'TODO',                'TEAM_LEAD,MANAGER', '',         'REQUIRE_SPRINT',                         FALSE, FALSE, 70),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'REMOVE_FROM_SPRINT',  'Remove from sprint',   'TODO',                'READY',               'TEAM_LEAD,MANAGER', '',         'REQUIRE_REASON',                         TRUE,  FALSE, 80),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'START_WORK',          'Start work',           'TODO',                'IN_PROGRESS',         'TEAM_LEAD',         'ASSIGNEE', 'REQUIRE_ASSIGNEE,NO_OPEN_DEPENDENCIES,LEVEL_RESTRICTION', FALSE, FALSE, 90),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'BLOCK',               'Block',                'IN_PROGRESS',         'BLOCKED',             'TEAM_LEAD',         'ASSIGNEE', 'REQUIRE_REASON',                         TRUE,  FALSE, 100),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'UNBLOCK',             'Unblock',              'BLOCKED',             'IN_PROGRESS',         'TEAM_LEAD',         'ASSIGNEE', '',                                       FALSE, FALSE, 110),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'SUBMIT_FOR_REVIEW',   'Submit for review',    'IN_PROGRESS',         'CODE_REVIEW',         '',                  'ASSIGNEE', '',                                       FALSE, FALSE, 120),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'REJECT_REVIEW',       'Request changes',      'CODE_REVIEW',         'IN_PROGRESS',         'TEAM_LEAD',         'REVIEWER', 'REQUIRE_REASON',                         TRUE,  FALSE, 130),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'APPROVE_REVIEW',      'Approve review',       'CODE_REVIEW',         'TESTING',             'TEAM_LEAD',         'REVIEWER', 'NOT_SELF_REVIEW',                        FALSE, FALSE, 140),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'TEST_FAILED',         'Test failed',          'TESTING',             'IN_PROGRESS',         'TEAM_LEAD',         'REVIEWER', 'REQUIRE_REASON',                         TRUE,  FALSE, 150),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000103', 'PASS_QA',             'Pass QA',              'TESTING',             'DONE',                'TEAM_LEAD',         'REVIEWER', 'NOT_SELF_REVIEW',                        FALSE, FALSE, 160);

-- ---------------------------------------------------------------------------
-- BUG (9 states, 13 transitions)
-- ---------------------------------------------------------------------------
INSERT INTO workflow_states
    (id, workflow_definition_id, code, name, category, is_initial, is_terminal,
     is_terminal_success, is_active_work, is_blocked, is_waiting_external, display_order)
VALUES
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'OPEN',        'Open',        'TODO',        TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE, 10),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'ANALYSIS',    'Analysis',    'IN_PROGRESS', FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, 20),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'READY',       'Ready',       'TODO',        FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 30),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'IN_PROGRESS', 'In Progress', 'IN_PROGRESS', FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, 40),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'BLOCKED',     'Blocked',     'BLOCKED',     FALSE, FALSE, FALSE, FALSE, TRUE,  TRUE,  50),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'CODE_REVIEW', 'Code Review', 'REVIEW',      FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, 60),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'QA',          'QA',          'TESTING',     FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, 70),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'VERIFIED',    'Verified',    'DONE',        FALSE, FALSE, TRUE,  FALSE, FALSE, FALSE, 80),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'CLOSED',      'Closed',      'DONE',        FALSE, TRUE,  TRUE,  FALSE, FALSE, FALSE, 90)
ON CONFLICT (workflow_definition_id, code) DO UPDATE
    SET name                = EXCLUDED.name,
        category            = EXCLUDED.category,
        is_initial          = EXCLUDED.is_initial,
        is_terminal         = EXCLUDED.is_terminal,
        is_terminal_success = EXCLUDED.is_terminal_success,
        is_active_work      = EXCLUDED.is_active_work,
        is_blocked          = EXCLUDED.is_blocked,
        is_waiting_external = EXCLUDED.is_waiting_external,
        display_order       = EXCLUDED.display_order;

INSERT INTO workflow_transitions
    (id, workflow_definition_id, transition_code, label, from_state, to_state,
     allowed_roles, allowed_relationships, guards, requires_reason, automatic, display_order)
VALUES
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'START_ANALYSIS',    'Start analysis',   'OPEN',        'ANALYSIS',    'TEAM_LEAD', 'ASSIGNEE', 'REQUIRE_ASSIGNEE',          FALSE, FALSE, 10),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'ROOT_CAUSE_FOUND',  'Root cause found', 'ANALYSIS',    'READY',       'TEAM_LEAD', 'ASSIGNEE', '',                          FALSE, FALSE, 20),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'CANNOT_REPRODUCE',  'Cannot reproduce', 'ANALYSIS',    'OPEN',        'TEAM_LEAD', 'ASSIGNEE', 'REQUIRE_REASON',            TRUE,  FALSE, 30),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'START_WORK',        'Start work',       'READY',       'IN_PROGRESS', 'TEAM_LEAD', 'ASSIGNEE', 'REQUIRE_ASSIGNEE,NO_OPEN_DEPENDENCIES,LEVEL_RESTRICTION', FALSE, FALSE, 40),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'BLOCK',             'Block',            'IN_PROGRESS', 'BLOCKED',     'TEAM_LEAD', 'ASSIGNEE', 'REQUIRE_REASON',            TRUE,  FALSE, 50),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'UNBLOCK',           'Unblock',          'BLOCKED',     'IN_PROGRESS', 'TEAM_LEAD', 'ASSIGNEE', '',                          FALSE, FALSE, 60),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'SUBMIT_FOR_REVIEW', 'Submit for review','IN_PROGRESS', 'CODE_REVIEW', '',          'ASSIGNEE', '',                          FALSE, FALSE, 70),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'REJECT_REVIEW',     'Request changes',  'CODE_REVIEW', 'IN_PROGRESS', 'TEAM_LEAD', 'REVIEWER', 'REQUIRE_REASON',            TRUE,  FALSE, 80),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'APPROVE_REVIEW',    'Approve review',   'CODE_REVIEW', 'QA',          'TEAM_LEAD', 'REVIEWER', 'NOT_SELF_REVIEW',           FALSE, FALSE, 90),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'QA_FAILED',         'QA failed',        'QA',          'IN_PROGRESS', 'TEAM_LEAD', 'REVIEWER', 'REQUIRE_REASON',            TRUE,  FALSE, 100),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'QA_PASSED',         'QA passed',        'QA',          'VERIFIED',    'TEAM_LEAD', 'REVIEWER', 'NOT_SELF_REVIEW',           FALSE, FALSE, 110),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'CLOSE',             'Close',            'VERIFIED',    'CLOSED',      'TEAM_LEAD,MANAGER', 'PROCESS_OWNER', 'ALL_CHILDREN_TERMINAL', FALSE, FALSE, 120),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000104', 'REOPEN',            'Reopen',           'VERIFIED',    'OPEN',        'TEAM_LEAD,MANAGER', 'REQUESTER,PROCESS_OWNER', 'REQUIRE_REASON', TRUE, FALSE, 130);

-- ---------------------------------------------------------------------------
-- RESEARCH (6 states, 7 transitions)
-- ---------------------------------------------------------------------------
INSERT INTO workflow_states
    (id, workflow_definition_id, code, name, category, is_initial, is_terminal,
     is_terminal_success, is_active_work, is_blocked, is_waiting_external, display_order)
VALUES
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000105', 'BACKLOG',         'Backlog',         'TODO',        TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE, 10),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000105', 'READY',           'Ready',           'TODO',        FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 20),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000105', 'IN_PROGRESS',     'In Progress',     'IN_PROGRESS', FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, 30),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000105', 'BLOCKED',         'Blocked',         'BLOCKED',     FALSE, FALSE, FALSE, FALSE, TRUE,  TRUE,  40),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000105', 'FINDINGS_REVIEW', 'Findings Review', 'REVIEW',      FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, 50),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000105', 'COMPLETED',       'Completed',       'DONE',        FALSE, TRUE,  TRUE,  FALSE, FALSE, FALSE, 60)
ON CONFLICT (workflow_definition_id, code) DO UPDATE
    SET name                = EXCLUDED.name,
        category            = EXCLUDED.category,
        is_initial          = EXCLUDED.is_initial,
        is_terminal         = EXCLUDED.is_terminal,
        is_terminal_success = EXCLUDED.is_terminal_success,
        is_active_work      = EXCLUDED.is_active_work,
        is_blocked          = EXCLUDED.is_blocked,
        is_waiting_external = EXCLUDED.is_waiting_external,
        display_order       = EXCLUDED.display_order;

INSERT INTO workflow_transitions
    (id, workflow_definition_id, transition_code, label, from_state, to_state,
     allowed_roles, allowed_relationships, guards, requires_reason, automatic, display_order)
VALUES
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000105', 'MARK_READY',           'Mark ready',        'BACKLOG',         'READY',           'TEAM_LEAD,MANAGER', '',         '',               FALSE, FALSE, 10),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000105', 'START_WORK',           'Start work',        'READY',           'IN_PROGRESS',     'TEAM_LEAD',         'ASSIGNEE', 'REQUIRE_ASSIGNEE', FALSE, FALSE, 20),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000105', 'BLOCK',                'Block',             'IN_PROGRESS',     'BLOCKED',         'TEAM_LEAD',         'ASSIGNEE', 'REQUIRE_REASON', TRUE,  FALSE, 30),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000105', 'UNBLOCK',              'Unblock',           'BLOCKED',         'IN_PROGRESS',     'TEAM_LEAD',         'ASSIGNEE', '',               FALSE, FALSE, 40),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000105', 'SUBMIT_FINDINGS',      'Submit findings',   'IN_PROGRESS',     'FINDINGS_REVIEW', '',                  'ASSIGNEE', '',               FALSE, FALSE, 50),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000105', 'MORE_RESEARCH_NEEDED', 'More research',     'FINDINGS_REVIEW', 'IN_PROGRESS',     'TEAM_LEAD',         'REVIEWER', 'REQUIRE_REASON', TRUE,  FALSE, 60),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000105', 'ACCEPT_FINDINGS',      'Accept findings',   'FINDINGS_REVIEW', 'COMPLETED',       'TEAM_LEAD',         'REVIEWER', 'NOT_SELF_REVIEW', FALSE, FALSE, 70);

-- ---------------------------------------------------------------------------
-- INCIDENT (8 states, 10 transitions)
-- ---------------------------------------------------------------------------
INSERT INTO workflow_states
    (id, workflow_definition_id, code, name, category, is_initial, is_terminal,
     is_terminal_success, is_active_work, is_blocked, is_waiting_external, display_order)
VALUES
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000106', 'OPEN',          'Open',          'TODO',        TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE, 10),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000106', 'TRIAGED',       'Triaged',       'TODO',        FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 20),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000106', 'INVESTIGATING', 'Investigating', 'IN_PROGRESS', FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, 30),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000106', 'MITIGATED',     'Mitigated',     'IN_PROGRESS', FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, 40),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000106', 'ESCALATED',     'Escalated',     'BLOCKED',     FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 50),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000106', 'RESOLVED',      'Resolved',      'DONE',        FALSE, FALSE, TRUE,  FALSE, FALSE, FALSE, 60),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000106', 'POSTMORTEM',    'Postmortem',    'DONE',        FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, 70),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000106', 'CLOSED',        'Closed',        'DONE',        FALSE, TRUE,  TRUE,  FALSE, FALSE, FALSE, 80)
ON CONFLICT (workflow_definition_id, code) DO UPDATE
    SET name                = EXCLUDED.name,
        category            = EXCLUDED.category,
        is_initial          = EXCLUDED.is_initial,
        is_terminal         = EXCLUDED.is_terminal,
        is_terminal_success = EXCLUDED.is_terminal_success,
        is_active_work      = EXCLUDED.is_active_work,
        is_blocked          = EXCLUDED.is_blocked,
        is_waiting_external = EXCLUDED.is_waiting_external,
        display_order       = EXCLUDED.display_order;

INSERT INTO workflow_transitions
    (id, workflow_definition_id, transition_code, label, from_state, to_state,
     allowed_roles, allowed_relationships, guards, requires_reason, automatic, display_order)
VALUES
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000106', 'TRIAGE',               'Triage',              'OPEN',          'TRIAGED',       'TEAM_LEAD,MANAGER', 'ASSIGNEE', '',                                 FALSE, FALSE, 10),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000106', 'START_INVESTIGATION',  'Start investigation', 'TRIAGED',       'INVESTIGATING', 'TEAM_LEAD',         'ASSIGNEE', 'REQUIRE_ASSIGNEE,LEVEL_RESTRICTION', FALSE, FALSE, 20),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000106', 'APPLY_MITIGATION',     'Apply mitigation',    'INVESTIGATING', 'MITIGATED',     'TEAM_LEAD',         'ASSIGNEE', '',                                 FALSE, FALSE, 30),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000106', 'MITIGATION_FAILED',    'Mitigation failed',   'MITIGATED',     'INVESTIGATING', 'TEAM_LEAD',         'ASSIGNEE', 'REQUIRE_REASON',                   TRUE,  FALSE, 40),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000106', 'CONFIRM_RESOLUTION',   'Confirm resolution',  'MITIGATED',     'RESOLVED',      'TEAM_LEAD,MANAGER', 'REVIEWER', 'NOT_SELF_REVIEW',                  FALSE, FALSE, 50),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000106', 'START_POSTMORTEM',     'Start postmortem',    'RESOLVED',      'POSTMORTEM',    'TEAM_LEAD,MANAGER', '',         '',                                 FALSE, FALSE, 60),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000106', 'COMPLETE_POSTMORTEM',  'Complete postmortem', 'POSTMORTEM',    'CLOSED',        'TEAM_LEAD,MANAGER', '',         '',                                 FALSE, FALSE, 70),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000106', 'CLOSE_NO_POSTMORTEM',  'Close',               'RESOLVED',      'CLOSED',        'MANAGER',           '',         'REQUIRE_POSTMORTEM',               FALSE, FALSE, 80),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000106', 'ESCALATE',             'Escalate',            'INVESTIGATING', 'ESCALATED',     'TEAM_LEAD',         'ASSIGNEE', 'REQUIRE_REASON',                   TRUE,  FALSE, 90),
 (gen_random_uuid(), '00000000-0000-7000-8000-000000000106', 'ESCALATION_RESOLVED',  'Resolve escalation',  'ESCALATED',     'INVESTIGATING', 'MANAGER',           '',         'REQUIRE_REASON',                   TRUE,  FALSE, 100);
