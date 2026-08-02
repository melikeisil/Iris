-- V901: Phase 5 demo data — process templates, level policies, assignment policies.
--
-- A separate file rather than an edit to V900 so existing demo databases do not fail on a
-- changed checksum.
--
-- Nothing here references a user. Demo users are created by DemoUserSeeder on
-- ApplicationReadyEvent, which runs *after* Flyway, so any row needing a user id
-- (template process owners, user skills, workload items) is seeded by DemoPlanningSeeder
-- in Java instead. This is the same ordering trap that produced ADR-029; the fix is to
-- respect the boundary rather than work around it.

-- Employee level policies: the documented defaults from docs/08 §7, made explicit --------
INSERT INTO employee_level_policies (
    id, organization_id, employee_level, allow_production_access, allow_financial_approval,
    allow_security_sensitive, max_priority, max_active_work_items, max_estimated_hours,
    require_mentor, allowed_categories) VALUES
    ('00000000-0000-7000-8000-00000000e001', '00000000-0000-7000-8000-00000000a001',
     'INTERN', FALSE, FALSE, FALSE, 'HIGH', 3, 8.00, TRUE, NULL),
    ('00000000-0000-7000-8000-00000000e002', '00000000-0000-7000-8000-00000000a001',
     'JUNIOR', FALSE, TRUE, TRUE, 'HIGH', 6, 16.00, FALSE, NULL),
    ('00000000-0000-7000-8000-00000000e003', '00000000-0000-7000-8000-00000000a001',
     'MID', TRUE, FALSE, TRUE, 'CRITICAL', NULL, NULL, FALSE, NULL),
    ('00000000-0000-7000-8000-00000000e004', '00000000-0000-7000-8000-00000000a001',
     'SENIOR', TRUE, TRUE, TRUE, 'CRITICAL', NULL, NULL, FALSE, NULL),
    ('00000000-0000-7000-8000-00000000e005', '00000000-0000-7000-8000-00000000a001',
     'LEAD', TRUE, TRUE, TRUE, 'CRITICAL', NULL, NULL, FALSE, NULL)
ON CONFLICT (organization_id, employee_level) DO NOTHING;

-- Assignment policies: org-wide defaults plus a stricter Engineering policy ------------
INSERT INTO assignment_policies (
    id, organization_id, department_id, team_id, name, priority,
    min_auto_assign_confidence, max_auto_assign_workload, max_auto_assign_hours,
    auto_assign_max_risk, min_auto_assign_score, min_score_gap,
    allow_cross_department_auto, weight_overrides) VALUES
    ('00000000-0000-7000-8000-00000000e101', '00000000-0000-7000-8000-00000000a001',
     NULL, NULL, 'Organization defaults', 100,
     0.85, 0.80, 8, 'LOW', 60, 5, FALSE, NULL),

    -- Engineering is deliberately stricter: higher confidence bar, lower workload
    -- ceiling, and a wider required score gap. It also weights skill a little harder at
    -- the expense of fairness, which is the trade-off an engineering org actually makes.
    ('00000000-0000-7000-8000-00000000e102', '00000000-0000-7000-8000-00000000a001',
     '00000000-0000-7000-8000-00000000a104', NULL, 'Engineering (stricter)', 50,
     0.90, 0.70, 8, 'LOW', 65, 8, FALSE,
     '{"skill": 0.35, "capacity": 0.20, "team": 0.15, "experience": 0.10, "component": 0.10, "deadline": 0.08, "fairness": 0.02}'::jsonb)
ON CONFLICT DO NOTHING;

-- Process templates ---------------------------------------------------------------------
-- match_keywords is what TemplateMatcher scores an incoming summary against. Kept in data
-- rather than code so an Admin can tune matching without a deploy.
INSERT INTO process_templates (
    id, organization_id, code, name, description, category, work_family,
    owner_department_id, default_priority, closure_type, match_keywords) VALUES

    ('00000000-0000-7000-8000-00000000f001', '00000000-0000-7000-8000-00000000a001',
     'EMPLOYEE_ONBOARDING', 'Employee Onboarding',
     'Everything required before a new hire''s first day.', 'hr_onboarding', 'PROCESS_WORK',
     '00000000-0000-7000-8000-00000000a101', 'HIGH', 'PROCESS_OWNER_VERIFICATION',
     'onboarding onboard new hire employee starting first day welcome induction'),

    ('00000000-0000-7000-8000-00000000f002', '00000000-0000-7000-8000-00000000a001',
     'EMPLOYEE_OFFBOARDING', 'Employee Offboarding',
     'Access revocation, equipment return and final payroll for a departing employee.',
     'hr_offboarding', 'PROCESS_WORK',
     '00000000-0000-7000-8000-00000000a101', 'HIGH', 'PROCESS_OWNER_VERIFICATION',
     'offboarding offboard leaving resignation departure termination last day exit'),

    ('00000000-0000-7000-8000-00000000f003', '00000000-0000-7000-8000-00000000a001',
     'ACCESS_REQUEST', 'Access Request',
     'Grant access to a system, repository or shared resource.', 'access', 'SERVICE_WORK',
     '00000000-0000-7000-8000-00000000a102', 'MEDIUM', 'REQUESTER_VERIFICATION',
     'access permission grant account login credentials repository system role'),

    ('00000000-0000-7000-8000-00000000f004', '00000000-0000-7000-8000-00000000a001',
     'HARDWARE_REQUEST', 'Hardware Request',
     'Procure, configure and hand over a hardware device.', 'hardware', 'SERVICE_WORK',
     '00000000-0000-7000-8000-00000000a102', 'MEDIUM', 'REQUESTER_VERIFICATION',
     'hardware laptop monitor keyboard device equipment machine replacement broken'),

    ('00000000-0000-7000-8000-00000000f005', '00000000-0000-7000-8000-00000000a001',
     'SOFTWARE_INSTALLATION', 'Software Installation',
     'Licence, approve and install a software package.', 'software', 'SERVICE_WORK',
     '00000000-0000-7000-8000-00000000a102', 'LOW', 'REQUESTER_VERIFICATION',
     'software install licence license application tool subscription package'),

    ('00000000-0000-7000-8000-00000000f006', '00000000-0000-7000-8000-00000000a001',
     'PURCHASE_REQUEST', 'Purchase Request',
     'Budget approval and procurement of goods or services.', 'procurement', 'PROCESS_WORK',
     '00000000-0000-7000-8000-00000000a103', 'MEDIUM', 'PROCESS_OWNER_VERIFICATION',
     'purchase buy procurement order invoice budget vendor supplier cost spend'),

    ('00000000-0000-7000-8000-00000000f007', '00000000-0000-7000-8000-00000000a001',
     'PRODUCTION_INCIDENT', 'Production Incident',
     'Triage, mitigate, resolve and review a production incident.', 'incident', 'INCIDENT_WORK',
     '00000000-0000-7000-8000-00000000a104', 'CRITICAL', 'TEAM_LEAD_VERIFICATION',
     'incident outage down production degraded failing errors sev severity emergency'),

    ('00000000-0000-7000-8000-00000000f008', '00000000-0000-7000-8000-00000000a001',
     'CUSTOMER_COMPLAINT', 'Customer Complaint',
     'Acknowledge, investigate and respond to a customer complaint.', 'complaint', 'SERVICE_WORK',
     '00000000-0000-7000-8000-00000000a102', 'HIGH', 'REQUESTER_VERIFICATION',
     'complaint customer unhappy dissatisfied escalation refund apology feedback')
ON CONFLICT (organization_id, code) DO NOTHING;

-- Template tasks ------------------------------------------------------------------------
-- depends_on_sequences references sibling `sequence` values and is resolved into real
-- DEPENDS_ON links by ProcessInstantiator.

-- EMPLOYEE_ONBOARDING: nine tasks across HR, IT and Finance. This is Demo 1.
INSERT INTO process_template_tasks (
    id, process_template_id, sequence, title, description, work_item_type,
    department_id, team_id, estimated_hours, priority, required_skill_codes,
    depends_on_sequences, requires_approval) VALUES
    ('00000000-0000-7000-8000-000000010001', '00000000-0000-7000-8000-00000000f001', 1,
     'Create HR personnel file and offer paperwork',
     'Open the personnel record and prepare the signed offer documentation.', 'TASK',
     '00000000-0000-7000-8000-00000000a101', '00000000-0000-7000-8000-00000000a205',
     1.50, 'HIGH', 'HR_ONBOARDING', NULL, FALSE),
    ('00000000-0000-7000-8000-000000010002', '00000000-0000-7000-8000-00000000f001', 2,
     'Send welcome email with first-day logistics',
     'Confirm start time, location, dress code and who to ask for on arrival.', 'TASK',
     '00000000-0000-7000-8000-00000000a101', '00000000-0000-7000-8000-00000000a205',
     0.50, 'MEDIUM', 'HR_ONBOARDING', '1', FALSE),
    ('00000000-0000-7000-8000-000000010003', '00000000-0000-7000-8000-00000000f001', 3,
     'Provision Active Directory account and email',
     'Create the domain account, mailbox and distribution group memberships.', 'TASK',
     '00000000-0000-7000-8000-00000000a102', '00000000-0000-7000-8000-00000000a201',
     1.00, 'HIGH', 'ACTIVE_DIRECTORY,USER_PROVISIONING', '1', FALSE),
    ('00000000-0000-7000-8000-000000010004', '00000000-0000-7000-8000-00000000f001', 4,
     'Assign Microsoft 365 licence and mailbox',
     'Allocate a licence from the pool and confirm mailbox delivery.', 'TASK',
     '00000000-0000-7000-8000-00000000a102', '00000000-0000-7000-8000-00000000a201',
     0.50, 'MEDIUM', 'MICROSOFT_365,USER_PROVISIONING', '3', FALSE),
    ('00000000-0000-7000-8000-000000010005', '00000000-0000-7000-8000-00000000f001', 5,
     'Order and configure laptop and peripherals',
     'Order the standard build, image it and install role-specific tooling.', 'TASK',
     '00000000-0000-7000-8000-00000000a102', '00000000-0000-7000-8000-00000000a201',
     3.00, 'HIGH', 'HARDWARE_SUPPORT', '1', FALSE),
    -- The one task that touches money. requires_approval is what makes Demo 1 step 7
    -- stop for a human while everything around it assigns itself.
    ('00000000-0000-7000-8000-000000010006', '00000000-0000-7000-8000-00000000f001', 6,
     'Purchase desk setup and office equipment',
     'Raise the purchase order for desk, chair and accessories.', 'TASK',
     '00000000-0000-7000-8000-00000000a103', '00000000-0000-7000-8000-00000000a206',
     1.00, 'MEDIUM', 'PROCUREMENT', '1', TRUE),
    ('00000000-0000-7000-8000-000000010007', '00000000-0000-7000-8000-00000000f001', 7,
     'Grant building and system access badges',
     'Issue the building badge and enrol the starter in door groups.', 'TASK',
     '00000000-0000-7000-8000-00000000a102', '00000000-0000-7000-8000-00000000a201',
     0.50, 'MEDIUM', 'USER_PROVISIONING', '3', FALSE),
    ('00000000-0000-7000-8000-000000010008', '00000000-0000-7000-8000-00000000f001', 8,
     'Schedule first-week onboarding meetings',
     'Book the team introduction, buddy pairing and week-one check-in.', 'TASK',
     '00000000-0000-7000-8000-00000000a101', '00000000-0000-7000-8000-00000000a205',
     1.50, 'MEDIUM', 'HR_ONBOARDING', '2', FALSE),
    ('00000000-0000-7000-8000-000000010009', '00000000-0000-7000-8000-00000000f001', 9,
     'Confirm payroll setup and benefits enrollment',
     'Register the starter for payroll and confirm benefits elections.', 'TASK',
     '00000000-0000-7000-8000-00000000a103', '00000000-0000-7000-8000-00000000a206',
     2.00, 'HIGH', 'PAYROLL', '1', FALSE)
ON CONFLICT (process_template_id, sequence) DO NOTHING;

-- EMPLOYEE_OFFBOARDING
INSERT INTO process_template_tasks (
    id, process_template_id, sequence, title, description, work_item_type,
    department_id, team_id, estimated_hours, priority, required_skill_codes,
    depends_on_sequences, requires_approval) VALUES
    ('00000000-0000-7000-8000-000000010101', '00000000-0000-7000-8000-00000000f002', 1,
     'Confirm last working day and notice terms', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a101', '00000000-0000-7000-8000-00000000a205',
     1.00, 'HIGH', 'HR_ONBOARDING', NULL, FALSE),
    ('00000000-0000-7000-8000-000000010102', '00000000-0000-7000-8000-00000000f002', 2,
     'Revoke system and application access', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a102', '00000000-0000-7000-8000-00000000a201',
     1.50, 'CRITICAL', 'ACTIVE_DIRECTORY,USER_PROVISIONING,SECURITY', '1', FALSE),
    ('00000000-0000-7000-8000-000000010103', '00000000-0000-7000-8000-00000000f002', 3,
     'Collect hardware and building badge', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a102', '00000000-0000-7000-8000-00000000a201',
     1.00, 'HIGH', 'HARDWARE_SUPPORT', '1', FALSE),
    ('00000000-0000-7000-8000-000000010104', '00000000-0000-7000-8000-00000000f002', 4,
     'Process final payroll and settle expenses', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a103', '00000000-0000-7000-8000-00000000a206',
     2.00, 'HIGH', 'PAYROLL', '1', TRUE),
    ('00000000-0000-7000-8000-000000010105', '00000000-0000-7000-8000-00000000f002', 5,
     'Conduct exit interview and record feedback', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a101', '00000000-0000-7000-8000-00000000a205',
     1.00, 'MEDIUM', 'HR_ONBOARDING', '1', FALSE)
ON CONFLICT (process_template_id, sequence) DO NOTHING;

-- ACCESS_REQUEST
INSERT INTO process_template_tasks (
    id, process_template_id, sequence, title, description, work_item_type,
    department_id, team_id, estimated_hours, priority, required_skill_codes,
    depends_on_sequences, requires_approval) VALUES
    ('00000000-0000-7000-8000-000000010201', '00000000-0000-7000-8000-00000000f003', 1,
     'Verify the requester''s entitlement to this access', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a102', '00000000-0000-7000-8000-00000000a201',
     0.50, 'MEDIUM', 'SECURITY,USER_PROVISIONING', NULL, TRUE),
    ('00000000-0000-7000-8000-000000010202', '00000000-0000-7000-8000-00000000f003', 2,
     'Grant the access and confirm with the requester', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a102', '00000000-0000-7000-8000-00000000a201',
     0.50, 'MEDIUM', 'USER_PROVISIONING', '1', FALSE)
ON CONFLICT (process_template_id, sequence) DO NOTHING;

-- HARDWARE_REQUEST
INSERT INTO process_template_tasks (
    id, process_template_id, sequence, title, description, work_item_type,
    department_id, team_id, estimated_hours, priority, required_skill_codes,
    depends_on_sequences, requires_approval) VALUES
    ('00000000-0000-7000-8000-000000010301', '00000000-0000-7000-8000-00000000f004', 1,
     'Confirm specification and check stock', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a102', '00000000-0000-7000-8000-00000000a201',
     0.50, 'MEDIUM', 'HARDWARE_SUPPORT', NULL, FALSE),
    ('00000000-0000-7000-8000-000000010302', '00000000-0000-7000-8000-00000000f004', 2,
     'Raise purchase order if not in stock', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a103', '00000000-0000-7000-8000-00000000a206',
     1.00, 'MEDIUM', 'PROCUREMENT', '1', TRUE),
    ('00000000-0000-7000-8000-000000010303', '00000000-0000-7000-8000-00000000f004', 3,
     'Configure the device and hand it over', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a102', '00000000-0000-7000-8000-00000000a201',
     2.00, 'MEDIUM', 'HARDWARE_SUPPORT', '1,2', FALSE)
ON CONFLICT (process_template_id, sequence) DO NOTHING;

-- SOFTWARE_INSTALLATION
INSERT INTO process_template_tasks (
    id, process_template_id, sequence, title, description, work_item_type,
    department_id, team_id, estimated_hours, priority, required_skill_codes,
    depends_on_sequences, requires_approval) VALUES
    ('00000000-0000-7000-8000-000000010401', '00000000-0000-7000-8000-00000000f005', 1,
     'Check licence availability and compliance', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a102', '00000000-0000-7000-8000-00000000a201',
     0.50, 'LOW', 'MICROSOFT_365', NULL, FALSE),
    ('00000000-0000-7000-8000-000000010402', '00000000-0000-7000-8000-00000000f005', 2,
     'Install the package and verify it launches', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a102', '00000000-0000-7000-8000-00000000a201',
     0.50, 'LOW', 'HARDWARE_SUPPORT', '1', FALSE)
ON CONFLICT (process_template_id, sequence) DO NOTHING;

-- PURCHASE_REQUEST
INSERT INTO process_template_tasks (
    id, process_template_id, sequence, title, description, work_item_type,
    department_id, team_id, estimated_hours, priority, required_skill_codes,
    depends_on_sequences, requires_approval) VALUES
    ('00000000-0000-7000-8000-000000010501', '00000000-0000-7000-8000-00000000f006', 1,
     'Collect quotes and confirm budget line', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a103', '00000000-0000-7000-8000-00000000a206',
     2.00, 'MEDIUM', 'PROCUREMENT', NULL, TRUE),
    ('00000000-0000-7000-8000-000000010502', '00000000-0000-7000-8000-00000000f006', 2,
     'Raise the purchase order with the vendor', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a103', '00000000-0000-7000-8000-00000000a206',
     1.00, 'MEDIUM', 'PROCUREMENT', '1', FALSE),
    ('00000000-0000-7000-8000-000000010503', '00000000-0000-7000-8000-00000000f006', 3,
     'Receipt the goods and reconcile the invoice', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a103', '00000000-0000-7000-8000-00000000a206',
     1.00, 'LOW', 'PROCUREMENT', '2', FALSE)
ON CONFLICT (process_template_id, sequence) DO NOTHING;

-- PRODUCTION_INCIDENT
INSERT INTO process_template_tasks (
    id, process_template_id, sequence, title, description, work_item_type,
    department_id, team_id, estimated_hours, priority, required_skill_codes,
    depends_on_sequences, requires_approval) VALUES
    ('00000000-0000-7000-8000-000000010601', '00000000-0000-7000-8000-00000000f007', 1,
     'Acknowledge and assess customer impact', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a104', '00000000-0000-7000-8000-00000000a204',
     0.50, 'CRITICAL', 'INCIDENT_RESPONSE', NULL, FALSE),
    ('00000000-0000-7000-8000-000000010602', '00000000-0000-7000-8000-00000000f007', 2,
     'Apply mitigation to restore service', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a104', '00000000-0000-7000-8000-00000000a204',
     2.00, 'CRITICAL', 'INCIDENT_RESPONSE', '1', FALSE),
    ('00000000-0000-7000-8000-000000010603', '00000000-0000-7000-8000-00000000f007', 3,
     'Identify the root cause of the incident',
     'Time-boxed investigation producing findings, not a fix.', 'RESEARCH',
     '00000000-0000-7000-8000-00000000a104', '00000000-0000-7000-8000-00000000a202',
     4.00, 'HIGH', 'INCIDENT_RESPONSE,JAVA', '2', FALSE),
    ('00000000-0000-7000-8000-000000010604', '00000000-0000-7000-8000-00000000f007', 4,
     'Implement and release the permanent fix', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a104', '00000000-0000-7000-8000-00000000a202',
     6.00, 'HIGH', 'JAVA,SPRING_BOOT', '3', TRUE),
    ('00000000-0000-7000-8000-000000010605', '00000000-0000-7000-8000-00000000f007', 5,
     'Write the post-incident review', NULL, 'DOCUMENTATION',
     '00000000-0000-7000-8000-00000000a104', '00000000-0000-7000-8000-00000000a204',
     2.00, 'MEDIUM', 'INCIDENT_RESPONSE', '3,4', FALSE)
ON CONFLICT (process_template_id, sequence) DO NOTHING;

-- CUSTOMER_COMPLAINT
INSERT INTO process_template_tasks (
    id, process_template_id, sequence, title, description, work_item_type,
    department_id, team_id, estimated_hours, priority, required_skill_codes,
    depends_on_sequences, requires_approval) VALUES
    ('00000000-0000-7000-8000-000000010701', '00000000-0000-7000-8000-00000000f008', 1,
     'Acknowledge the complaint within the SLA window', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a102', '00000000-0000-7000-8000-00000000a201',
     0.50, 'HIGH', 'INCIDENT_RESPONSE', NULL, FALSE),
    ('00000000-0000-7000-8000-000000010702', '00000000-0000-7000-8000-00000000f008', 2,
     'Investigate what happened and gather evidence', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a102', '00000000-0000-7000-8000-00000000a201',
     2.00, 'HIGH', 'INCIDENT_RESPONSE', '1', FALSE),
    ('00000000-0000-7000-8000-000000010703', '00000000-0000-7000-8000-00000000f008', 3,
     'Respond to the customer with the outcome', NULL, 'TASK',
     '00000000-0000-7000-8000-00000000a102', '00000000-0000-7000-8000-00000000a201',
     1.00, 'HIGH', 'INCIDENT_RESPONSE', '2', FALSE)
ON CONFLICT (process_template_id, sequence) DO NOTHING;
