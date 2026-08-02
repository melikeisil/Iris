package com.smartwork.identity.domain;

/**
 * Global roles - axis 1 of authorization.
 *
 * <p>REQUESTER, ASSIGNEE, PROCESS_OWNER, WATCHER, APPROVER and REVIEWER are deliberately
 * absent: they are per-resource relationships (axis 2), derived at evaluation time. See
 * docs/03-roles-and-authorization.md.
 */
public enum SystemRole {
    EMPLOYEE,
    TEAM_LEAD,
    MANAGER,
    ADMIN
}
