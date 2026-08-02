# 04 — Workflows

## 1. Configurable workflow profiles

There is no single global status enum. Each work item is bound at creation to a `workflow_definition`, and its legal states and transitions come from `workflow_states` and `workflow_transitions`.

```mermaid
erDiagram
    WORKFLOW_DEFINITIONS ||--o{ WORKFLOW_STATES : has
    WORKFLOW_DEFINITIONS ||--o{ WORKFLOW_TRANSITIONS : has
    WORKFLOW_STATES ||--o{ WORKFLOW_TRANSITIONS : "from / to"
    WORK_ITEMS }o--|| WORKFLOW_DEFINITIONS : "bound at creation"
```

### Profile selection

| Work item type / family | Profile code |
|---|---|
| `REQUEST`, `GENERAL_OPERATIONAL_WORK`, tasks under a request | `SERVICE` |
| process root + tasks in `PROCESS_WORK` | `PROCESS` |
| `EPIC`, `FEATURE`, `STORY`, `TASK`, `TECHNICAL_DEBT`, `TEST`, `DOCUMENTATION` | `DEVELOPMENT` |
| `BUG` | `BUG` |
| `RESEARCH` | `RESEARCH` |
| `INCIDENT` | `INCIDENT` |
| `SUBTASK` | inherits its parent's profile |

The binding is stored as `work_items.workflow_definition_id` and does not change if the admin later edits the profile — existing items keep their definition version. Admins can create additional profiles (e.g. `DEVELOPMENT_NO_QA` for a team without a QA stage) and set them as the default for a team or project.

### State metadata

Every row in `workflow_states` carries flags used across the platform:

| Flag | Used by |
|---|---|
| `is_initial` | creation |
| `is_terminal` | progress, reporting, sprint carry-over |
| `is_terminal_success` | progress numerator, "done" definition |
| `is_active_work` | workload calculation (hours count against capacity) |
| `is_blocked` | blocked queues, blocked-duration metric |
| `is_waiting_external` | SLA clock pause |
| `category` | board column mapping: `TODO / IN_PROGRESS / BLOCKED / REVIEW / TESTING / DONE` |

`category` is what lets one board render items from six different profiles side by side on My Work.

## 2. The six profiles

### 2.1 SERVICE

```mermaid
stateDiagram-v2
    [*] --> NEW
    NEW --> AI_ANALYSIS : ANALYZE (auto)
    AI_ANALYSIS --> WAITING_INFORMATION : NEEDS_INFO (auto)
    AI_ANALYSIS --> ASSIGNED : AUTO_ASSIGN (policy)
    AI_ANALYSIS --> NEW : ANALYSIS_FAILED
    WAITING_INFORMATION --> AI_ANALYSIS : INFO_PROVIDED
    NEW --> ASSIGNED : ASSIGN (Team Lead)
    ASSIGNED --> IN_PROGRESS : START_WORK
    IN_PROGRESS --> BLOCKED : BLOCK (reason required)
    BLOCKED --> IN_PROGRESS : UNBLOCK
    IN_PROGRESS --> WAITING_REQUESTER : ASK_REQUESTER
    WAITING_REQUESTER --> IN_PROGRESS : REQUESTER_REPLIED
    IN_PROGRESS --> WAITING_DEVELOPMENT : CONVERT_TO_DEV
    WAITING_DEVELOPMENT --> RESOLVED : DEV_COMPLETED (auto)
    IN_PROGRESS --> RESOLVED : RESOLVE
    RESOLVED --> CLOSED : VERIFY
    RESOLVED --> IN_PROGRESS : REOPEN
    ASSIGNED --> CANCELLED : CANCEL
    IN_PROGRESS --> CANCELLED : CANCEL
    CLOSED --> [*]
    CANCELLED --> [*]
```

`WAITING_DEVELOPMENT` and `BLOCKED` are additions to the brief's minimal list. `WAITING_DEVELOPMENT` is required by Demo 3; `BLOCKED` is required because Employees must be able to block a task with a reason and Team Leads need a blocked queue.

State categories: `NEW/AI_ANALYSIS/WAITING_INFORMATION/ASSIGNED → TODO`, `IN_PROGRESS/WAITING_REQUESTER/WAITING_DEVELOPMENT → IN_PROGRESS`, `BLOCKED → BLOCKED`, `RESOLVED/CLOSED → DONE`.

SLA clock pauses in `WAITING_INFORMATION`, `WAITING_REQUESTER` and `BLOCKED`.

### 2.2 PROCESS

```mermaid
stateDiagram-v2
    [*] --> NEW
    NEW --> AI_ANALYSIS : ANALYZE (auto)
    AI_ANALYSIS --> WAITING_INFORMATION : NEEDS_INFO
    WAITING_INFORMATION --> AI_ANALYSIS : INFO_PROVIDED
    AI_ANALYSIS --> PLANNING : PLAN_GENERATED
    PLANNING --> PLANNING : PLAN_EDITED (Process Owner)
    PLANNING --> IN_PROGRESS : PLAN_APPROVED
    IN_PROGRESS --> BLOCKED : BLOCK
    BLOCKED --> IN_PROGRESS : UNBLOCK
    IN_PROGRESS --> WAITING_PROCESS_VERIFICATION : ALL_CHILDREN_DONE (auto)
    WAITING_PROCESS_VERIFICATION --> IN_PROGRESS : REJECT_VERIFICATION
    WAITING_PROCESS_VERIFICATION --> CLOSED : CLOSE_PROCESS
    IN_PROGRESS --> ESCALATED : ESCALATE
    ESCALATED --> IN_PROGRESS : ESCALATION_RESOLVED
    CLOSED --> [*]
```

`ALL_CHILDREN_DONE` is an automatic transition fired by the progress calculator when estimate-weighted progress reaches 100% and no child is blocked. `CLOSE_PROCESS` is restricted to `PROCESS_OWNER` and `MANAGER`.

### 2.3 DEVELOPMENT

```mermaid
stateDiagram-v2
    [*] --> BACKLOG
    BACKLOG --> AI_ANALYSIS : ANALYZE (auto)
    AI_ANALYSIS --> WAITING_INFORMATION : NEEDS_INFO
    WAITING_INFORMATION --> AI_ANALYSIS : INFO_PROVIDED
    AI_ANALYSIS --> BACKLOG : ANALYSIS_COMPLETE
    BACKLOG --> READY : MARK_READY
    READY --> TODO : ADD_TO_SPRINT
    TODO --> IN_PROGRESS : START_WORK
    IN_PROGRESS --> BLOCKED : BLOCK
    BLOCKED --> IN_PROGRESS : UNBLOCK
    IN_PROGRESS --> CODE_REVIEW : SUBMIT_FOR_REVIEW
    CODE_REVIEW --> IN_PROGRESS : REJECT_REVIEW
    CODE_REVIEW --> TESTING : APPROVE_REVIEW
    TESTING --> IN_PROGRESS : TEST_FAILED
    TESTING --> DONE : PASS_QA
    TODO --> READY : REMOVE_FROM_SPRINT
    READY --> BACKLOG : RETURN_TO_BACKLOG
    DONE --> [*]
```

`MARK_READY` has a guard: acceptance criteria must be present for `STORY` and `FEATURE`, and there must be no unresolved `DEPENDS_ON` link to an unfinished item. **There is no `IN_PROGRESS → DONE` edge.** Review and testing are structurally unskippable.

### 2.4 BUG

```mermaid
stateDiagram-v2
    [*] --> OPEN
    OPEN --> ANALYSIS : START_ANALYSIS
    ANALYSIS --> READY : ROOT_CAUSE_FOUND
    ANALYSIS --> OPEN : CANNOT_REPRODUCE
    READY --> IN_PROGRESS : START_WORK
    IN_PROGRESS --> BLOCKED : BLOCK
    BLOCKED --> IN_PROGRESS : UNBLOCK
    IN_PROGRESS --> CODE_REVIEW : SUBMIT_FOR_REVIEW
    CODE_REVIEW --> IN_PROGRESS : REJECT_REVIEW
    CODE_REVIEW --> QA : APPROVE_REVIEW
    QA --> IN_PROGRESS : QA_FAILED
    QA --> VERIFIED : QA_PASSED
    VERIFIED --> CLOSED : CLOSE
    VERIFIED --> OPEN : REOPEN
    CLOSED --> [*]
```

Reaching `VERIFIED` or `CLOSED` fires `BugResolved`, which is what moves a linked ticket out of `WAITING_DEVELOPMENT` (§5).

### 2.5 RESEARCH

```mermaid
stateDiagram-v2
    [*] --> BACKLOG
    BACKLOG --> READY : MARK_READY
    READY --> IN_PROGRESS : START_WORK
    IN_PROGRESS --> BLOCKED : BLOCK
    BLOCKED --> IN_PROGRESS : UNBLOCK
    IN_PROGRESS --> FINDINGS_REVIEW : SUBMIT_FINDINGS
    FINDINGS_REVIEW --> IN_PROGRESS : MORE_RESEARCH_NEEDED
    FINDINGS_REVIEW --> COMPLETED : ACCEPT_FINDINGS
    COMPLETED --> [*]
```

Research items are time-boxed: `estimated_hours` is mandatory, and exceeding it by 50% raises a notification to the Team Lead rather than silently continuing.

### 2.6 INCIDENT

```mermaid
stateDiagram-v2
    [*] --> OPEN
    OPEN --> TRIAGED : TRIAGE
    TRIAGED --> INVESTIGATING : START_INVESTIGATION
    INVESTIGATING --> MITIGATED : APPLY_MITIGATION
    MITIGATED --> RESOLVED : CONFIRM_RESOLUTION
    MITIGATED --> INVESTIGATING : MITIGATION_FAILED
    RESOLVED --> POSTMORTEM : START_POSTMORTEM
    POSTMORTEM --> CLOSED : COMPLETE_POSTMORTEM
    RESOLVED --> CLOSED : CLOSE_NO_POSTMORTEM
    INVESTIGATING --> ESCALATED : ESCALATE
    ESCALATED --> INVESTIGATING : ESCALATION_RESOLVED
    CLOSED --> [*]
```

Guard: `CLOSE_NO_POSTMORTEM` is unavailable when `priority = CRITICAL` or `production_impacting = true` — those incidents must go through `POSTMORTEM`.

## 3. Ticket-originated end-to-end flow

The 25 steps from the brief, as an executable sequence.

```mermaid
sequenceDiagram
    autonumber
    actor EMP as Employee (Requester)
    participant BE as Spring Boot
    participant AI as FastAPI
    participant POL as Policy Engine
    actor TL as Team Lead
    actor PO as Process Owner
    participant OB as Outbox → Jira

    EMP->>BE: POST /work-items (REQUEST)
    Note over BE: reporter_id = employee ⇒ REQUESTER relationship<br/>status NEW → WAITING_AI_ANALYSIS
    BE->>AI: analyze(sanitized context)
    AI-->>BE: classification + missing info + risk + template match

    alt information missing
        BE->>BE: status → WAITING_FOR_INFORMATION
        BE->>EMP: clarification questions (notification)
        EMP->>BE: POST /work-items/{id}/additional-information
        BE->>BE: status → WAITING_AI_REANALYSIS
        BE->>AI: reanalyze(original + answers)
        AI-->>BE: updated analysis
    end

    BE->>AI: generate plan (template-customizing or from scratch)
    AI-->>BE: suggested child work items
    BE->>BE: resolve Process Owner<br/>(template default, or dept lead of the dominant department)
    BE->>BE: persist children (origin TICKET_GENERATED / PROCESS_TEMPLATE)

    loop each generated task
        BE->>AI: per-task skill + effort extraction
        BE->>BE: rank candidates (deterministic scoring)
        BE->>POL: evaluate(task, analysis, candidates, policies)
        alt AUTO_ASSIGN
            POL-->>BE: AUTO_ASSIGN
            BE->>BE: assign, status → ASSIGNED
            BE->>OB: AssignmentAutoApproved + JiraSyncRequested
        else TEAM_LEAD_REVIEW
            POL-->>BE: TEAM_LEAD_REVIEW
            BE->>TL: approval request in review queue
            TL->>BE: approve / edit / reassign
            BE->>OB: AssignmentApproved + JiraSyncRequested
        else PROCESS_OWNER_REVIEW
            POL-->>BE: PROCESS_OWNER_REVIEW
            BE->>PO: plan approval request
            PO->>BE: approve plan (possibly edited)
        end
    end

    OB->>OB: create Jira issues, set assignees, apply transitions
    EMP->>BE: (assignees) start, progress, complete tasks
    BE->>BE: recalculate parent progress on each child change
    BE->>BE: all children done ⇒ closure workflow by closure_type
    PO->>BE: verify + close (or requester verifies)
    BE->>OB: JiraSyncRequested (close parent issue)
```

### Closure types

`closure_type` comes from the process template, or defaults by family.

| Closure type | Who closes | Default for |
|---|---|---|
| `AUTO_CLOSE` | system, when all children are done | `GENERAL_OPERATIONAL_WORK` |
| `REQUESTER_VERIFICATION` | the REQUESTER | `SERVICE_WORK` |
| `PROCESS_OWNER_VERIFICATION` | the PROCESS_OWNER | `PROCESS_WORK` |
| `TEAM_LEAD_VERIFICATION` | the owning Team Lead | `INCIDENT_WORK` |

If the verifier does not act within the configured window, a reminder is raised; the item is never auto-closed against a non-`AUTO_CLOSE` policy.

## 4. Development end-to-end flow

```mermaid
sequenceDiagram
    autonumber
    actor CRE as Creator (Employee / Team Lead / Jira import)
    participant BE as Spring Boot
    participant AI as FastAPI
    participant POL as Policy Engine
    actor TL as Team Lead
    actor DEV as Assignee
    participant OB as Outbox → Jira

    CRE->>BE: create STORY (or import from Jira)
    BE->>BE: normalize, bind DEVELOPMENT profile, status BACKLOG → AI_ANALYSIS
    BE->>AI: analyze(dev context: project, components, criteria)
    AI-->>BE: dev type, objective, criteria completeness, components,<br/>skills, complexity, points, dependencies, risk flags, decomposition need

    alt acceptance criteria incomplete
        BE->>CRE: clarification questions
        CRE->>BE: answers
        BE->>AI: reanalyze
    end

    alt too large
        BE->>AI: decompose into stories / tasks / subtasks
        AI-->>BE: child items with skills, components, estimates, dependencies, tests
    end

    loop each child
        BE->>BE: extract skills, resolve project/component experience
        BE->>BE: rank candidates
        BE->>POL: evaluate
        POL-->>BE: route
    end

    TL->>BE: review decomposition, edit, approve
    BE->>BE: items → READY, added to backlog
    TL->>BE: POST /team-lead/work-items/{id}/add-to-sprint
    BE->>BE: capacity check, status → TODO
    BE->>OB: JiraSyncRequested (create issues, set sprint, assign)

    DEV->>BE: START_WORK → IN_PROGRESS
    DEV->>BE: SUBMIT_FOR_REVIEW → CODE_REVIEW
    TL->>BE: APPROVE_REVIEW → TESTING
    TL->>BE: PASS_QA → DONE
    BE->>BE: recalculate STORY / FEATURE / EPIC progress (points-weighted)
    BE->>OB: JiraSyncRequested (transition Jira issue)
    BE->>BE: include in sprint + project analytics
```

## 5. Ticket → development conversion

The mechanism behind Demo 3.

```mermaid
sequenceDiagram
    autonumber
    actor EMP as Requester
    participant BE as Spring Boot
    participant AI as FastAPI
    actor TL as Payment Team Lead
    actor DEV as Developer

    EMP->>BE: REQUEST "Payments fail intermittently at checkout"
    BE->>AI: analyze
    AI-->>BE: workFamily DEVELOPMENT_WORK, likelyDefect true,<br/>suggestedConversion BUG, productionImpacting true
    BE->>BE: create BUG (origin TICKET_GENERATED)
    BE->>BE: link  TCK-102 --RESOLVED_BY--> DEV-456
    BE->>BE: TCK-102 → WAITING_DEVELOPMENT (SLA clock pauses)
    BE->>BE: DEV-456 → OPEN, team = Payments (from AI + policy)
    Note over BE: productionImpacting ⇒ MANAGER_APPROVAL on the fix task
    BE->>AI: decompose bug
    AI-->>BE: root cause analysis, code fix, unit test,<br/>integration test, regression test
    DEV->>BE: work the bug through ANALYSIS → ... → QA → VERIFIED
    BE->>BE: BugResolved event
    BE->>BE: TCK-102 WAITING_DEVELOPMENT → RESOLVED (auto)
    BE->>EMP: "Your issue is fixed — please verify"
    EMP->>BE: VERIFY
    BE->>BE: TCK-102 → CLOSED
```

Rules:

1. Conversion **never deletes or replaces** the ticket. The ticket remains the requester's window into progress.
2. The link is `RESOLVED_BY` in canonical direction (`Ticket RESOLVED_BY Bug`), rendered as `RESOLVES` on the bug.
3. `WAITING_DEVELOPMENT` pauses the ticket's SLA clock, and the ticket's progress mirrors the bug's progress.
4. The ticket returns to `RESOLVED` when **all** items linked `RESOLVED_BY` reach a terminal-success state. Multiple bugs may resolve one ticket.
5. The requester always verifies, regardless of who fixed it. `closure_type` on a converted ticket is forced to `REQUESTER_VERIFICATION`.
6. If the requester rejects, the ticket reopens and a new `BUG` may be linked; the old link is retained for history.

## 6. Transition validation

```mermaid
flowchart TB
    A["POST /work-items/{id}/transition<br/>{ transition: 'APPROVE_REVIEW' }"] --> B{Transition exists for<br/>this definition + current state?}
    B -->|no| E1["422 INVALID_TRANSITION<br/>+ list of legal transitions"]
    B -->|yes| C{Actor holds an allowed<br/>role or relationship?}
    C -->|no| E2[403 TRANSITION_NOT_PERMITTED]
    C -->|yes| D{Guards pass?}
    D -->|no| E3["422 TRANSITION_GUARD_FAILED<br/>+ failed guard codes"]
    D -->|yes| F{Optimistic lock<br/>version matches?}
    F -->|no| E4[409 CONCURRENT_MODIFICATION]
    F -->|yes| G[Apply: status, audit, domain event,<br/>outbox JiraSyncRequested, progress recalc]
```

### Guard catalogue

| Guard | Fails when |
|---|---|
| `REQUIRE_REASON` | `BLOCK`, `REJECT_REVIEW`, `CANCEL`, `ESCALATE` submitted without a reason ≥ 10 chars |
| `REQUIRE_ASSIGNEE` | moving into an `is_active_work` state with no assignee |
| `NOT_SELF_REVIEW` | actor is the assignee on `APPROVE_REVIEW` / `PASS_QA` / `QA_PASSED` |
| `NO_OPEN_DEPENDENCIES` | `START_WORK` while a `DEPENDS_ON` target is not terminal-success |
| `REQUIRE_ACCEPTANCE_CRITERIA` | `MARK_READY` on a `STORY`/`FEATURE` with empty criteria |
| `ALL_CHILDREN_TERMINAL` | closing a parent with unfinished children |
| `REQUIRE_SPRINT` | `ADD_TO_SPRINT` with no active sprint, or sprint capacity exceeded without an override |
| `REQUIRE_POSTMORTEM` | `CLOSE_NO_POSTMORTEM` on a critical / production-impacting incident |
| `LEVEL_RESTRICTION` | intern taking a restricted task (see [08](08-policy-and-assignment.md)) |
| `REQUIRE_VERIFICATION_ACTOR` | non-requester attempting `VERIFY` on a `REQUESTER_VERIFICATION` item |

### Automatic transitions

Fired by the system, not by a user, and audited with `actor = SYSTEM`:

| Automatic transition | Trigger |
|---|---|
| `ANALYZE` | work item created and origin is not `RECURRING` |
| `NEEDS_INFO` | analysis returns `requiresMoreInformation = true` |
| `AUTO_ASSIGN` | policy engine returns `AUTO_ASSIGN` |
| `ALL_CHILDREN_DONE` | progress reaches 100% with no blocked child |
| `DEV_COMPLETED` | every `RESOLVED_BY` target reaches terminal success |
| `ANALYSIS_FAILED` | AI validation fails after the repair retry |

## 7. Board column mapping

My Work and the sprint board render items from all six profiles. The mapping is `workflow_states.category`:

| Column | States that map to it |
|---|---|
| **Todo** | `NEW`, `BACKLOG`, `READY`, `TODO`, `ASSIGNED`, `OPEN`, `TRIAGED`, `AI_ANALYSIS`, `WAITING_INFORMATION` |
| **In Progress** | `IN_PROGRESS`, `ANALYSIS`, `INVESTIGATING`, `PLANNING`, `WAITING_REQUESTER`, `WAITING_DEVELOPMENT`, `MITIGATED` |
| **Blocked** | `BLOCKED`, `ESCALATED` |
| **Review** | `CODE_REVIEW`, `FINDINGS_REVIEW`, `WAITING_PROCESS_VERIFICATION` |
| **Testing** | `TESTING`, `QA` |
| **Done** | `DONE`, `RESOLVED`, `VERIFIED`, `COMPLETED`, `CLOSED`, `POSTMORTEM`, `CANCELLED` |

Dragging a card issues the transition whose target state has the destination category. If more than one is legal, the UI asks which; if none is legal, the drop is rejected with the reason. The frontend never sends a status string.

## 8. Seeded workflow definitions

Flyway seeds six definitions with their states and transitions (`V6__workflow_definitions.sql`):

| Code | Family | States | Transitions |
|---|---|---|---|
| `SERVICE` | `SERVICE_WORK` | 11 | 18 |
| `PROCESS` | `PROCESS_WORK` | 9 | 13 |
| `DEVELOPMENT` | `DEVELOPMENT_WORK` | 10 | 16 |
| `BUG` | `DEVELOPMENT_WORK` | 9 | 13 |
| `RESEARCH` | `DEVELOPMENT_WORK` | 6 | 7 |
| `INCIDENT` | `INCIDENT_WORK` | 8 | 10 |

Definitions are data, not code. Adding a team-specific profile is an admin action, not a deployment.
