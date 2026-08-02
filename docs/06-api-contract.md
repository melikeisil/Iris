# 06 — REST API Contract

Base path `/api/v1`. All requests and responses `application/json`. Errors `application/problem+json`.

## 1. Cross-cutting conventions

### Authentication

```
Authorization: Bearer <access-jwt>
X-Correlation-Id: <optional client-supplied uuid>
```

Access token TTL 15 minutes; refresh token TTL 7 days, rotating and single-use.

### Error model (RFC 7807 + a stable `code`)

```json
{
  "type": "https://smartwork.ai/errors/invalid-transition",
  "title": "Invalid workflow transition",
  "status": 422,
  "code": "INVALID_TRANSITION",
  "detail": "Transition APPROVE_REVIEW is not available from state IN_PROGRESS.",
  "instance": "/api/v1/work-items/018f.../transition",
  "correlationId": "9c1e...",
  "timestamp": "2026-08-01T10:22:31Z",
  "errors": [
    { "field": "transition", "code": "NOT_AVAILABLE", "message": "Available: SUBMIT_FOR_REVIEW, BLOCK" }
  ]
}
```

`code` is the contract; `title` and `detail` are human text and may change.

| HTTP | Codes |
|---|---|
| 400 | `VALIDATION_FAILED`, `LINK_CYCLE_DETECTED`, `INVALID_LINK_TYPE`, `MALFORMED_FILTER` |
| 401 | `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `REFRESH_TOKEN_REUSED` |
| 403 | `FORBIDDEN`, `TRANSITION_NOT_PERMITTED`, `ROUTE_NOT_PERMITTED`, `LEVEL_RESTRICTION_VIOLATED` |
| 404 | `NOT_FOUND` (also returned for items the caller may not see) |
| 409 | `CONCURRENT_MODIFICATION`, `DUPLICATE_RESOURCE`, `ALREADY_DECIDED` |
| 422 | `INVALID_TRANSITION`, `TRANSITION_GUARD_FAILED`, `RECOMMENDATION_STALE`, `SPRINT_CAPACITY_EXCEEDED`, `TYPE_RULE_VIOLATED` |
| 429 | `RATE_LIMITED` |
| 502/503 | `AI_SERVICE_UNAVAILABLE`, `JIRA_UNAVAILABLE` |

### Pagination, filtering, sorting

```
GET /api/v1/work-items?page=0&size=25&sort=priority,desc&sort=dueDate,asc
    &type=STORY,BUG&status=IN_PROGRESS&teamId=...&assigneeId=...
    &priority=HIGH,CRITICAL&family=DEVELOPMENT_WORK&origin=TICKET_GENERATED
    &sprintId=...&projectId=...&parentId=...&dueBefore=2026-08-15
    &q=profile%20photo&hasBlocked=true&unassigned=true
```

```json
{
  "content": [ /* items */ ],
  "page": { "number": 0, "size": 25, "totalElements": 143, "totalPages": 6 },
  "sort": [ { "field": "priority", "direction": "DESC" } ]
}
```

Max `size` 100. Filters are AND across parameters, OR within a comma list. `q` runs trigram search on summary and description. Every list endpoint applies the caller's visibility predicate **in SQL**, so `totalElements` never leaks invisible items.

### Concurrency

Mutating endpoints on versioned aggregates accept `If-Match: "<version>"` or a `version` field in the body. Mismatch → `409 CONCURRENT_MODIFICATION` with the current representation in `detail`.

## 2. Authentication

| Method | Path | Body → Response |
|---|---|---|
| POST | `/auth/login` | `{email, password}` → `{accessToken, refreshToken, expiresIn, user}` |
| POST | `/auth/refresh` | `{refreshToken}` → new pair; reuse of a consumed token revokes the family and returns `401 REFRESH_TOKEN_REUSED` |
| GET | `/auth/me` | → principal, roles, scope, capabilities |
| POST | `/auth/logout` | `{refreshToken}` → 204, revokes the family |

```json
// GET /auth/me
{
  "id": "018f...",
  "email": "ayse.demir@acme.io",
  "fullName": "Ayşe Demir",
  "employeeLevel": "SENIOR",
  "organizationId": "018f...",
  "department": { "id": "018f...", "code": "ENGINEERING", "name": "Engineering" },
  "teams": [ { "id": "018f...", "code": "BACKEND", "name": "Backend Team", "isLead": true } ],
  "roles": ["EMPLOYEE", "TEAM_LEAD"],
  "organizationWide": false,
  "capabilities": ["work_item.create", "team.review_queue", "team.assign", "sprint.manage"],
  "processOwnerOf": { "count": 2 }
}
```

`roles` drives navigation; `capabilities` drives affordances. Neither is trusted by the backend.

## 3. Work items

| Method | Path | Purpose |
|---|---|---|
| POST | `/work-items` | create (any permitted type) |
| GET | `/work-items` | search / list with filters |
| GET | `/work-items/{id}` | detail incl. `permissions` and `availableTransitions` |
| PUT | `/work-items/{id}` | update mutable fields |
| GET | `/work-items/my` | assigned to me, board-shaped |
| GET | `/work-items/created-by-me` | where I am REQUESTER |
| GET | `/work-items/watching` | where I am WATCHER |
| GET | `/work-items/{id}/children` | direct children + progress |
| GET | `/work-items/{id}/links` | both directions, rendered |
| POST | `/work-items/{id}/links` | create a typed link |
| DELETE | `/work-items/{id}/links/{linkId}` | remove a link |
| POST | `/work-items/{id}/transition` | named transition |
| POST | `/work-items/{id}/block` | convenience for `BLOCK` |
| POST | `/work-items/{id}/complete` | convenience for the terminal transition |
| POST | `/work-items/{id}/request-reassignment` | employee-initiated, creates an approval request |
| POST | `/work-items/{id}/additional-information` | answer clarification questions |
| POST | `/work-items/{id}/comments` | comment |
| POST | `/work-items/{id}/attachments` | multipart upload |
| PUT | `/work-items/{id}/remaining-hours` | update remaining effort |
| POST | `/work-items/{id}/watch` / `DELETE` | watch / unwatch |

### Create

```json
// POST /work-items
{
  "workItemType": "STORY",
  "summary": "Users must be able to upload a profile photo",
  "description": "As a user I want to upload an avatar so that my profile is recognisable.",
  "acceptanceCriteria": null,
  "projectId": "018f...",
  "parentId": "018f...",
  "priority": "MEDIUM",
  "dueDate": "2026-08-22",
  "estimatedHours": null,
  "storyPoints": null,
  "departmentId": null,
  "teamId": null,
  "requestedSkillCodes": [],
  "originType": "USER_CREATED"
}
```

Server-controlled and rejected if supplied: `status`, `workFamily`, `workflowDefinitionId`, `assigneeId`, `aiConfidence`, `riskLevel`, `referenceKey`, `jiraIssueKey`, all sensitivity flags.

```json
// 201 Created
{
  "id": "018f...",
  "referenceKey": "DEV-231",
  "workItemType": "STORY",
  "workFamily": "DEVELOPMENT_WORK",
  "originType": "USER_CREATED",
  "status": "BACKLOG",
  "statusCategory": "TODO",
  "analysisState": "QUEUED",
  "permissions": { "canEdit": true, "canAssign": false, "canApprove": false },
  "availableTransitions": []
}
```

### Detail

```json
// GET /work-items/{id}
{
  "id": "018f...",
  "referenceKey": "DEV-231",
  "workItemType": "STORY",
  "workFamily": "DEVELOPMENT_WORK",
  "originType": "USER_CREATED",
  "status": "IN_PROGRESS",
  "statusCategory": "IN_PROGRESS",
  "summary": "Users must be able to upload a profile photo",
  "description": "...",
  "acceptanceCriteria": "Given ... When ... Then ...",
  "priority": "MEDIUM",
  "priorityScore": 62,
  "riskLevel": "MEDIUM",
  "complexity": "MEDIUM",
  "estimatedHours": 20.0,
  "remainingHours": 12.0,
  "storyPoints": 8,
  "dueDate": "2026-08-22",
  "progressPercent": 40.0,
  "progressStrategy": "STORY_POINT_WEIGHTED",
  "aiConfidence": 0.87,
  "sensitivity": {
    "securitySensitive": false, "productionImpacting": false,
    "budgetSensitive": false, "complianceSensitive": false
  },
  "project": { "id": "018f...", "code": "IDENTITY_PLATFORM", "name": "Identity Platform" },
  "sprint": { "id": "018f...", "name": "Sprint 24", "endDate": "2026-08-14" },
  "department": { "id": "018f...", "code": "ENGINEERING" },
  "team": { "id": "018f...", "code": "PROFILE_TEAM" },
  "assignee": { "id": "018f...", "fullName": "Mert Kaya", "employeeLevel": "MID" },
  "reporter": { "id": "018f...", "fullName": "Ayşe Demir" },
  "processOwner": null,
  "parent": { "id": "018f...", "referenceKey": "DEV-200", "workItemType": "EPIC", "summary": "Profile revamp" },
  "childrenSummary": { "total": 5, "done": 2, "blocked": 1 },
  "requiredSkills": [
    { "code": "SPRING_BOOT", "name": "Spring Boot", "minProficiency": 3, "mandatory": true, "source": "AI" },
    { "code": "OBJECT_STORAGE", "name": "Object Storage", "minProficiency": 2, "mandatory": false, "source": "AI" }
  ],
  "affectedComponents": ["profile-api", "frontend-profile", "object-storage"],
  "jira": { "issueKey": "PLAT-88", "issueId": "10231", "syncStatus": "IN_SYNC", "url": "https://acme.atlassian.net/browse/PLAT-88" },
  "blocked": null,
  "watching": false,
  "permissions": {
    "canEdit": true, "canAssign": false, "canApprove": false,
    "canComment": true, "canDelete": false
  },
  "availableTransitions": [
    { "code": "SUBMIT_FOR_REVIEW", "label": "Submit for review", "toState": "CODE_REVIEW", "requiresReason": false },
    { "code": "BLOCK", "label": "Block", "toState": "BLOCKED", "requiresReason": true }
  ],
  "createdAt": "2026-07-28T09:12:00Z",
  "updatedAt": "2026-08-01T08:03:11Z",
  "version": 7
}
```

`availableTransitions` is computed for **this actor on this item** — the UI renders exactly the buttons the backend will honour.

### Transition, block, links

```json
// POST /work-items/{id}/transition
{ "transition": "SUBMIT_FOR_REVIEW", "reason": null, "reviewerId": "018f...", "version": 7 }

// POST /work-items/{id}/block
{ "reason": "Waiting for the S3 bucket policy from Platform team", "blockedByWorkItemId": "018f..." }

// POST /work-items/{id}/links
{ "linkType": "DEPENDS_ON", "targetId": "018f...", "note": "Needs the storage bucket first" }

// GET /work-items/{id}/links  → rendered in both directions
{
  "outgoing": [ { "linkId": "018f...", "linkType": "DEPENDS_ON",
                  "target": { "id": "018f...", "referenceKey": "DEV-458", "summary": "Provision bucket", "status": "IN_PROGRESS" },
                  "blocking": true } ],
  "incoming": [ { "linkId": "018f...", "linkType": "RESOLVES",
                  "source": { "id": "018f...", "referenceKey": "TCK-102", "workItemType": "REQUEST" } } ]
}
```

### Additional information

```json
// POST /work-items/{id}/additional-information
{
  "answers": [
    { "questionId": "q1", "answer": "Max 5 MB, JPEG and PNG only." },
    { "questionId": "q2", "answer": "Yes, existing avatars must be migrated." }
  ]
}
// → 202 Accepted { "status": "WAITING_AI_REANALYSIS", "reanalysisQueued": true }
```

## 4. AI analysis

| Method | Path | Notes |
|---|---|---|
| POST | `/work-items/{id}/analyze` | 202; idempotent while an analysis is in flight |
| POST | `/work-items/{id}/reanalyze` | 202; supersedes the current analysis |
| GET | `/work-items/{id}/analysis` | latest analysis, or 404 if never analyzed |
| GET | `/work-items/{id}/analysis/history` | superseded analyses, for AI metrics |
| POST | `/work-items/{id}/generate-plan` | 202; decomposition into child items |

```json
// GET /work-items/{id}/analysis
{
  "recommendationId": "018f...",
  "status": "PENDING",
  "generatedAt": "2026-07-28T09:12:44Z",
  "provider": "anthropic",
  "model": "claude-sonnet-5",
  "latencyMs": 3120,
  "isMock": false,
  "stale": false,
  "analysis": {
    "workFamily": "DEVELOPMENT_WORK",
    "workItemType": "STORY",
    "category": "PROFILE_MANAGEMENT",
    "summary": "User profile image upload",
    "projectCode": "IDENTITY_PLATFORM",
    "recommendedTeam": "PROFILE_TEAM",
    "departments": [
      { "departmentCode": "ENGINEERING", "confidence": 0.96, "reason": "The work requires software development." }
    ],
    "affectedComponents": ["profile-api", "frontend-profile", "object-storage"],
    "requiredSkills": ["SPRING_BOOT", "REACT", "OBJECT_STORAGE", "SECURITY"],
    "priority": "MEDIUM",
    "priorityScore": 62,
    "riskLevel": "MEDIUM",
    "riskSignals": [],
    "complexity": "MEDIUM",
    "suggestedStoryPoints": 8,
    "requiresMoreInformation": false,
    "clarificationQuestions": [],
    "requiresDecomposition": true,
    "matchedProcessTemplate": null,
    "estimatedTotalHours": 20,
    "confidence": 0.87,
    "suggestedWorkItems": []
  },
  "unresolvedSkills": [],
  "explanation": "Classified as development work because the request describes a user-facing capability requiring API, UI and storage changes."
}
```

The `analysis` object is byte-for-byte the AI service's validated response — no reshaping, so the contract is verifiable end-to-end.

```json
// clarification case
{
  "analysis": {
    "requiresMoreInformation": true,
    "clarificationQuestions": [
      { "id": "q1", "question": "What is the maximum accepted file size and which formats?", "field": "acceptanceCriteria", "required": true },
      { "id": "q2", "question": "Should existing profile images be migrated?", "field": "scope", "required": false }
    ],
    "confidence": 0.52
  }
}
```

## 5. Recommendations

| Method | Path | Purpose |
|---|---|---|
| GET | `/recommendations/pending` | queue scoped to the caller's routes |
| GET | `/recommendations/{id}` | full detail incl. candidates and score components |
| PUT | `/recommendations/{id}` | edit before approving (Team Lead / Manager) |
| POST | `/recommendations/{id}/approve` | approve, optionally overriding the assignee |
| POST | `/recommendations/{id}/reject` | reject with a required reason |
| POST | `/recommendations/{id}/regenerate` | discard and re-run |

```json
// GET /recommendations/{id}
{
  "id": "018f...",
  "workItem": { "id": "018f...", "referenceKey": "DEV-457", "summary": "Implement avatar upload endpoint", "workItemType": "TASK" },
  "type": "ASSIGNMENT",
  "status": "AWAITING_APPROVAL",
  "confidence": 0.78,
  "stale": false,
  "routing": {
    "decision": "TEAM_LEAD_REVIEW",
    "reasonCodes": ["CONFIDENCE_BELOW_AUTO_THRESHOLD", "CANDIDATE_SCORES_CLOSE"],
    "explanation": "AI confidence 0.78 is below the 0.85 auto-assign threshold and the top two candidates are within 3 points.",
    "approvalRequestId": "018f...",
    "requiredActor": { "type": "TEAM_SCOPE", "teamId": "018f...", "teamName": "Backend Team" }
  },
  "candidates": [
    {
      "candidateUserId": "018f...", "fullName": "Mert Kaya", "employeeLevel": "MID",
      "rank": 1, "totalScore": 91,
      "skillScore": 95, "teamScore": 100, "capacityScore": 82,
      "experienceScore": 90, "componentScore": 88, "deadlineScore": 90, "fairnessScore": 80,
      "workloadRatio": 0.62,
      "explanation": "Strong skill, component and capacity match."
    },
    { "candidateUserId": "018f...", "fullName": "Deniz Aslan", "rank": 2, "totalScore": 88, "...": "..." },
    { "candidateUserId": "018f...", "fullName": "Ece Yıldız", "rank": 3, "totalScore": 74, "...": "..." }
  ],
  "excludedCandidates": [
    { "candidateUserId": "018f...", "fullName": "Can Öztürk", "reason": "LEVEL_RESTRICTION_PRODUCTION_ACCESS" }
  ],
  "suggestedPlan": null,
  "generatedAt": "2026-07-28T09:13:02Z",
  "version": 1
}
```

```json
// POST /recommendations/{id}/approve
{
  "assigneeIdOverride": "018f...",
  "note": "Deniz has the storage context from last sprint.",
  "addToSprintId": "018f...",
  "version": 1
}
// → 200 { "status": "APPROVED", "assignment": {...}, "jiraSyncQueued": true }
// → 422 RECOMMENDATION_STALE if the work item changed in Jira since generation
```

```json
// PUT /recommendations/{id} — edit the plan before approving
{
  "suggestedPlan": {
    "workItems": [
      { "tempId": "t1", "title": "Add POST /profile/avatar endpoint", "workItemType": "TASK",
        "teamCode": "BACKEND", "estimatedHours": 6, "requiredSkills": ["SPRING_BOOT", "OBJECT_STORAGE"],
        "dependencies": [], "testingRequirements": ["Unit test for size and MIME validation"] },
      { "tempId": "t2", "title": "Avatar upload UI with crop preview", "workItemType": "TASK",
        "teamCode": "FRONTEND", "estimatedHours": 8, "requiredSkills": ["REACT", "TYPESCRIPT"],
        "dependencies": ["t1"], "testingRequirements": ["Component test for validation errors"] }
    ]
  },
  "editReason": "Split storage provisioning into the platform team's backlog instead.",
  "version": 1
}
```

Every edit is recorded and feeds the "Team Lead edit rate" and "Process Owner decomposition edit rate" AI metrics.

## 6. Team Lead

| Method | Path |
|---|---|
| GET | `/team-lead/dashboard` |
| GET | `/team-lead/backlog` |
| GET | `/team-lead/workload` |
| GET | `/team-lead/review-queue` |
| GET | `/team-lead/unassigned` |
| GET | `/team-lead/blocked` |
| GET | `/team-lead/overdue` |
| POST | `/team-lead/work-items/{id}/assign` |
| POST | `/team-lead/work-items/{id}/redirect` |
| POST | `/team-lead/work-items/{id}/add-to-sprint` |
| DELETE | `/team-lead/work-items/{id}/sprint` |
| POST | `/team-lead/work-items/{id}/escalate` |

```json
// GET /team-lead/workload?teamId=...
{
  "team": { "id": "018f...", "code": "BACKEND", "name": "Backend Team" },
  "sprint": { "id": "018f...", "name": "Sprint 24", "remainingDays": 6 },
  "teamCapacityHours": 240, "committedHours": 198, "utilizationRatio": 0.825,
  "members": [
    { "userId": "018f...", "fullName": "Mert Kaya", "employeeLevel": "MID",
      "capacityHours": 60, "assignedHours": 37, "remainingHours": 22,
      "activeStoryPoints": 8, "activeItems": 4, "reviewItems": 2,
      "workloadRatio": 0.62, "status": "HEALTHY", "blockedItems": 0 },
    { "userId": "018f...", "fullName": "Deniz Aslan", "workloadRatio": 0.94, "status": "OVERLOADED", "...": "..." }
  ],
  "skillCoverage": [
    { "skillCode": "SPRING_BOOT", "membersWithSkill": 4, "coverage": "GOOD" },
    { "skillCode": "OBJECT_STORAGE", "membersWithSkill": 1, "coverage": "AT_RISK" }
  ]
}
```

`status` is a capacity band (`UNDERUTILIZED` < 0.5, `HEALTHY` 0.5–0.85, `HIGH` 0.85–1.0, `OVERLOADED` > 1.0) — a workload signal, never a performance rating.

```json
// POST /team-lead/work-items/{id}/assign
{ "assigneeId": "018f...", "reason": "Better storage context", "version": 4 }

// POST /team-lead/work-items/{id}/redirect
{ "targetDepartmentId": "018f...", "targetTeamId": "018f...",
  "reason": "This is an identity provisioning task, not application development." }
// crossing a department boundary creates a PROCESS_OWNER_REVIEW approval request

// POST /team-lead/work-items/{id}/escalate
{ "reason": "Needs budget approval above my authority — vendor licence required.", "toRoute": "MANAGER_APPROVAL" }
```

## 7. Process Owner

| Method | Path |
|---|---|
| GET | `/process-owner/dashboard` |
| GET | `/process-owner/work-items` |
| GET | `/process-owner/work-items/{id}/plan` |
| GET | `/process-owner/dependencies` |
| GET | `/process-owner/waiting-verification` |
| POST | `/process-owner/work-items/{id}/approve-plan` |
| POST | `/process-owner/work-items/{id}/verify` |
| POST | `/process-owner/work-items/{id}/close` |
| POST | `/process-owner/work-items/{id}/escalate` |

```json
// GET /process-owner/work-items/{id}/plan
{
  "rootWorkItem": { "id": "018f...", "referenceKey": "TCK-118", "summary": "Onboard Selin Arslan starting Monday", "status": "PLANNING" },
  "matchedTemplate": { "code": "EMPLOYEE_ONBOARDING", "name": "Employee Onboarding", "customized": true },
  "businessObjective": "New joiner productive on day one with accounts, hardware and payroll in place.",
  "departmentsInvolved": ["HR", "IT", "FINANCE"],
  "progress": { "percent": 0, "strategy": "ESTIMATE_WEIGHTED", "totalHours": 14.5 },
  "children": [
    { "id": "018f...", "referenceKey": "TCK-119", "title": "Create employee record in HRIS",
      "department": "HR", "team": "HR_OPS", "estimatedHours": 1.5, "priority": "HIGH",
      "route": "AUTO_ASSIGN", "assignee": { "fullName": "Zeynep Koç" }, "dependencies": [] },
    { "id": "018f...", "referenceKey": "TCK-121", "title": "Provision laptop and peripherals",
      "department": "IT", "team": "SUPPORT", "estimatedHours": 3,
      "route": "TEAM_LEAD_REVIEW", "assignee": null,
      "reasonCodes": ["BUDGET_IMPACT"], "dependencies": ["TCK-119"] }
  ],
  "crossTeamDependencies": [
    { "from": "TCK-121", "to": "TCK-119", "type": "DEPENDS_ON", "riskNote": "IT cannot provision before the HR record exists." }
  ],
  "permissions": { "canApprovePlan": true, "canEditPlan": true, "canClose": false }
}
```

```json
// POST /process-owner/work-items/{id}/approve-plan
{ "editedChildren": [ { "id": "018f...", "estimatedHours": 4, "priority": "HIGH" } ],
  "removedChildIds": [], "note": "Increased laptop provisioning estimate — model is on back-order." }
```

## 8. Manager

| Method | Path |
|---|---|
| GET | `/manager/dashboard` |
| GET | `/manager/critical-work` |
| GET | `/manager/escalations` |
| GET | `/manager/sla-risk` |
| GET | `/manager/capacity` |
| GET | `/manager/project-risk` |
| GET | `/manager/reports` |
| POST | `/manager/escalations/{id}/resolve` |
| PUT | `/manager/automation-policies/{id}` |

```json
// GET /manager/dashboard
{
  "scope": { "type": "DEPARTMENT", "departmentId": "018f...", "name": "Engineering" },
  "criticalOpen": 3,
  "escalationsOpen": 2,
  "slaAtRisk": 5,
  "slaBreached": 1,
  "capacity": { "totalHours": 960, "committedHours": 874, "utilizationRatio": 0.91, "overloadedMembers": 3 },
  "sprintRisk": [ { "sprintId": "018f...", "name": "Sprint 24", "risk": "MEDIUM",
                    "reason": "18% of committed points are blocked with 6 days remaining." } ],
  "plannedVsUnplanned": { "plannedHours": 612, "unplannedHours": 262, "unplannedRatio": 0.30 },
  "aiMetrics": { "autoAssignmentRate": 0.61, "teamLeadEditRate": 0.18,
                 "managerEscalationRate": 0.04, "lowConfidenceRate": 0.11 }
}
```

```json
// PUT /manager/automation-policies/{id}  — thresholds only; Admin owns the rules
{ "minAutoAssignConfidence": 0.90, "maxAutoAssignWorkload": 0.75,
  "autoAssignMaxRisk": "LOW", "reason": "Tightening after a mis-assignment last sprint.", "version": 3 }
```

## 9. Sprints and backlog

| Method | Path |
|---|---|
| GET | `/sprints` · `/sprints/{id}` · `/sprints/current` |
| POST | `/sprints` · `POST /sprints/{id}/start` · `POST /sprints/{id}/close` |
| GET | `/sprints/{id}/board` |
| GET | `/sprints/{id}/metrics` |
| GET | `/sprints/{id}/burndown` |
| GET | `/backlog?projectId=` · `PUT /backlog/order` |

## 10. Jira

| Method | Path | Notes |
|---|---|---|
| GET | `/jira/connect-url` | Admin; returns the 3LO authorization URL + `state` |
| GET | `/jira/oauth/callback` | Atlassian redirect target; exchanges the code, stores encrypted tokens, redirects to the UI |
| GET | `/jira/connection` | status, site, scopes, last sync — **never tokens** |
| DELETE | `/jira/connection` | disconnect and purge tokens |
| GET | `/jira/projects` · `/jira/boards` · `/jira/sprints` | discovery for mapping |
| GET/PUT | `/jira/mappings/projects/{projectId}` | issue type, status, priority, field maps |
| POST | `/jira/sync` | `{ "mode": "FULL" \| "INCREMENTAL" \| "RECONCILE", "projectId": "..." }` → 202 |
| GET | `/jira/sync-status` | queue depth, dead letters, last sync, conflicts |
| POST | `/webhooks/jira` | inbound; signature-verified, unauthenticated by JWT |

```json
// GET /jira/sync-status
{
  "connection": { "status": "ACTIVE", "siteUrl": "https://acme.atlassian.net",
                  "cloudId": "a1b2...", "tokenExpiresAt": "2026-08-01T11:40:00Z" },
  "lastFullSyncAt": "2026-07-30T22:00:00Z",
  "lastReconcileAt": "2026-08-01T06:00:00Z",
  "outbox": { "pending": 4, "inProgress": 1, "failed": 0, "deadLetter": 2 },
  "webhooks": { "receivedLast24h": 312, "processed": 309, "ignoredDuplicates": 3, "failed": 0 },
  "conflicts": [ { "workItemId": "018f...", "referenceKey": "DEV-231",
                   "field": "status", "localValue": "TESTING", "jiraValue": "In Progress",
                   "resolution": "JIRA_WINS", "detectedAt": "2026-08-01T07:12:00Z" } ]
}
```

## 11. Admin

| Method | Path group |
|---|---|
| CRUD | `/admin/users`, `/admin/users/{id}/roles`, `/admin/users/{id}/skills` |
| CRUD | `/admin/departments`, `/admin/teams`, `/admin/teams/{id}/members` |
| CRUD | `/admin/skills`, `/admin/skills/aliases` |
| CRUD | `/admin/projects`, `/admin/projects/{id}/components` |
| CRUD | `/admin/process-templates`, `/admin/process-templates/{id}/tasks` |
| CRUD | `/admin/workflow-definitions`, `.../states`, `.../transitions` |
| CRUD | `/admin/assignment-policies`, `/admin/employee-level-policies` |
| GET | `/admin/audit-logs` (filter by actor, entity, action, correlationId, date) |
| GET | `/admin/outbox/dead-letter` · `POST /admin/outbox/{id}/retry` |
| GET | `/admin/unresolved-skills` · `POST /admin/unresolved-skills/{code}/map` |

## 12. Reporting

| Method | Path |
|---|---|
| GET | `/reports/tickets?from=&to=&departmentId=` |
| GET | `/reports/development?sprintId=\|projectId=` |
| GET | `/reports/capacity?departmentId=\|teamId=` |
| GET | `/reports/ai?from=&to=` |
| GET | `/reports/planned-vs-unplanned?sprintId=` |

```json
// GET /reports/ai
{
  "period": { "from": "2026-07-01", "to": "2026-08-01" },
  "recommendationsGenerated": 412,
  "autoAssignmentRate": 0.61,
  "teamLeadEditRate": 0.18,
  "processOwnerDecompositionEditRate": 0.24,
  "managerEscalationRate": 0.04,
  "acceptance": { "department": 0.94, "team": 0.88, "assignee": 0.79, "priority": 0.83 },
  "priorityChangeRate": 0.17,
  "lowConfidenceRate": 0.11,
  "byRoute": { "AUTO_ASSIGN": 251, "TEAM_LEAD_REVIEW": 118,
               "PROCESS_OWNER_REVIEW": 26, "MANAGER_APPROVAL": 12,
               "REQUEST_MORE_INFORMATION": 4, "MANUAL_EXCEPTION_REVIEW": 1 }
}
```

No endpoint returns a per-employee score, and `/reports/*` responses are aggregated by team, department or role only.

## 13. Notifications

| Method | Path |
|---|---|
| GET | `/notifications?unreadOnly=true` |
| POST | `/notifications/{id}/read` · `/notifications/read-all` |
| GET | `/notifications/unread-count` |

## 14. Internal AI contract (Spring Boot → FastAPI)

Not exposed publicly. Auth via `X-Service-Token`; the AI service rejects anything else. Full schemas in [07-ai-service-contract.md](07-ai-service-contract.md).

| Method | Path |
|---|---|
| POST | `/internal/v1/analyze/ticket` |
| POST | `/internal/v1/analyze/development` |
| POST | `/internal/v1/decompose` |
| POST | `/internal/v1/extract-skills` |
| GET | `/health` · `/ready` |

## 15. OpenAPI

`springdoc-openapi` generates the spec from controllers, DTO records and Bean Validation annotations.

- Interactive docs: `/swagger-ui.html`
- Spec: `/v3/api-docs`
- Grouped by tag: `auth`, `work-items`, `ai`, `recommendations`, `team-lead`, `process-owner`, `manager`, `sprints`, `jira`, `admin`, `reports`, `notifications`
- CI fails if the committed `openapi.json` differs from the generated one, so the contract cannot drift silently
- The frontend's `src/api/generated/` types are produced from the spec by `openapi-typescript`
