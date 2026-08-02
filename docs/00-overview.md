# 00 — Overview, Assumptions & Non-Goals

## 1. Problem statement

Enterprise work arrives through two doors that most tools treat as unrelated:

1. **Operational work** — an employee raises a ticket ("I need a laptop", "payments are failing", "onboard the new hire starting Monday"). It is unstructured, often incomplete, and frequently spans HR, IT, Finance and Engineering at the same time.
2. **Planned work** — an epic, story, bug or technical-debt item created during project planning or imported from Jira. It is structured but often under-specified, and someone has to decide who does it.

In both cases the same expensive human loop repeats: read it, work out what it actually is, find the missing information, decide which department owns it, break it down, find someone with the right skills who is not already drowning, and decide whether the decision needs approval.

SmartWork AI automates the analysis and the ranking, and **keeps the decision deterministic**.

## 2. Core product thesis

> The LLM proposes. The policy engine disposes.

The AI service classifies, extracts, decomposes and explains. It never assigns anyone, never changes workflow state, never approves anything and never touches Jira. Every decision that has organizational consequence is made by deterministic Java code with stored, auditable inputs.

This is not a stylistic preference. It is what makes the system defensible: an assignment can be replayed and explained from stored score components, and an authorization outcome never depends on how a prompt was phrased.

## 3. The two things that make this different from "Jira + a chatbot"

### 3.1 A Task does not need a Ticket

Most ticket-AI demos assume `Ticket → Task`. Real work does not look like that. A Task in SmartWork AI can originate from any of eight sources (`USER_CREATED`, `JIRA_IMPORTED`, `TICKET_GENERATED`, `AI_GENERATED`, `PROCESS_TEMPLATE`, `PROJECT_PLANNING`, `INCIDENT_GENERATED`, `RECURRING`) and may hang off a Project, Epic, Feature, Story, Backlog, Sprint or Incident — or nothing at all.

Consequence for the data model: `ticket_id` is **not** a column on work items. Ticket linkage, when it exists, is a typed relationship (`GENERATED_FROM`, `RESOLVED_BY`) in `work_item_links`. `project_id`, `sprint_id` and `parent_id` are all nullable.

### 3.2 Managers do not approve everything

Approval is a routing outcome, not a default. The policy engine emits one of six routes:

| Route | Who acts | Typical trigger |
|---|---|---|
| `AUTO_ASSIGN` | nobody | low risk, confidence ≥ 0.85, single team, no sensitive flags |
| `TEAM_LEAD_REVIEW` | Team Lead | medium risk, ambiguous classification, close candidate scores |
| `PROCESS_OWNER_REVIEW` | Process Owner | cross-department plan, no template match, unclear business scope |
| `MANAGER_APPROVAL` | Manager | critical / security / production / budget / legal, or an escalation |
| `REQUEST_MORE_INFORMATION` | Requester | AI flagged missing information |
| `MANUAL_EXCEPTION_REVIEW` | Team Lead or Admin | policy evaluation itself failed or hit an exception rule |

The demo-visible consequence: in the onboarding scenario, most tasks are assigned with no human in the loop at all, and only the budget-touching task stops for review. That contrast is the pitch.

## 4. Assumptions

These are decisions taken in the absence of a stakeholder. Each is cheap to revisit.

### 4.1 Organizational

| # | Assumption | Rationale / impact |
|---|---|---|
| A1 | Single-tenant deployment, but `organization_id` is present on every business table from day one | Multi-tenancy is nearly free at design time and prohibitively expensive to retrofit. Enforced by a Hibernate filter + a resolved `OrganizationContext`. |
| A2 | A user belongs to exactly one department, but may belong to multiple teams | Matches how most engineering orgs work; keeps workload aggregation unambiguous at department level. |
| A3 | A team belongs to exactly one department | Simplifies the department/team match component of the scoring formula. |
| A4 | Employee working capacity is configured per user in hours/week and defaults to 40 | Real capacity systems (leave, part-time, calendars) are post-MVP. |
| A5 | Skills are a controlled vocabulary with canonical codes (`SPRING_BOOT`, not "spring boot") | Free-text skills make matching unusable. AI output is mapped onto the vocabulary; unrecognized skills are stored as `unmatched` for admin curation, never invented into the taxonomy at runtime. |
| A6 | Skill proficiency is 1–5 | Enough resolution for weighted matching without pretending to precision. |

### 4.2 Product

| # | Assumption | Rationale / impact |
|---|---|---|
| A7 | "Ticket" is not a distinct entity — it is a Work Item of type `REQUEST` or `INCIDENT` in family `SERVICE_WORK` / `INCIDENT_WORK` | One table, one lifecycle engine, one link model. The UI still says "Ticket" because that is what users call it. |
| A8 | Story Points are Fibonacci (1,2,3,5,8,13,21) and only meaningful on `EPIC`, `FEATURE`, `STORY`, `BUG` | Avoids the "estimate everything twice" problem; hours remain the universal unit. |
| A9 | Sprints are 2 weeks | Only affects seed data and capacity defaults. |
| A10 | SLA is modelled as a target duration per priority per work family, evaluated at read time | A full SLA calendar engine (business hours, holidays, pause-on-blocked) is post-MVP. |
| A11 | One active AI analysis per work item; re-analysis supersedes rather than versions | Keeps the UI simple. Superseded analyses are retained for the AI-acceptance metrics. |
| A12 | Notifications are in-app only | Email/Slack fan-out is a post-MVP notification channel plug-in. |

### 4.3 Integration

| # | Assumption | Rationale / impact |
|---|---|---|
| A13 | Jira is the system of record for issue *content*; SmartWork AI is the system of record for *analysis, recommendations, policy and approvals* | Defines conflict resolution: on a field collision during reconciliation, Jira wins for summary/description/status; SmartWork wins for recommendation state. |
| A14 | One Jira Cloud site per organization for the MVP | The schema supports several (`jira_connections` is a table, not a row), but the UI assumes one. |
| A15 | Jira may be entirely absent | Every Jira call sits behind the outbox. With no connection, work items simply have `jira_issue_key = null` and events sit in `PENDING`. Nothing else breaks. |
| A16 | Webhook delivery is at-least-once and unordered | De-duplicated on `(jira_event_id)` with a payload-hash fallback; ordering resolved via `jira_updated_at` comparison, not arrival order. |
| A17 | The LLM may be entirely absent or may fail | `MockLLMProvider` returns deterministic, scenario-aware analyses. The demo runs identically offline. |

### 4.4 Explicit design constraints derived from the brief

- FastAPI never stores Jira tokens, never writes to Jira, never makes authorization decisions, never mutates workflow state.
- The frontend never calls Jira or an LLM provider.
- The MCP adapter never opens a database connection and never bypasses policy.
- REST controllers never return JPA entities.

## 5. Non-goals for the MVP

Called out so reviewers do not read absence as oversight:

- **No employee performance scoring or ranking.** The platform reports work *distribution*, delivery efficiency and process problems. It deliberately produces no per-person score. This is an ethical constraint, not a backlog item.
- **No protected characteristics anywhere in scoring.** Gender, age, race, ethnicity, religion, disability, political affiliation and family status are absent from the schema, the scoring inputs and the AI prompts. There is a scoring unit test that asserts the candidate feature vector contains only the seven permitted components.
- **No microservice decomposition of the Java side.** One modular monolith, module boundaries enforced by Spring Modulith tests.
- **No Kafka, no Kubernetes, no service mesh.** The outbox is a Postgres table drained by a scheduled worker with `FOR UPDATE SKIP LOCKED`.
- **No RAG, no vector store, no fine-tuning.** Analysis is single-shot structured extraction against a strict schema. Duplicate detection in the MVP is trigram similarity in Postgres (`pg_trgm`), not embeddings.
- **No real-time collaboration**, presence, or WebSocket push. TanStack Query polling on dashboards.
- **No mobile layout.** Desktop-first, responsive down to tablet.

## 6. What "done" means for the MVP

Three end-to-end scenarios run against a seeded database, through the real UI, with Jira synchronization visible:

1. **Employee Onboarding** — ticket → template match → cross-department task plan → mixed auto-assign / review → Process Owner closure.
2. **Development Story** — story → incomplete acceptance criteria → clarification → decomposition → candidate ranking → sprint → code review → testing → done.
3. **Ticket to Bug** — support ticket → defect detection → linked Bug → decomposition → fix → requester verification → ticket closed.

Detail in [14-scope-and-roadmap.md](14-scope-and-roadmap.md).

## 7. Glossary

| Term | Meaning here |
|---|---|
| **Work Item** | The single persisted unit of work. Every ticket, epic, story, bug, task and subtask is one. |
| **Work family** | Coarse behavioural grouping that selects defaults (workflow, SLA, template pool). |
| **Origin type** | How the work item came into existence. Drives analytics (planned vs unplanned) and skip rules. |
| **Recommendation** | An AI-derived proposal (classification + plan + ranked candidates) awaiting a policy route. |
| **Route** | The policy engine's output: who, if anyone, must act on a recommendation. |
| **Process Owner** | A per-resource relationship, not a global role. Owns a specific ticket, epic, feature or process instance. |
| **Requester** | The creator of a ticket, as a relationship on that ticket. Never a global role. |
| **Stale recommendation** | A recommendation whose underlying work item changed in Jira after generation. Cannot be applied. |
