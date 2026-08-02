# API gaps

Scope: the seven routes this release ships - `/login`, `/dashboard`, `/requests/new`,
`/my-work`, `/work-items/:workItemId`, `/ai-review`, `/manager`. Screens that used to
consume other endpoints (admin, sprint board, onboarding, notifications page, process
owner, Jira config, policy config, audit log) have been deleted, and so have their
fixtures.

Every capability the frontend renders falls into exactly one of the sections below.
Nothing in section 1 is mocked; nothing outside section 1 is presented as live.

Audited against `backend/src/main/java/com/smartwork/**`.

---

## 1. Real API, used for real (default in both modes)

| Capability | Endpoint | Used by |
| --- | --- | --- |
| Sign in | `POST /auth/login` | `/login` |
| Token rotation | `POST /auth/refresh` | `api/client/http.ts` (single-flight) |
| Sign out | `POST /auth/logout` | `AuthProvider.logout` |
| Current principal, roles, capabilities | `GET /auth/me` | `AuthProvider`, navigation, route guards |
| Create work item | `POST /work-items` | `/requests/new` |
| Search work items (incl. `blocked`, `dueBefore` filters) | `GET /work-items` | `/my-work`, `/manager`, dashboard counts and the open/completed split |
| Assigned to me | `GET /work-items/my` | `/dashboard`, `/my-work` |
| Reported by me | `GET /work-items/created-by-me` | `/dashboard`, `/my-work` |
| Watched | `GET /work-items/watching` | `/dashboard`, `/my-work` |
| Work item detail, permissions, transitions | `GET /work-items/{id}` | `/work-items/:id`, `/ai-review`, manager drawer |
| Children and roll-up | `GET /work-items/{id}/children` | detail |
| Link graph | `GET /work-items/{id}/links` | detail, dependencies |
| Comments | `GET/POST /work-items/{id}/comments` | detail activity, "request more information" |
| Recommendation history | `GET /work-items/{id}/recommendations` | detail AI tab, `/ai-review` |
| Named workflow transition | `POST /work-items/{id}/transition` | detail, `WorkflowActions` |
| Block with reason | `POST /work-items/{id}/block` | block dialog |
| Watch / unwatch | `POST /work-items/{id}/watch` | detail header |
| Analyse | `POST /work-items/{id}/analyze` | `/requests/new`, detail |
| Route and rank | `POST /work-items/{id}/route` | `useRouteWorkItem`; its cached response is the **real** source of the `/ai-review` candidate ranking |
| Review queues | `GET /team-lead/review-queue`, `GET /approvals/pending`, `GET /approvals/overdue`, `GET /manager/escalations` | `/dashboard`, `/ai-review`, `/manager` |
| Approve / reject / escalate | `POST /approvals/{id}/approve\|reject\|escalate` | `/ai-review`, `/manager` |

---

## 2. Demo fixtures currently in use (no endpoint exists)

Four capabilities, all routed through `src/demo/repository.ts`, all gated by
`VITE_DEMO_MODE`, all badged with `<DemoDataBadge />`. The per-widget table - route,
component, fixture, and demo-off behaviour - lives in `docs/demo-mode.md`.

| Area | Gap | Adapter |
| --- | --- | --- |
| Notifications | No notification module at all: no endpoint, no table. | `demoRepository.notifications` |
| **Assignment candidate ranking** | Computed and persisted by `ScoringEngine` / `RecommendationCandidateRepository`, but readable only as the response of the **side-effecting** `POST /work-items/{id}/route`. A reviewer opening their queue cannot fetch the ranking that already exists. | `useAssignmentCandidates` - uses the cached real routing outcome when present, otherwise a badged fixture, and in that case **does not render the assignee "Select" control**, because approving would post a fabricated user id. |

With `VITE_DEMO_MODE=false` each adapter throws `DemoUnavailableError` and the widget
renders a named "not available" state. Nothing silently shows zeros.

---

## 3. Presentation heuristics over real data (documented, not mocked)

| Derivation | Basis | Where |
| --- | --- | --- |
| "Needs information" | Reported items whose backend workflow status matches `/INFO\|CLARIF\|WAITING\|PENDING_REQUESTER/` (`NEEDS_INFO` in `features/work-items/viewModel.ts`). No endpoint lists clarification requests; the status is the signal the backend already publishes. | `/dashboard` |
| "Requester verification" | Reported items in the `REVIEW` status category, which is where the SERVICE workflow parks work awaiting sign-off. | `/dashboard` |
| Manager open / completed split | `GET /work-items` has no status-category filter and there is no aggregate endpoint, so the split is counted client-side over the 200 most recently updated items (`MANAGER_SPLIT_SIZE`). The sample size is printed on the card. | `/dashboard` (manager), `/manager` |
| Ticket `affectedSystem` / `businessImpact` | No columns exist. Folded into the description as a labelled block so the AI service still sees them; `businessImpact` also seeds `priority`. | `/requests/new` |

---

## 4. Missing endpoints, as future work

These are genuine backend gaps. **None of them is faked in the current app** - the
screens that would have consumed them were removed from scope, so today they cost
nothing but future capability. Listed in priority order.

1. `GET /work-items/{id}/assignment-candidates` - read the stored ranking without side
   effects. The single highest-value gap: it is the only reason the flagship review
   screen ever falls back to a fixture.
2. A notification module (table, endpoint, read/unread). Would retire the last generic fixture.
3. `GET /work-items/{id}/history` - the transition trail. Today only comments and the
   current version are readable, so the Activity tab is thinner than the design.
4. `GET /reference/projects`, `GET /reference/teams` - non-admin, caller-scoped
   reference data, so an employee can populate a project dropdown without `/admin/*`.
5. `GET /me/owned-resources` - consolidated ownership, so navigation need not infer
   ownership from a queue being non-empty.
6. Attachment storage (`POST /work-items/{id}/attachments`).
7. Out-of-scope-today, still absent from the backend, and only worth building alongside
   the screens that need them: Jira integration (Phase 8 - connection, OAuth, project
   mappings, sync, webhooks), a sprint module, admin CRUD for assignment and
   employee-level policies, workflow-definition and process-template read endpoints, an
   audit-log query endpoint, and `PUT /me/preferences` for onboarding answers.
