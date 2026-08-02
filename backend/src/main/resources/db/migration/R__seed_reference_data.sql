-- Repeatable: global reference data that is not tenant-specific.
-- Re-runs whenever this file's checksum changes.

INSERT INTO roles (id, code, name) VALUES
    ('00000000-0000-7000-8000-000000000001', 'EMPLOYEE',  'Employee'),
    ('00000000-0000-7000-8000-000000000002', 'TEAM_LEAD', 'Team Lead'),
    ('00000000-0000-7000-8000-000000000003', 'MANAGER',   'Manager'),
    ('00000000-0000-7000-8000-000000000004', 'ADMIN',     'Administrator')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;
