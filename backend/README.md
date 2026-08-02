# SmartWork AI — Backend (Phase 6)

Spring Boot modular monolith. Thirteen modules, boundaries verified at build time.

**Phase 2 — foundation.** Flyway V1–V6 · Argon2id + JWT with rotating refresh tokens ·
users, roles, skills · departments, teams, memberships · projects and components ·
the six workflow profiles seeded as data.

**Phase 3 — work items and the workflow engine.** The `work_items` table (V7–V8) ·
12 types, 5 families, 8 origins · typed links with cycle detection · the workflow engine
with 10 guards · three progress strategies · resource-scoped authorization with
visibility pushed into SQL · the full work item REST API.

**Phase 5 — plan and rank.** Three new modules and migrations V9–V10.

- `process` — 8 seeded process templates, deterministic keyword matching, and
  instantiation into a real work item tree with dependency links resolved from template
  sequence numbers.
- `analysis` — the HTTP client to the FastAPI service, the org-context vocabulary builder,
  stored recommendations, and template-first decomposition.
- `assignment` — hours-based workload, the seven-component score, eligibility filters with
  reason codes, employee level policies, and tiered candidate discovery.

**Phase 6 — the policy engine.** One new module and migration V11.

- `policy` — six routes evaluated strictest-first, the fourteen auto-assign conditions,
  approval requests addressed to a *scope* rather than a person, review queues, and
  escalation as a linked successor request.

A low-risk, single-team, confidently-classified task with an unambiguous top candidate now
assigns itself with no human involved. Everything else stops somewhere specific, and says
why in a closed set of reason codes.

**Not yet:** Jira (Phase 8), the frontend (Phase 7), reporting and MCP (Phase 9).

## What is deliberately deterministic

No LLM is in the assignment loop. The AI proposes *what* work is needed and which skills
it requires; *who* performs it is decided in Java, on stored data, with a formula that can
be replayed and explained months later. Two consequences worth knowing about:

- **Level restrictions are enforced twice** — in candidate filtering, so a restricted
  person never appears as a recommendation, and again in the `LEVEL_RESTRICTION` transition
  guard, so a Team Lead assigning manually is stopped too. An LLM told "do not assign
  interns to production work" complies most of the time, and most of the time is not an
  authorization model.
- **Protected characteristics are absent from the schema**, not filtered out of the
  formula. There is no column to accidentally join. `ProtectedCharacteristicsSchemaIT`
  scans `information_schema` and fails the build if one is ever added.
- **Routes are evaluated strictest-first, first match wins.** No combination of conditions
  can produce a laxer outcome than the strictest applicable trigger — a CRITICAL-risk item
  cannot be talked down to a Team Lead by also being low-confidence. Scoring the triggers
  and picking a winner has the opposite failure mode, and it fails silently.
- **Deciding *what scrutiny* is needed is separate from deciding *whose queue*.** The
  engine never sees a person; `ApproverResolver` does that afterwards. Reorganising a
  department therefore changes who reviews something without changing whether it needed
  review, which is the only way a decision stored a year ago still means anything.

---

## Run it

```bash
cp .env.example .env          # from the repository root
docker compose up --build
```

| | |
|---|---|
| API | http://localhost:8080 |
| OpenAPI UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |

The default profile is `dev,demo`, which seeds the Acme organization: 4 departments,
6 teams, 31 skills, 3 projects, one active sprint and 20 users. Every demo account uses
the password in `DEMO_PASSWORD` (default `SmartWork!Demo2026`).

| Account | Roles | Why it exists |
|---|---|---|
| `admin@acme.io` | ADMIN | configuration only — cannot assign or approve work |
| `elif.yilmaz@acme.io` | EMPLOYEE, MANAGER | organization-wide manager |
| `ayse.demir@acme.io` | EMPLOYEE, TEAM_LEAD | leads Backend |
| `zeynep.koc@acme.io` | EMPLOYEE | the HR Operations lead — an **EMPLOYEE**, deliberately, because Process Owner is a per-resource relationship, not a role |
| `can.ozturk@acme.io` | EMPLOYEE (INTERN) | subject to level restrictions in Phase 6 |

Without Docker:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev,demo
```

## Tests

```bash
make unit    # fast, no Docker
make it      # Testcontainers: starts a real PostgreSQL 16
make test    # both
```

Real Postgres rather than H2 — the schema relies on partial unique indexes, CHECK
constraints, `pg_trgm` and trigger functions, none of which H2 reproduces.

| Test | What it guarantees |
|---|---|
| `ModularityTest` | module boundaries hold; `api` cannot reach another module's `domain` |
| `ArchitectureTest` | no JPA entity is reachable from a controller; password hashes stay inside auth and identity |
| `AuthenticationIT` | login, rotation, **refresh-token reuse revokes the whole family** |
| `CrossOrganizationIsolationIT` | no endpoint or service leaks across tenants; pagination totals do not leak existence |
| `ProtectedCharacteristicsSchemaIT` | **no protected-characteristic column exists anywhere in the schema** (ADR-013) |
| `AdminAuthorizationIT` | an EMPLOYEE cannot reach admin, manager or team-lead surfaces |
| `WorkflowSeedIT` | the six profiles are seeded correctly and **no `IN_PROGRESS → DONE` edge exists** |
| `JwtServiceTest` | claims round-trip; expiry is distinguishable from invalidity; no PII in the token |
| `WorkItemValidatorTest` | every type rule; a task needs no ticket, project or parent; all errors reported at once |
| `ProgressCalculatorTest` | estimate-, point- and count-weighting; fallback when estimates are missing |
| `WorkFamilyResolverTest` | the same TASK lands on four different boards depending on origin |
| `StandardGuardsTest` | all 10 guards as pure functions — **self-review, intern restrictions, dependency blocking** |
| `WorkItemLifecycleIT` | ticket and story lifecycles; **an employee cannot assign work to a colleague** |
| `WorkItemLinkIT` | direct and indirect **cycle rejection**; inverse links collapse onto one row |
| `TicketToBugIT` | **Demo scenario 3 end to end** — ticket → bug → fix → requester verifies |
| `WorkItemAuthorizationIT` | 404 not 403 for invisible items; only a manager clears a sensitivity flag |
| `ScoreFunctionsTest` | all seven components at their exact boundaries — recency bonus at 90/365 days, the capacity curve at 0.50/0.85/1.00, **the busiest equally-skilled person ranks below the quieter one** |
| `WorkloadCalculatorTest` | **hours, never task count** — 5×1h ≠ 5×40h; fallbacks by type; story points convert; **long-blocked items count at 30%**; epics contribute 0 so children are not double-counted |
| `EligibilityFilterTest` | every exclusion reason; **interns barred from production, financial, security, critical and oversized work**; a HIGH ceiling still permits HIGH |
| `NoProtectedCharacteristicsTest` | the feature vector is exactly the seven permitted components; weights that do not sum to 1.0 are rejected on save |
| `ScoringEngineTest` | ranking is **deterministic under reordering** (replayability); weights actually change the outcome |
| `TemplateMatcherTest` | onboarding and hardware requests match; **an unrelated request matches nothing rather than the least-bad option** |
| `ProcessInstantiationIT` | **an onboarding ticket produces a cross-department plan** — 3 departments, 4 dependency links, forward references resolved, SUBTREE process owner |
| `AutoAssignConditionsTest` | **the fourteen conditions, one per test**, each flipping a single field from an auto-assignable baseline; boundaries are inclusive at 0.85 confidence, gap 5, score 60 |
| `PolicyOrderingTest` | **strictest-first, first match wins** — critical+low-confidence reaches a manager, a suspected injection outranks everything, the enum order encodes severity |
| `RoutingIsReplayableTest` | identical inputs reproduce the identical decision; the decision **pins its policy row, version and engine version**; the explanation is derived from the codes so they cannot disagree |
| `ApprovalRequestTest` | **a request is decided once** — a second decision is a 409, not an overwrite; escalation requires a reason; a malformed scope is refused rather than creating an unreachable request |
| `ActorScopesTest` | **seeing a request is not the same as answering it** — a team member sees the queue, only the lead decides; department managers have no authority over other departments |

## Driving the full pipeline end to end

Six steps, six calls. `POST /work-items/{id}/route` is the one that runs Phase 5 and
Phase 6 together — discovery, eligibility, scoring, policy, and then either the assignment
or the approval request.

```bash
BASE=http://localhost:8080/api/v1
TOKEN=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ayse.demir@acme.io","password":"SmartWork!Demo2026"}' | jq -r .accessToken)
AUTH="Authorization: Bearer $TOKEN"

# 1. Create a work item
ID=$(curl -s -X POST $BASE/work-items -H "$AUTH" -H 'Content-Type: application/json' -d '{
  "type":"TASK","originType":"USER_CREATED",
  "summary":"Add rate limiting to the public search endpoint",
  "description":"Cap anonymous callers at 60 requests per minute.",
  "priority":"MEDIUM","estimatedHours":4
}' | jq -r .id)

# 2. Analyze — required for step 4; routing refuses without it
curl -s -X POST $BASE/work-items/$ID/analyze -H "$AUTH" | jq '.status, .confidence'

# 3 + 4 + 5. Rank candidates, apply the policy, assign or open an approval request
curl -s -X POST $BASE/work-items/$ID/route -H "$AUTH" | jq '{
  route:        .decision.route,
  why:          [.decision.reasons[].code],
  autoAssigned: .assignedWithoutHuman,
  assignee:     .assigneeName,
  poolTier:     .candidatePoolTier,
  top3:         [.candidates[:3][] | {name:.fullName, total:.totalScore, scores}],
  excluded:     [.excludedCandidates[] | {reason, meaning}]
}'

# 6. Confirm it landed in the right queue
curl -s $BASE/approvals/pending -H "$AUTH" | jq '.content[] | {route, reasons:[.reasons[].code], dueAt}'
```

To see the other side of the branch, create an item with a sensitivity flag set — a
budget-impacting task routes to `MANAGER_APPROVAL` instead of assigning itself, which is
Demo 1 step 7. Then decide it:

```bash
curl -s -X POST $BASE/approvals/$REQUEST_ID/approve -H "$AUTH" \
  -H 'Content-Type: application/json' -d '{"assigneeIdOverride":"'$USER_ID'","note":"Approved."}'

curl -s -X POST $BASE/approvals/$REQUEST_ID/escalate -H "$AUTH" \
  -H 'Content-Type: application/json' -d '{"reason":"Vendor licence is above my authority."}'
```

The route response deliberately returns the whole picture — every candidate with all seven
score components, and everyone who was excluded with the reason. A response that said only
`TEAM_LEAD_REVIEW` would force three more calls to find out why, and explainability is the
product.

## Example requests

```bash
# Log in
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@acme.io","password":"SmartWork!Demo2026"}'
```

```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "Zk8sQ2p...",
  "expiresIn": 900,
  "tokenType": "Bearer",
  "user": {
    "email": "admin@acme.io",
    "roles": ["ADMIN"],
    "department": { "code": "IT", "name": "Information Technology" },
    "capabilities": ["work_item.create", "admin.users", "admin.jira", "..."]
  }
}
```

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@acme.io","password":"SmartWork!Demo2026"}' | jq -r .accessToken)

# Who am I, and what is my scope?
curl -s http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN"

# Directory
curl -s "http://localhost:8080/api/v1/admin/users?size=5" -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8080/api/v1/admin/departments  -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8080/api/v1/admin/teams        -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8080/api/v1/admin/projects     -H "Authorization: Bearer $TOKEN"

# Resolve raw AI-style skill strings onto the canonical vocabulary.
# "springboot" and "postgres" resolve via aliases; "Kubernetes" does not and is
# returned for an Admin to curate rather than being invented into the taxonomy.
curl -s -X POST http://localhost:8080/api/v1/admin/skills/resolve \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"codes":["springboot","postgres","REACT","Kubernetes"]}'
```

```json
{
  "resolved": [
    { "code": "SPRING_BOOT", "name": "Spring Boot", "category": "FRAMEWORK" },
    { "code": "POSTGRESQL",  "name": "PostgreSQL",  "category": "PLATFORM" },
    { "code": "REACT",       "name": "React",       "category": "FRAMEWORK" }
  ],
  "unresolved": ["Kubernetes"]
}
```

```bash
# Refresh rotation. Replaying a consumed token revokes the entire family.
REFRESH=... # from the login response
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH\"}"
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH\"}"
# → 401 { "code": "REFRESH_TOKEN_REUSED", ... }
```

Every error is `application/problem+json` with a stable `code` and a `correlationId`:

```json
{
  "type": "https://smartwork.ai/errors/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "correlationId": "018f2c...",
  "errors": [{ "field": "code", "code": "NOTBLANK", "message": "must not be blank" }]
}
```

## Schema

| Migration | Tables |
|---|---|
| `V1` | extensions (`pgcrypto`, `pg_trgm`), `updated_at` trigger, `organizations` |
| `V2` | `roles`, `users`, `user_roles`, `refresh_tokens` |
| `V3` | `departments`, `teams`, `team_members` |
| `V4` | `skills`, `skill_aliases`, `user_skills` |
| `V5` | `projects`, `project_components`, `sprints` |
| `V6` | `workflow_definitions`, `workflow_states`, `workflow_transitions` |
| `V6_1` | the six profiles: 53 states, 77 transitions |
| `V7` | `work_items`, `reference_key_counters` |
| `V8` | `work_item_links`, `_skills`, `_comments`, `_attachments`, `_watchers` |
| `R__seed_reference_data` | the four system roles |
| `db/demo/V900` | demo profile only — organization, departments, teams, skills, projects, sprint |

Demo data lives in a separate Flyway location activated only by the `demo` profile, so a
clean environment never inherits fake people.

`work_items` carries a composite foreign key `(workflow_definition_id, status)` into
`workflow_states`, so an invalid status is unrepresentable rather than merely rejected.
That is why the workflow seed is versioned (`V6_1`) and upserts rather than replaces —
see ADR-029.

## Phase 3 API

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ayse.demir@acme.io","password":"SmartWork!Demo2026"}' | jq -r .accessToken)
AUTH="Authorization: Bearer $TOKEN"

# A ticket. No project, no parent, no ceremony.
curl -s -X POST http://localhost:8080/api/v1/work-items -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d '{"workItemType":"REQUEST","summary":"Payments fail intermittently during checkout"}'
```

```json
{
  "referenceKey": "TCK-1",
  "workFamily": "SERVICE_WORK",
  "workflowCode": "SERVICE",
  "status": "NEW",
  "statusCategory": "TODO",
  "closureType": "REQUESTER_VERIFICATION",
  "relationships": ["REQUESTER"],
  "permissions": { "canEdit": true, "canAssign": false },
  "availableTransitions": [
    { "code": "ASSIGN", "label": "Assign", "toState": "ASSIGNED", "enabled": false,
      "blockedReasons": ["This work item must have an assignee first."] }
  ]
}
```

`availableTransitions` is computed for **this actor on this item**, with guards already
evaluated — so the UI renders exactly the buttons the backend will honour.

```bash
# A task, with no ticket and no project. This is the ADR-002 claim, exercised.
curl -s -X POST http://localhost:8080/api/v1/work-items -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d '{"workItemType":"TASK","summary":"Rotate the staging TLS certificate"}'
# → { "referenceKey": "WI-1", "workFamily": "GENERAL_OPERATIONAL_WORK", "projectId": null }

# Transitions take a NAME, never a status.
curl -s -X POST http://localhost:8080/api/v1/work-items/$ID/transition -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d '{"transition":"ASSIGN","assigneeId":"'$USER_ID'"}'

# Guard failures name what is wrong and what to do about it.
curl -s -X POST http://localhost:8080/api/v1/work-items/$ID/transition -H "$AUTH" \
  -H 'Content-Type: application/json' -d '{"transition":"BLOCK","reason":"wip"}'
```

```json
{
  "status": 422,
  "code": "TRANSITION_GUARD_FAILED",
  "detail": "A reason of at least 10 characters is required for this transition.",
  "errors": [{ "field": "transition", "code": "REQUIRE_REASON", "message": "..." }]
}
```

```bash
# Ticket to development conversion. The cross-family link is the whole point.
curl -s -X POST http://localhost:8080/api/v1/work-items/$TICKET/links -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d '{"linkType":"RESOLVED_BY","targetId":"'$BUG'"}'

# Stored once; rendered from both ends.
curl -s http://localhost:8080/api/v1/work-items/$BUG/links -H "$AUTH"
# → { "incoming": [ { "linkType": "RESOLVES", "referenceKey": "TCK-1" } ] }

# A cycle is refused before it can corrupt every later traversal.
curl -s -X POST http://localhost:8080/api/v1/work-items/$A/links -H "$AUTH" \
  -H 'Content-Type: application/json' -d '{"linkType":"DEPENDS_ON","targetId":"'$C'"}'
# → 400 { "code": "LINK_CYCLE_DETECTED" }

curl -s "http://localhost:8080/api/v1/work-items/my" -H "$AUTH"
curl -s "http://localhost:8080/api/v1/work-items?type=BUG&blocked=true&size=20" -H "$AUTH"
curl -s "http://localhost:8080/api/v1/work-items/$ID/children" -H "$AUTH"
```
