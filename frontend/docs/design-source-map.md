# Design source map

Which Google Stitch screen each route was converted from, and every place the
implementation deliberately differs from the mockup.

The Iris set (`stitch_iris_visual_design_foundation/`) is the visual source of truth.
The Lumina set (`stitch_smartwork_ai_orchestration_platform/`) is **content reference
only** - it is light-themed and its layouts are not used.

## Tokens

Colour, radius and font tokens come from the `tailwind.config` `<script>` block embedded
in each Iris `code.html`, transcribed into `src/design-system/styles/tokens.css` and
mapped in `tailwind.config.ts`. `iris_enterprise/DESIGN.md` frontmatter disagrees with
that block on `background` (`#111417` vs the operative `#07090C`) and on
`surface-container-lowest` (`#0c0e11` vs `#0C0F14`); the `code.html` values win.

Type sizes are the one exception: the Stitch config declares `display-lg`, `body-sm`
etc. only as font *families*, so `text-display-lg` was inert in the source markup. The
sizes in `tailwind.config.ts` come from the `typography` block of `DESIGN.md`, which is
the only place they are specified.

## Route map

| Route | Iris source | Notes |
| --- | --- | --- |
| shell | `iris_application_shell_employee_expanded`, `_collapsed`, `_multi_role_user`, `iris_shell_components_states` | One shell; role changes the nav data only |
| `/login` | none | New composition from Iris tokens and existing primitives |
| `/dashboard` | `iris_employee_dashboard_default` (+ `_empty`, `_loading`, `_1024px_preview`) | Fixed layout, role-dependent widget content |
| `/requests/new` | `iris_create_ticket` (+ one field from `iris_create_work_item`) | |
| `/my-work` | `iris_my_work` | |
| `/work-items/:id` | `iris_work_item_detail` | |
| `/ai-review` | `iris_team_lead_review` | |
| `/manager` | `iris_manager_overview` | |

## Deviations, and why

**Shell**

* The Stitch sidebar lists My Tickets, Current Sprint, Clarifications, Owned Processes,
  Watching, Reviews and My Reports. None of those are MVP routes. Nav is now
  Dashboard / Create Request / AI Review / My Work / Manager Overview, filtered by
  capability then role.
* The mockups render the logo and every avatar from `lh3.googleusercontent.com`. The
  logo is a local `public/iris-logo.png`; avatars are initials.
* Material Symbols is replaced by `lucide-react`. Glyph shapes therefore differ slightly
  (`qr_code_2` for the mark, `pace`, `settings_suggest`, `person_apron` have no exact
  lucide equivalent).
* The centre search box is real but scoped: it navigates to My Work with a query, because
  there is no global search endpoint. The `⌘K` hint is replaced by `/`, since the app no
  longer opens a global search dialog.

**Dashboard**

* The source's "Current Sprint" card (sprint name, points, days left, capacity) is
  replaced by a "Your queue" summary for employees and by the team workload panel for
  leads and managers. There is no sprint module in the backend at all, so the original
  card could only have been fabricated.
* The "Recent activity" column in the mockup shows git pushes and pull-request approvals.
  There is no activity endpoint; the panel shows the caller's most recently updated work
  items in the same treatment.
* The mockup's "Review requested / pull requests" metric is replaced by role-appropriate
  metrics, since there is no code-review integration.

**Create request**

* The five-step stepper loses "Clarification" and "Generated plan". There is no endpoint
  that returns clarification questions or accepts answers, and a step that can never
  advance would misrepresent the workflow. Three steps remain: Details, AI analysis,
  Submitted.
* The attachments drop zone is omitted - there is no attachment endpoint.
* "Affected system" and "Business impact" have no backend columns; they are folded into
  the description as a labelled block (see `ticketSchema.ts`) rather than silently
  dropped.

**Work item detail**

* The "Tree complexity" sparkline is dropped: no metric exists behind it.
* The three "system states" demo cards (loading / access restricted / stale version) at
  the bottom of the mockup are a component showcase, not page content, and are handled by
  the real loading, permission and 409 paths instead.
* Assignee is shown as a short id where the backend returns only `assigneeId`; there is
  no user-lookup endpoint a plain employee may call.

**AI review**

* The editable "Decomposition review" list is not implemented. The only plan operation
  the backend exposes is `POST /work-items/{id}/apply-plan` with a template id; nothing
  persists edited per-task estimates.
* The candidate rail's "Select" control is rendered **only** when the ranking came from a
  real cached routing outcome. Against fixture candidates it is omitted, so no fabricated
  user id can be posted.

**Manager overview**

* The four summary cards are re-labelled to metrics the backend can actually answer
  (decisions required, overdue approvals, blocked work, overdue work) rather than the
  mockup's "critical risks" and "cross-team blockers", which have no endpoint.
* "Cross-team delivery" and the risk/AI trend panels are fixtures and carry a
  "Demo data" badge.

**Global**

* Priority, risk and status badges carry an icon as well as a colour. The mockup uses
  colour-only tags; colour alone is not an accessible indicator for exactly these fields.
* The mockups' `webgl-shader` background layers and per-button hover translate scripts
  are not reproduced.
