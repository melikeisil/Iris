# Demo mode

`VITE_DEMO_MODE` (default `true` in development) only affects capabilities for which the
Spring Boot backend exposes **no endpoint at all**. Every real endpoint is called for
real in both modes; there is no `if (demoMode)` branch inside any component.

## The seam

```
component  ->  src/demo/hooks.ts  ->  src/demo/repository.ts  ->  src/demo/fixtures.ts
```

`src/demo` contains exactly two fixture-backed capabilities and nothing else. Fixtures
for screens outside the seven-route scope were deleted; if a fixture is in this folder,
something on screen renders it, behind a badge.

When a real endpoint ships, one function body in `repository.ts` changes from
`fixture(...)` to `api.something(...)`, the fixture is deleted, and the entry is removed
from `phase7-api-gaps.md`. Nothing in the presentation layer moves.

With `VITE_DEMO_MODE=false`, `repository.ts` throws `DemoUnavailableError` instead of
returning a fixture. Every consumer handles that error explicitly (last column below).
Nothing crashes, spins forever, or silently shows zeroes.

## The exact demo-data boundary

| Widget | Route(s) | Component | Fixture / hook | Missing backend endpoint | With `VITE_DEMO_MODE=false` |
| --- | --- | --- | --- | --- | --- |
| Notification bell | app shell, so all six authenticated routes | `components/shell/NotificationMenu.tsx` | `demoNotifications` via `useDemoNotifications` | none - there is no notification module: no endpoint, no table | Menu opens with the badge and one disabled row: "Notifications are not available - this backend has no notification endpoint." The unread count is 0. |
| Assignment candidate ranking | `/ai-review` right rail | `features/team-lead/AiReviewPage.tsx` via `features/team-lead/hooks/useAssignmentCandidates.ts` | `demoAssignmentCandidates`, `demoExcludedCandidates` (imported directly - this one is not a query) | no **read** path. The ranking exists but is returned only by the side-effecting `POST /work-items/{id}/route`. Proposed: `GET /work-items/{id}/assignment-candidates` | `ranking.available` is false and the rail renders the "Ranking unavailable" empty state. This hook prefers the **real** cached routing outcome whenever one exists; the fixture is only a last resort. |

Rules these surfaces follow:

1. Every one renders `<DemoDataBadge />` with a `reason` explaining the gap - including
   the notification menu, whose section header carries the badge.
2. No control on a fixture-backed surface posts a fabricated id. On `/ai-review` the
   candidate "Select" action - which would approve against a user id - is rendered only
   when `ranking.isReal` is true, so a fixture candidate can never be approved.
3. Fixtures never claim an integration is connected.

## What is *not* demo data

Everything else. In particular these are real calls in both modes:

`POST /auth/login`, `/auth/refresh`, `/auth/logout`, `GET /auth/me`;
`POST /work-items`; `GET /work-items` (including the `blocked` and `dueBefore` filters);
`GET /work-items/my`, `/created-by-me`, `/watching`, `/{id}`, `/{id}/children`,
`/{id}/links`, `/{id}/comments`, `/{id}/recommendations`;
`POST /work-items/{id}/transition`, `/{id}/block`, `/{id}/watch`, `/{id}/analyze`;
`GET /team-lead/review-queue`, `/approvals/pending`, `/approvals/overdue`,
`/manager/escalations`; `POST /approvals/{id}/approve|reject|escalate`.

Two figures are derived client-side from **real** responses. They are not demo data, but
they are approximations and are labelled as such on screen:

* **"Needs information"** counts reported items whose backend workflow status matches
  `INFO | CLARIF | WAITING` (`features/work-items/viewModel.ts`, `NEEDS_INFO`). The
  status is the authoritative signal the backend already exposes; no endpoint lists
  clarification requests.
* **Manager open / completed split** is computed over the 200 most recently updated
  items (`MANAGER_SPLIT_SIZE` in `features/dashboard/useDashboard.ts`), because
  `GET /work-items` has no status-category filter and no aggregate endpoint exists. The
  card states the sample size rather than implying an organisation-wide total.
