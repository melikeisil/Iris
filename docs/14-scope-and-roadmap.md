# 14 — Scope & Roadmap

## 1. MVP vs post-MVP

### In scope

| Area | MVP includes |
|---|---|
| **Work items** | All 12 types, 5 families, 8 origins, 12 link types, type-aware validation, three progress strategies |
| **Workflows** | Six configurable profiles as data, guard-validated transitions, automatic transitions, board category mapping |
| **AI analysis** | Ticket triage, development analysis, clarification loop, decomposition, skill extraction, duplicate hint, defect detection — real Anthropic provider + deterministic mock |
| **Assignment** | Deterministic 7-component scoring, top-3 candidates with stored components, exclusion reasons, hours-based workload |
| **Policy** | Six routes, all trigger sets, employee level policies, team/department/org policy resolution, replayable decisions |
| **Process templates** | Eight seeded templates, matching, customization, instantiation with dependencies |
| **Roles** | Four system roles × six resource relationships, full permission matrix, four-layer enforcement |
| **Frontend** | All five role areas, ~40 pages, My Work board, AI review queue, recommendation review with score breakdown, eight dashboards |
| **Jira** | OAuth 3LO, encrypted tokens, full/webhook/reconcile sync, mappings, ADF, outbox with retry and dead letter, staleness, mock client |
| **MCP** | 7 resources, 11 tools, 6 prompts, Streamable HTTP, privilege-free by construction |
| **Reporting** | Ticket, development, capacity and AI metrics; planned vs unplanned; burndown |
| **Security** | Argon2id, JWT + rotating refresh with reuse detection, tenancy isolation, encrypted Jira tokens, audit log, rate limits |
| **Infra** | Docker Compose, Flyway, GitHub Actions, seed data, three end-to-end scenarios |

### Out of scope (post-MVP)

| Area | Deferred | Why |
|---|---|---|
| Notifications | Email, Slack, Teams channels | in-app is enough to demonstrate the loop |
| Real-time | WebSocket push, presence, live board sync | polling is adequate at demo scale |
| Duplicate detection | Embeddings / pgvector | trigram covers the common case |
| Capacity | Calendar integration, holiday calendars, part-time schedules | configured hours are sufficient |
| SLA | Business-hours calendars, pause rules beyond blocked/waiting | target durations by priority are enough |
| Estimation | AI learning from historical actuals | needs history the MVP does not have |
| Jira | Multiple sites per org, Jira Service Management request types, custom field auto-discovery | one site, one mapping UI |
| Integrations | GitHub/GitLab, Confluence, PagerDuty | Jira is the required integration |
| Auth | MFA, SSO/SAML, SCIM provisioning | password auth is sufficient for a demo |
| Mobile | Native or mobile-optimized layout | desktop-first is specified |
| i18n | Multi-language UI | English only |
| Ops | Kubernetes, autoscaling, multi-region, blue/green | Compose is specified |
| Analytics | Custom report builder, scheduled exports | fixed dashboards |
| Security | RS256, KMS keys, hash-chained audit, object storage, pen test | listed in [11](11-security.md) §14 |

## 2. Phase plan

Each phase ends with runnable code, tests and a stop. Nothing is generated ahead of its phase.

| Phase | Scope | Exit criteria |
|---|---|---|
| **1 · Design** ✅ | Architecture, domain model, roles, workflows, ER, API contracts, folder structure, security, scope | These 15 documents |
| **2 · Foundation** ✅ | Spring Boot skeleton, Postgres, Flyway V1–V6, Spring Security, auth, identity, organization, team, project, skills | `docker compose up` runs; login works; `/auth/me` returns a real principal; repository + security tests green |
| **3 · Work item core** ✅ | Work item domain, links with cycle detection, workflow engine with guards, ticket and development lifecycles, REST endpoints | Create → transition → complete works for all six profiles; `AuthorizationMatrixIT` green; Modulith + ArchUnit verify |
| **4 · AI service** | FastAPI, schemas, validators, provider abstraction, mock provider, ticket and development analysis, repair loop | Analysis returns validated JSON for all three demo inputs; `test_no_authorization_fields` green; backend integration through `AiAnalysisClient` |
| **5 · Plan & rank** | Process templates, matching, instantiation, decomposition, skill extraction, scoring, workload | Onboarding ticket produces a cross-department plan; scoring unit tests including the protected-characteristics assertions |
| **6 · Policy** | Policy engine, level policies, six routes, approval requests, review queues, escalation | 14 auto-assign condition tests; routing replayability test; low-risk task assigns with no human |
| **7 · Frontend** | React app, role-based navigation, My Work board, ticket and work item screens, backlog, sprint board, recommendation review, dashboards | All three scenarios drivable end to end through the UI |
| **8 · Jira** | OAuth, mappings, full/webhook/reconcile sync, issue and subtask creation, assignee, transitions, outbox worker, retry, staleness | Real Jira site round-trips; webhook dedup, ordering and loop-prevention tests green; `NoJiraConnectionTest` green |
| **9 · Reporting, MCP, ops** | Reporting views and endpoints, AI analytics, MCP adapter, Compose hardening, CI, documentation | Dashboards populated; MCP tools callable from a host; CI green on all five workflows |
| **10 · Demo** | Seed data, three scenarios, end-to-end tests, demo script | Scenarios run from a clean `docker compose up` in under 10 minutes |

Before each phase: state the scope, list architectural decisions, list every file to create or update, implement complete runnable code with tests, give run commands and example requests, report remaining limitations, then stop.

## 3. Demo data

Seeded by `V900__demo_data.sql` under the `demo` profile only.

| Entity | Content |
|---|---|
| Organization | Acme Technologies |
| Departments | HR, IT, Finance, Engineering |
| Teams | Support (IT), Backend (Eng), Frontend (Eng), Platform (Eng), HR Ops (HR), Finance Ops (Finance) |
| Manager | 1 org-wide (Engineering Director) |
| Admin | 1 |
| Team Leads | 4 — Support, Backend, Frontend, Platform |
| Process Owners | 2, drawn from the employee pool — HR Operations Lead (an EMPLOYEE, deliberately, to prove the relationship model) and IT Service Lead |
| Employees | 12 across all teams, mixed `JUNIOR`/`MID`/`SENIOR` |
| Interns | 2 — one in Backend, one in Support |
| **Total users** | 20 (1 manager + 1 admin + 4 team leads + 12 employees + 2 interns) |
| Skills | ~40 canonical codes across `LANGUAGE`, `FRAMEWORK`, `PLATFORM`, `TOOL`, `DOMAIN`, `PROCESS` |
| User skills | 3–8 per person with varied proficiency and recency, engineered so scoring produces interesting near-ties |
| Workload | Pre-seeded active items giving ratios from 0.3 to 1.1 — including one `OVERLOADED` person so the capacity penalty is visible |
| Projects | Identity Platform, Payments, Internal Tools — with components |
| Sprint | One `ACTIVE` 2-week sprint, mid-flight, with carry-over |
| Policies | Org-wide defaults + a stricter Engineering department policy |
| Level policies | All five levels with the documented intern restrictions |
| Process templates | Employee Onboarding, Employee Offboarding, Access Request, Hardware Request, Software Installation, Purchase Request, Production Incident, Customer Complaint |
| Workflow definitions | All six profiles with states and transitions |

The HR Operations Lead being an `EMPLOYEE` rather than a `TEAM_LEAD` is deliberate: it demonstrates in the demo that PROCESS_OWNER is a resource relationship, not a role.

## 4. Demo scenarios

### Demo 1 — Employee Onboarding (~3 min)

**Input.** Employee files: *"Prepare all onboarding activities for a new employee starting Monday."*

| Step | Expected |
|---|---|
| 1 | Classified `PROCESS_WORK`, priority `HIGH` |
| 2 | Matches `EMPLOYEE_ONBOARDING`; AI customizes rather than inventing |
| 3 | Asks one clarification: role and start date |
| 4 | Generates 9 tasks across HR, IT and Finance with dependencies |
| 5 | Process Owner resolved to the HR Operations Lead — an `EMPLOYEE` |
| 6 | 6 low-risk tasks `AUTO_ASSIGN` with no human involved |
| 7 | The equipment-purchase task routes to `TEAM_LEAD_REVIEW` — `BUDGET_IMPACT` |
| 8 | The cross-department plan itself routes to `PROCESS_OWNER_REVIEW` |
| 9 | Assignees see their tasks in My Work |
| 10 | Jira issues created with dependency links |
| 11 | Parent progress moves as tasks complete (estimate-weighted) |
| 12 | Process Owner verifies and closes |

**The moment.** Step 6 and 7 side by side: most of the work assigns itself; the one task that touches money stops for a human. That is the whole product in one screen.

### Demo 2 — Development Story (~4 min)

**Input.** *"Users must be able to upload a profile photo."*

| Step | Expected |
|---|---|
| 1 | Classified `STORY`, `DEVELOPMENT_WORK`, project Identity Platform |
| 2 | Detects incomplete acceptance criteria, asks two questions (size/format, migration) |
| 3 | Re-analysis raises confidence 0.52 → 0.87 |
| 4 | Decomposes into backend endpoint, frontend upload UI, storage provisioning, integration test |
| 5 | Skills extracted per task: `SPRING_BOOT`, `REACT`, `OBJECT_STORAGE`, `SECURITY` |
| 6 | Candidates ranked with all seven score components visible |
| 7 | Storage task routes to `TEAM_LEAD_REVIEW` — only one person holds `OBJECT_STORAGE`, and they are at 0.94 workload |
| 8 | Team Lead edits the decomposition and approves |
| 9 | Tasks added to Sprint 24 with a capacity check |
| 10 | Developer moves a task through `IN_PROGRESS → CODE_REVIEW → TESTING → DONE` |
| 11 | Attempting `IN_PROGRESS → DONE` directly is impossible — no such edge |
| 12 | Story progress recalculates (story-point weighted); Jira transitions follow |

**The moment.** Step 6 — the score breakdown panel showing *why* each candidate ranked where they did, including the person excluded for a level restriction.

### Demo 3 — Ticket to Bug (~3 min)

**Input.** *"Payments fail intermittently during checkout."*

| Step | Expected |
|---|---|
| 1 | Classified `INCIDENT_WORK`-adjacent; `likely_defect = true`, `production_impact = true` |
| 2 | A `BUG` is created and linked `TCK-102 RESOLVED_BY DEV-456` |
| 3 | Ticket enters `WAITING_DEVELOPMENT`; SLA clock pauses |
| 4 | Bug routed to the Payments team |
| 5 | Production impact routes the fix task to `MANAGER_APPROVAL` |
| 6 | Bug decomposes into root cause analysis, code fix, unit test, integration test, regression test |
| 7 | Dependencies enforce root-cause-before-fix — `START_WORK` is blocked on the fix until analysis completes |
| 8 | Bug progresses to `VERIFIED` |
| 9 | Ticket automatically returns to `RESOLVED` |
| 10 | The original requester verifies and the ticket closes |
| 11 | Full audit trail queryable by one correlation ID |

**The moment.** Steps 9–10: the requester never learned what a bug ticket is, and still got told their problem was fixed and asked to confirm.

## 5. Success criteria

| # | Criterion | Verified by |
|---|---|---|
| 1 | `docker compose up` produces a working system from a clean checkout | `make demo` in CI |
| 2 | All three scenarios complete end to end through the UI | Playwright e2e |
| 3 | The system works with no Jira connection | `NoJiraConnectionTest` |
| 4 | The system works with no LLM key | mock provider tests |
| 5 | No task is auto-assigned that violates any policy condition | 14 policy tests |
| 6 | No employee can assign work to another employee | authorization tests, REST and MCP |
| 7 | Review and test stages cannot be skipped | `ReviewStageCannotBeSkippedTest` |
| 8 | No protected characteristic exists in the schema or scoring | two CI tests |
| 9 | No per-employee performance score exists anywhere | reporting review + view definitions |
| 10 | Every approval is auditable with before/after and a correlation ID | audit integration test |
| 11 | Module boundaries hold | `ModularityTest`, `ArchitectureTest` |
| 12 | The OpenAPI spec matches the implementation | `openapi-drift.yml` |

## 6. Known risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Scope is very large for a hackathon | **High** | Strict phase gating; each phase ships something runnable. If time runs short, Phase 9's MCP and reporting are the first to cut — both are additive. |
| LLM decomposition quality varies | Medium | Few-shot examples from the seeded templates; vagueness validator; repair loop; human review on anything below confidence |
| Jira mapping differs per site | Medium | Every mapping has a documented fallback; unmapped types degrade to Task + label rather than failing |
| Demo depends on network | Medium | Mock providers for both external dependencies, selected automatically |
| Policy misconfiguration auto-assigns something it should not | Low | Conservative defaults, score gap and floor conditions, full audit, and an AI-metrics dashboard that surfaces drift immediately |
| Scoring produces obviously wrong candidates on thin data | Medium | Seed data engineered to make ranking meaningful; exclusion reasons shown so "why not X?" is always answerable |

## 7. What Phase 1 deliberately does not answer

Stated so the gaps are visible rather than discovered later:

- **Exact prompt text.** Prompts are Phase 4 artefacts; their structure and versioning are specified here, their wording is not.
- **Concrete UI layouts.** Page inventory and component inventory are specified; visual design is Phase 7.
- **Final weight tuning.** The seven components and their default weights come from the brief; whether 30/20/15/10/10/10/5 is right for a given organization is an empirical question the policy table exists to answer.
- **Jira custom field IDs.** Site-specific, discovered at mapping time.
- **Performance targets.** No load testing is planned for the MVP; the indexes are designed from the actual dashboard queries, but no throughput number is claimed.
