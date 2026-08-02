# 13 — Decision Log (ADRs)

Each entry: the decision, why, what was rejected, and what it costs.

---

### ADR-001 — One Work Item table, not separate ticket and issue tables

**Decision.** Tickets, incidents, epics, features, stories, bugs, tasks and subtasks are rows in `work_items`, discriminated by `work_item_type`.

**Why.** The brief requires typed links *between* tickets and development items (`Ticket RESOLVED_BY Bug`). Separate tables force polymorphic foreign keys, which Postgres cannot constrain. Everything downstream — analysis, decomposition, ranking, routing, progress rollup, workload — operates identically on all of them.

**Rejected.** Separate `tickets` and `work_items` tables (link integrity impossible); single-table inheritance with per-type subclass tables (join cost on the hottest table for little gain).

**Cost.** Many nullable columns. Mitigated by type-aware validation in `WorkItemValidator` and partial CHECK constraints.

---

### ADR-002 — No `ticket_id` column anywhere

**Decision.** Ticket linkage is a typed link (`GENERATED_FROM`, `RESOLVED_BY`), never a foreign key on the work item.

**Why.** The brief is emphatic that a Task may originate from eight different sources. A `ticket_id` column is an invitation to write code that assumes it is populated, and that assumption breaks the moment a task comes from project planning.

**Rejected.** Nullable `ticket_id` "for convenience" — convenience columns become load-bearing.

**Cost.** "Which ticket produced this?" is a join rather than a column read. Indexed on both link directions.

---

### ADR-003 — Modular monolith, not microservices

**Decision.** One Spring Boot application with 19 Spring Modulith modules.

**Why.** The approval operation must atomically touch six tables. As microservices that becomes a distributed saga for the single most important write in the product — enormous complexity for a hackathon MVP with no scaling pressure.

**Rejected.** Microservices per bounded context; Maven multi-module (slower builds, more ceremony, same result).

**Cost.** One deployment unit. Mitigated by compile-time-verified module boundaries, so extraction stays cheap if it is ever needed.

---

### ADR-004 — Separate FastAPI service for AI

**Decision.** AI analysis is a separate Python process.

**Why.** Different ecosystem (Pydantic, LLM SDKs), different failure profile (must fail without taking the platform down), different scaling shape (slow, bursty, IO-bound). Isolating it also makes the "no authority" guarantee structural: the container has no database and no Jira credentials, so the boundary is enforced by deployment, not discipline.

**Rejected.** Calling an LLM from Java (weaker structured-output ergonomics, and the isolation guarantee becomes a code convention rather than a deployment fact).

**Cost.** One more service and a network hop. Both acceptable; the hop is off the request path anyway.

---

### ADR-005 — The AI never makes assignment or authorization decisions

**Decision.** `AnalysisResponse` contains no assignee, no approval and no route field. Candidate ranking and routing are deterministic Java.

**Why.** Assignment must be explainable, replayable and testable. An LLM cannot provide those properties, and an LLM given authority is an authorization system that can be talked out of its rules. This is the product's central design claim.

**Rejected.** "Let the model suggest an assignee, humans review" — reviewers rubber-stamp, and the reviewed artefact would be unexplainable.

**Cost.** More Java code. It is also the code most worth having, and the easiest to unit-test.

---

### ADR-006 — Postgres outbox instead of a message broker

**Decision.** `outbox_events` drained by a `@Scheduled` relay using `FOR UPDATE SKIP LOCKED`.

**Why.** Transactional correctness by construction: the event and the business change commit together. No dual-write problem, no extra container, and `SKIP LOCKED` gives safe multi-instance draining for free.

**Rejected.** Kafka and RabbitMQ (operational weight far beyond the volume); direct Jira calls inside the transaction (holds locks across a slow network call, and Jira downtime fails approvals).

**Cost.** Polling latency of up to 2 seconds. Irrelevant for Jira synchronization.

---

### ADR-007 — Configurable workflow profiles as data

**Decision.** Six seeded profiles in `workflow_definitions` / `_states` / `_transitions`; `work_items.status` is a `VARCHAR` validated against the item's profile.

**Why.** The brief requires six different lifecycles and admin-configurable profiles. A Java enum cannot express that, and hard-coded state machines would need a deployment per team-specific variation.

**Rejected.** A global status enum with per-type filtering (cannot express different edges); a workflow library (overkill).

**Cost.** Status is not enum-safe at the type level. Mitigated by the API accepting transition *names* only, so a status string can never arrive from a client.

---

### ADR-008 — Two-axis authorization: system roles × resource relationships

**Decision.** `EMPLOYEE/TEAM_LEAD/MANAGER/ADMIN` are global roles. `REQUESTER/ASSIGNEE/PROCESS_OWNER/WATCHER/APPROVER/REVIEWER` are per-item relationships derived at evaluation time.

**Why.** The brief demands it, and it is correct: making REQUESTER a global role means anyone who ever filed a ticket has requester rights over every ticket. Process ownership is inherently per-resource.

**Rejected.** A flat role list (cannot express "owner of this one process"); a full ABAC/policy-language engine (too heavy for the MVP).

**Cost.** Every access check needs the item, so authorization is a query. Mitigated by pushing visibility predicates into SQL rather than fetching then filtering.

---

### ADR-009 — 404, not 403, for invisible resources

**Decision.** An item the caller may not see returns `404`. `403` means "visible, but you may not do that".

**Why.** `403` confirms existence and leaks the ID space and the org structure.

**Cost.** Slightly less helpful errors for legitimate users. Correct trade.

---

### ADR-010 — Managers do not approve routine work

**Decision.** The policy engine emits six routes; `AUTO_ASSIGN` requires no human at all.

**Why.** It is the product thesis. A system where every task needs approval automates nothing.

**Rejected.** Approval-everything with a bulk-approve button — the same bottleneck with a faster click.

**Cost.** Trust in the policy configuration. Mitigated by conservative defaults, per-department overrides, full auditability, and an AI-metrics dashboard where a wrong threshold shows up immediately as a falling auto-assignment rate.

---

### ADR-011 — Auto-assign additionally requires a score gap and a score floor

**Decision.** Beyond the brief's twelve conditions, add `topScore − secondScore ≥ 5` and `topScore ≥ 60`.

**Why.** Without the gap, auto-assign fires on coin-flips between equally good candidates — technically compliant, obviously arbitrary to a user. Without the floor, it fires on the best of a uniformly unsuitable pool.

**Cost.** A slightly lower auto-assignment rate. Every avoided case is one a human should have seen.

---

### ADR-012 — Workload is hours over capacity, never task count

**Decision.** `openEstimatedHours / remainingCapacityHours`, with typed fallback estimates and partial counting for long-blocked work.

**Why.** Five 1-hour tasks and five 40-hour tasks are not the same load. Task count is the single most common way capacity systems become useless.

**Cost.** Depends on estimates. Mitigated by per-type fallbacks and story-point conversion, so missing estimates degrade rather than break the calculation.

---

### ADR-013 — Protected characteristics are absent from the schema

**Decision.** No column for gender, age, race, ethnicity, religion, disability, political affiliation or family status. Two CI tests enforce it — one on the score components, one on the `users` table columns.

**Why.** "Excluded from the formula" is a policy that erodes. "The column does not exist" is a property. The schema test is the one that matters: it fails the build if anyone ever adds such a column, whatever their intent.

**Cost.** None for this product.

---

### ADR-014 — No per-employee performance score, ever

**Decision.** Reports aggregate by team, department and role. `v_ai_metrics` cannot be grouped by assignee. Workload bands are capacity signals shown to a Team Lead, not ratings.

**Why.** Explicitly required by the brief, and correct. A system holding skills, estimates, cycle times and reopen rates is one `GROUP BY` away from a surveillance tool, and that changes what employees enter into it — which destroys the data quality the whole product depends on.

**Cost.** A "top performers" dashboard some buyers ask for. Declining it is the right answer.

---

### ADR-015 — Mock providers are first-class, not stubs

**Decision.** `MockLLMProvider` and `MockJiraClient` are deterministic, scenario-aware implementations selected automatically when credentials are absent.

**Why.** A hackathon demo cannot depend on conference wifi or a rate limit. Determinism also makes the AI-path integration tests hermetic and fast.

**Rejected.** Recorded fixtures only (brittle for unseen inputs); demo-only branches in production code paths (untested code shipping alongside tested code).

**Cost.** Maintaining a second implementation of two interfaces. It pays for itself the first time the network fails on stage.

---

### ADR-016 — Structured output via schema constraint + one repair retry

**Decision.** Tool/JSON-schema-constrained generation, Pydantic validation, exactly one repair attempt with the validation errors appended, then fail to `MANUAL_EXCEPTION_REVIEW`.

**Why.** One retry captures nearly all recoverable failures. Beyond that, repeated attempts burn latency and tokens on a model that has misunderstood the task; a human is the better next step.

**Cost.** Some analyses reach a human. Visible in the `analysis_validation_failure_rate` metric, which is exactly where it should be.

---

### ADR-017 — Vague task titles fail validation

**Decision.** Regex-based rejection of "Work on backend", "Fix issue", and titles under three words, applied as a Pydantic validator.

**Why.** The brief forbids vague tasks. A prompt instruction is a request; a validator is a guarantee — and the failure feeds the repair loop, which usually produces a specific title on the retry.

**Cost.** Occasional false positives on legitimately terse titles. Acceptable; the repair loop resolves them.

---

### ADR-018 — MCP adapter is a translator with no privileges

**Decision.** Every tool is a thin wrapper over an existing REST endpoint called with the end user's own JWT. No database, no Jira credentials, no ability to force an assignment.

**Why.** Agentic access must not be a second, weaker authorization path. Making the adapter privilege-free by construction is the only version of this that stays true under change.

**Cost.** Slightly chattier than direct DB access. Irrelevant at this scale.

---

### ADR-019 — Jira wins content, SmartWork wins decisions

**Decision.** On a reconciliation conflict, Jira is authoritative for summary/description/status/priority; SmartWork is authoritative for analysis, recommendations, routing and approvals. Conflicts are surfaced, not silently merged.

**Why.** Users edit issues in Jira and expect that to stick. But approvals and audit trails are the platform's reason to exist and must not be overwritable from outside.

**Cost.** Conflicts need an admin surface. Built into `/jira/sync-status`.

---

### ADR-020 — Reference keys independent of Jira

**Decision.** Every work item gets a local `reference_key` (`TCK-102`, `DEV-456`) at creation, independent of `jira_issue_key`.

**Why.** The platform must be fully usable before, during and after a Jira outage, and imported items must not collide with locally created ones.

**Cost.** Two identifiers in the UI. Shown as `TCK-102 · PLAT-88`, which users find clearer than either alone.

---

### ADR-021 — Refresh token rotation with reuse detection

**Decision.** Single-use rotating refresh tokens stored hashed; presenting a consumed token revokes the whole family and raises an audited security event.

**Why.** It converts silent token theft into a detectable event, at almost no implementation cost.

**Cost.** A client that races two refreshes logs itself out. Mitigated by single-flight refresh in the API client.

---

### ADR-022 — Prompt injection is defended structurally, not by pattern matching

**Decision.** Sanitization and detection exist, but the primary defence is that the AI service has no authority to abuse.

**Why.** Pattern-matching defences are an arms race that the defender loses. A successful injection here produces a bad *suggestion*, which the policy engine and possibly a human then evaluate — the same treatment a bad suggestion from any source receives.

**Cost.** None. The structural property was required for other reasons anyway.

---

### ADR-023 — UUID v7 primary keys

**Decision.** UUID v7, generated in the application.

**Why.** IDs are known before insert (needed for outbox events in the same transaction), no sequence coordination, and time-ordering keeps B-tree locality good — unlike v4, which fragments indexes.

**Rejected.** `BIGSERIAL` (leaks volume, awkward for pre-insert generation); UUID v4 (index fragmentation).

**Cost.** 16 bytes per key. Irrelevant at this scale.

---

### ADR-024 — Enums as VARCHAR + CHECK, not native Postgres enums

**Decision.** Enum-valued columns are `VARCHAR` with a `CHECK` constraint.

**Why.** Adding a value to a native PG enum requires DDL that has historically been awkward inside transactions and migrations; a CHECK constraint is trivially replaceable in a Flyway script.

**Cost.** A few bytes and no native type safety. The application enums and CI drift tests provide it instead.

---

### ADR-025 — Trigram duplicate detection, not embeddings

**Decision.** `pg_trgm` similarity on `summary` to flag possible duplicate tickets.

**Why.** No extra infrastructure, no embedding pipeline, no vector store. Good enough to catch the "printer broken again" class of duplicate, which is the overwhelming majority.

**Rejected.** pgvector + an embedding model — real value, but a whole subsystem for a secondary feature.

**Cost.** Misses semantic duplicates with no lexical overlap. Listed as post-MVP.

---

### ADR-029 — A composite foreign key enforces valid workflow states *(Phase 3)*

**Decision.** `work_items` carries `FOREIGN KEY (workflow_definition_id, status) REFERENCES workflow_states (workflow_definition_id, code)`. As a consequence, the workflow seed became a **versioned** migration that upserts states rather than a repeatable one that replaces them.

**Why.** `status` is a `VARCHAR` because the legal set depends on the item's profile (ADR-007), which normally means the database cannot check it. But `workflow_states` is already unique on `(workflow_definition_id, code)`, so the composite FK costs nothing and makes an invalid status *unrepresentable* rather than merely rejected by application code.

**What it forced.** A repeatable migration that deletes and re-inserts states fails the moment any work item references one. So states are now upserted, and removing a state requires a new versioned migration — which is correct: a state that work items may be sitting in is not something to drop on a checksum change.

**Also fixed by this.** Flyway runs repeatable migrations *after* every versioned one, so the workflow seed was landing after `V900` demo data, which references definition IDs by foreign key. Making it `V6_1` makes the ordering explicit instead of accidental.

**Cost.** Iterating on workflow definitions now needs a migration rather than an edit-and-restart.

---

### ADR-030 — Guards evaluate a context record, not the database *(Phase 3)*

**Decision.** `TransitionGuard` takes a `GuardContext` record assembled by the caller. The `workflow` module's only declared dependency is `common`.

**Why.** The alternative — guards that query work items, users and sprints themselves — would make the workflow module depend on half the system, and would make the ten guards untestable without a database and a web context. As it stands, `StandardGuardsTest` covers every enforcement rule in the platform as pure function calls.

**Cost.** `GuardContextAssembler` runs a handful of queries per transition to populate facts a given guard may not need. Worth it: the state machine stays a state machine.

---

### ADR-031 — Links are stored once, in a canonical direction *(Phase 3)*

**Decision.** `A CHILD_OF B` is persisted as `B PARENT_OF A`; `BLOCKS` as `DEPENDS_ON`; `RESOLVES` as `RESOLVED_BY`. The read model renders both directions.

**Why.** Storing both halves makes `UNIQUE (source, type, target)` meaningless and lets the two rows drift apart under concurrent edits. Canonicalizing means "A blocks B" and "B depends on A" are provably the same fact — a test asserts the second request returns `409` rather than creating a duplicate.

**Cost.** The read path has to invert incoming links for display. About ten lines.

---

### ADR-027 — Tenancy by explicit query predicate, not a Hibernate filter *(Phase 2, revises 11-security §4)*

**Decision.** Every repository method that touches a tenant entity takes `organizationId` as a parameter. The Hibernate `@Filter` described in the security document is deferred.

**Why the change.** A `@Filter` must be enabled on the Hibernate `Session`, which means either an AOP advice ordered to run *inside* the transaction advice, or an interceptor tied to session lifecycle. Both are fragile in ways that fail silently: if the filter does not activate, the query returns **every tenant's rows** and nothing errors. An explicit parameter has the opposite failure mode — a method that forgets the tenant does not compile.

`CrossOrganizationIsolationIT` verifies the outcome either way, including that pagination totals match the visible set so existence cannot leak through a count.

**Cost.** More verbose repository signatures, and the discipline is per-method rather than global. The filter remains worth adding later as defence in depth; it is not worth adding as the only defence.

---

### ADR-028 — Cross-module references are UUIDs, never JPA associations *(Phase 2)*

**Decision.** `User.departmentId`, `Team.leadUserId` and `TeamMember.userId` are plain UUID columns. JPA associations exist only within a module.

**Why.** A `@ManyToOne Department` on `User` would make `organization.domain` — a Spring Modulith *internal* package — a compile dependency of `identity`, which `ModularityTest` rejects. Beyond the tooling, entity graphs spanning modules are what make a monolith impossible to decompose later: extracting a module becomes an exercise in unwinding a lazy-loading graph rather than replacing a service call.

**Cost.** Referential integrity is enforced by the database foreign key and by an explicit existence check in the service (`TeamService.addMember` calls `UserDirectory.findSummary` before adding), rather than by the ORM. That check is also where the cross-tenant guard lives, so it is not wasted work.

---

### ADR-026 — Demo data in a separate migration location

**Decision.** Seed data lives in `db/demo/V900__demo_data.sql`, activated only by the `demo` Spring profile.

**Why.** A clean environment must never inherit seventeen fake employees. Keeping it in the main migration path makes that inevitable.

**Cost.** One extra Flyway location configuration.

---

### ADR-032 — Providers return raw text, not a validated model *(Phase 4, revises 07-ai-service-contract §7)*

**Decision.** `LLMProvider.complete_structured()` returns `(raw_json: str, ProviderMeta)`, not `(BaseModel, ProviderMeta)` as originally specified. Pydantic parsing, the post-validators (vocabulary, vagueness, dependency graph), and the one-shot repair retry all live in `services/analyzer.py`, outside every provider.

**Why the change.** If each provider parsed and validated its own output, the repair loop (contract §8) would need to live inside every provider implementation, duplicated between `AnthropicProvider`, `MockProvider` and `EchoProvider` — or `MockProvider` would have to fake a validation failure just to exercise the retry path. Keeping providers as dumb text-in/text-out functions means the exact same validation code processes real and mock output, so `test_repair_loop.py` exercises production validation logic using `EchoProvider`, not a parallel test-only implementation of it.

**Rejected.** Validating inside each provider (duplicated logic, retry path untestable without a real or heavily-mocked LLM call).

**Cost.** A provider can't reject its own obviously-malformed output early; it always makes the round trip through `analyzer.py`. For `MockProvider` this costs nothing (it never produces malformed JSON in the first place, other than in `EchoProvider`'s scripted test fixtures). For `AnthropicProvider`, a response that fails to parse as JSON at all is still surfaced as a validation error through the same path rather than a distinct provider-level exception, which is one fewer error type the API layer needs to handle.

---

### ADR-041 — Flyway runs out-of-order, because demo data lives in the 900 band *(Phase 6, refines ADR-026)*

**Decision.** `spring.flyway.out-of-order=true`.

**Why.** ADR-026 put demo data in `V900__demo_data.sql` so it sorts after every real migration and only loads under the `demo` profile. That works, and it has a consequence nobody wrote down at the time: once a database has applied V900, *every* future schema migration is numbered below the highest applied version, which is Flyway's definition of out-of-order. The default response is to refuse to start:

> Detected resolved migration not applied to database: 12.

So the choice is not "should migrations be allowed out of order" in the abstract — it is "should adding V12 to a demo database be possible at all". Three ways out were available:

1. **Number new migrations above 901.** Works once. The next demo migration has to go higher again, and the version number stops meaning anything.
2. **Make demo data repeatable (`R__`).** Structurally the cleanest — repeatable migrations run after all versioned ones by definition, which is exactly the requirement, and the seed is already idempotent. Rejected for now because ordering *between* two repeatable migrations is alphabetical by description, so `R__demo_data` / `R__demo_planning` would depend on a naming coincidence rather than a stated order — and ADR-029 is a standing reminder of what implicit migration ordering costs.
3. **Allow out-of-order.** One setting, and it makes the 900-band convention behave the way it was always assumed to.

**Cost.** Flyway will apply a migration numbered below one already applied, so a migration written today can land after a demo seed written months ago. That is safe only while migrations are additive and mutually independent — which they are, and which the existing schema-validation checks would catch if they stopped being. It also means the schema history is not strictly ordered by version, so reading it requires sorting by `installed_rank` rather than `version`.

**If demo data grows a third file, revisit option 2.** Two files ordered by luck is tolerable; three is not.

---

### ADR-037 — A routing decision pins its policy row, version and engine version *(Phase 6, extends 08-policy-and-assignment §8)*

**Decision.** `recommendations` gains `assignment_policy_id`, `assignment_policy_version` and `policy_engine_version`. Every routing decision records all three.

**Why.** docs/08 §8 promises a historical decision can be "replayed against the policy that was actually in force", but the design stores only the route and its reason codes — the answer, not the question. `assignment_policies` is mutable and versioned, so replaying a year-old decision against the row as it reads today would silently use current thresholds and conclude the engine had changed its mind.

`policy_engine_version` is the subtler one. Thresholds live in data and are versioned by the row; the *rules* live in code and were not versioned at all. A decision replayed under changed logic is not the same decision, and without this column that difference is invisible. It is bumped when a rule changes, not when a threshold does.

**Cost.** Three columns, and the discipline of bumping `PolicyEngine.VERSION` when the logic changes — which a reviewer has to notice, since nothing enforces it.

---

### ADR-038 — `RecommendationAccess` takes primitives, not a `RoutingDecision` *(Phase 6)*

**Decision.** The interface `policy` uses to record a decision against a recommendation accepts a route name, a CSV of reason codes and the policy identifiers, rather than the `RoutingDecision` value object.

**Why.** `policy` already depends on `analysis::application` to read the analysis it routes on. Accepting a policy type on that interface would make `analysis` depend on `policy::domain` in return — a module cycle Spring Modulith rejects, and rightly so. Passing primitives is not a workaround for the tool; it reflects the actual relationship. The module that *stores* a decision has no business knowing how decisions are made, and a `String` route plus a CSV is exactly the coupling the job requires.

**Rejected.** Moving the policy domain types into `common` to break the cycle — that makes `common` the place where any two modules' shared vocabulary accumulates, and the policy domain is not shared vocabulary, it is one module's core.

**Cost.** The route name and reason codes lose their type safety at the boundary. Mitigated by `PolicyDtos.ReasonResponse`, which parses them back on read and — deliberately — renders an unrecognised code rather than dropping it, so a decision recorded by an older engine version stays readable after the enum moves on.

---

### ADR-039 — A system assignment path separate from the actor path *(Phase 6)*

**Decision.** `WorkItemAssignment.assignBySystem` sets an assignee with no actor permission check. Tenancy and existence are still validated.

**Why.** When a Team Lead assigns someone, the question is "may *you* place this work". When the policy engine assigns someone, there is no "you" — the authorization already happened upstream, in the eligibility filter and the fourteen auto-assign conditions, and it is more thorough than any actor check would be.

The alternative was to invent a synthetic system principal with enough rights to do anything. That is worse in a specific way: a principal that can do anything is a permanent hole in the authorization model that any future code path can pick up and use, whereas a single narrowly-named method that does one thing is visible in every call site and greppable.

**Cost.** A method exists that bypasses actor permissions. Named to be conspicuous, documented at the interface, and reachable only from `AssignmentApplier`.

---

### ADR-040 — Escalation creates a linked successor rather than mutating the request *(Phase 6)*

**Decision.** Escalating closes the original request as `ESCALATED` and opens a new one at the higher route, linked by `escalated_from_id`, with a mandatory reason.

**Why.** Changing the original's route in place would lose the fact that a Team Lead saw it, could not decide, and why — which is the most interesting part of the record. docs/08 §5.4 also makes "Team Lead escalation" a MANAGER_APPROVAL trigger in its own right, so the successor's reason codes should say that explicitly rather than inheriting the original's.

The mandatory reason is enforced in the entity *and* by a check constraint. An escalation with no reason is the least useful row in an audit trail: it records that someone passed the decision upward and nothing about the judgement they were exercising.

**Cost.** Two rows per escalation, and any "current state of this item" query must follow the chain to the open leaf.

---

### ADR-034 — No `v_user_workload` view; the formula lives once, in Java *(Phase 5, revises 05-database-er §7 and 08-policy-and-assignment §6)*

**Decision.** The workload formula is implemented solely in `WorkloadCalculator`, a pure Java function. The `v_user_workload` view the design proposed is not created. The "one round trip rather than N" requirement is met instead by `CandidateQueryRepository`, which fetches open items, review counts and active counts for the whole candidate pool in one query each.

**Why the change.** A view that materializes the numerator and denominator has to encode the fallback-estimate table, the story-point conversion, the 30% blocked weighting and the meeting-overhead deduction *in SQL* — while `WorkloadCalculator` encodes the same rules in Java for the unit tests. Two implementations of one formula, in two languages, that must agree. They would not stay in agreement: the first time someone adds a work item type, they would update the Java table and the tests would pass.

The design's stated goal was performance ("scoring is one join rather than N queries"), and that goal is fully met by batching. What the view would have added on top is a second copy of the business rule, which is a cost, not a benefit.

**Rejected.** Building the view and adding a reconciliation test asserting it agrees with the calculator on the demo dataset — plausible, but it makes the drift detectable rather than impossible, and only for the rows the demo happens to cover.

**Cost.** Aggregation happens in the JVM rather than in Postgres, so a pool of several thousand candidates would move more rows over the wire than a view would. At the scale this system targets — a pool is one team or one department — that is a few hundred rows. If it ever stops being true, the fix is a materialized view *generated from* the Java definition, not a hand-written second copy.

---

### ADR-035 — Absent data is zero, and says so *(Phase 5)*

**Decision.** Three inputs the design calls for have no table to read from, and each returns a documented neutral value rather than an estimate: `supportDutyHours` is zero (no on-call roster table exists), `leaveHours` is zero (no leave/availability table exists, so the `UNAVAILABLE` eligibility filter also never fires), and `reopenedSimilar` is zero (reopen tracking arrives with the Phase 9 reporting views).

**Why.** The alternative was to invent proxies — infer leave from a calendar integration that does not exist, infer reopens from status-transition history that is not yet queryable. A scoring input derived from a guess is worse than an absent one, because it looks authoritative in the score breakdown the reviewer reads.

Each is marked at its call site with the reason and the phase that will supply it, so the gap is discoverable in the code rather than only in this document.

**Cost.** `deadlineScore` slightly overestimates availability for someone on leave, and `qualityFactor` is uniformly 1.0, making `experienceScore` purely a volume measure for now. Both degrade gracefully: they make the score less discriminating, not wrong.

---

### ADR-036 — `domain` packages exposed as named interfaces, entities blocked by ArchUnit *(Phase 5, refines ADR-028)*

**Decision.** `identity.domain`, `workitem.domain`, `workflow.domain` and `process.domain` are declared `@NamedInterface("domain")`. A new ArchUnit rule, `modulesMustNotDependOnAnotherModulesEntities`, fails the build on any cross-module reference to an `@Entity` type.

**Why.** `Priority`, `RiskLevel`, `WorkItemType`, `EmployeeLevel` and `GuardContext` are the platform's shared vocabulary — the scorer, the templates, the AI DTOs and the workflow guards all speak in them. The alternatives were to duplicate each enum per module (four copies of `Priority` kept in sync by convention, which is precisely the drift a modular monolith exists to prevent) or to move them all into `common` (a large refactor of Phase 2 and 3 code, and it would make `common` the dumping ground for anything two modules share).

Exposing the package makes the enums reachable and also makes the entities reachable. Modulith controls *which packages are visible*; the new ArchUnit rule controls *what may be taken from them*. Splitting the concern across the two tools keeps the ADR-028 guarantee — cross-module references are UUIDs — as an enforced rule rather than a convention.

**Also fixes a latent Phase 3 defect.** `workitem` has imported `workflow.domain.GuardContext` since Phase 3 while declaring only `workflow::application`. That is a `ModularityTest` failure that had never run, because this environment has no Maven. It was found in Phase 5 by a static check written to substitute for the compiler.

**Cost.** Two mechanisms to understand instead of one, and a developer reading only the `package-info` could conclude entities are fair game. Mitigated by saying so explicitly in each exposed package's Javadoc.

---

### ADR-033 — Analysis-level vocabulary enforcement extends beyond the decomposition set *(Phase 4, extends 07-ai-service-contract §6)*

**Decision.** `AnalysisResponse.departments[].department_code` and `.project_code` are checked against `org_context` in `analyzer._validate_analysis_vocabulary`, in addition to the decomposition-set checks contract §6 specifies (which only cover `SuggestedWorkItem`).

**Why.** The contract's vocabulary-enforcement table is written under "Additional structural checks on a decomposition set," but the same failure mode — the model inventing a department that does not exist in this organization — is just as possible on the analysis response itself, and it feeds directly into `WorkItemService` routing in the backend. Leaving it unchecked at the analysis stage would mean the first line of defence against a hallucinated department is whatever the backend does with an unrecognized code, which is undefined behaviour today.

**Cost.** None beyond the check itself; it reuses the same `org_context` already passed to build the prompt vocabulary.
