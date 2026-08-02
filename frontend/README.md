# Iris frontend

The React and TypeScript client for the SmartWork AI / Iris orchestration platform.
It talks only to the Spring Boot backend on `/api/v1`; it never calls the FastAPI AI
service directly.

## Running it

```bash
cp .env.example .env.development   # already present in the repo
npm install
npm run dev                        # http://localhost:5173, proxies /api to :8080
```

With the whole stack:

```bash
docker compose up --build          # frontend on http://localhost:5173
```

Demo credentials come from the backend's `demo` profile: any seeded account, e.g.
`ayse.demir@acme.io` (team lead), `elif.yilmaz@acme.io` (manager),
`admin@acme.io` (administrator), `mert.kaya@acme.io` (employee), with the password from
`DEMO_PASSWORD` (default `SmartWork!Demo2026`).

## Scripts

| Command | What it does |
| --- | --- |
| `npm run dev` | Vite dev server with an `/api` proxy |
| `npm run build` | `tsc -b` then a production bundle |
| `npm run preview` | Serves the built bundle on :4173 |
| `npm test` | Vitest + React Testing Library (see "Tests" below) |
| `npm run test:watch` | The same, in watch mode |

## Architecture

```
src/
  api/          one HTTP client, typed endpoints, RFC 7807 error mapping, query keys
  app/          App, router, providers (query, auth, toast), guards, layouts
  components/   the application shell
  config/       brand, environment, navigation - the three files you edit to rebrand
  demo/         typed fixtures for capabilities with no backend endpoint
  design-system/ tokens, styles and every reusable component
  features/     one folder per screen: auth, dashboard, tickets, my-work, work-items,
                team-lead, manager, misc
  hooks/ lib/   small shared utilities
  test/         test helpers
```

### Scope

The MVP is exactly nine routes, defined in `src/app/router/paths.ts`:

| Route | Screen |
| --- | --- |
| `/login` | Sign in |
| `/dashboard` | Role-aware dashboard (employee / team lead / manager widgets) |
| `/requests/new` | Create a request |
| `/my-work` | My Work, with tabs for reviews, raised-by-me and watching |
| `/work-items/:id` | Work item detail |
| `/ai-review` | AI review queue (team lead and manager) |
| `/manager` | Manager overview: decisions, delivery, risks, AI analytics |
| `/help` | Lightweight help center with search, FAQs and support guidance |
| `/settings` | Profile summary, local preferences and session controls |

Admin, onboarding, sprint, notifications, process-owner and the separate manager
sub-pages are **not** part of this release and are not reachable. Their old frontend
files were removed from `src/`; the reachable app surface is the nine routes above.

Rules the codebase keeps to:

* **One API client.** Nothing outside `api/client/http.ts` calls `fetch`.
* **Server state is TanStack Query only.** No global store mirrors it.
* **No business rules in React.** Statuses, transitions, scores and policy decisions are
  read from the backend and formatted, never recomputed. `WorkItemDetail.permissions` and
  `availableTransitions` decide which controls exist.
* **Tokens, not hex.** Every colour is a CSS custom property in
  `src/design-system/styles/tokens.css`; `tailwind.config.ts` maps them. No component
  contains a raw hex value. The values are transcribed from the `tailwind.config`
  `<script>` block inside the Stitch `code.html` sources, which is the operative
  configuration - `iris_enterprise/DESIGN.md` frontmatter disagrees with it and is not
  used for colour.
* **No CDNs.** No `cdn.tailwindcss.com`, no Google Fonts `<link>`, no Material Symbols
  webfont, no remote avatar or logo URLs. Tailwind is a real dependency, icons are
  `lucide-react`, the logo is `public/iris-logo.png` and avatars are initials.
* **Hiding a control is not security.** The backend re-authorises every request.

## Authentication

Short-lived access token plus a single-use rotating refresh token, matching
`AuthService`/`RefreshTokenService`. On a 401 the client performs one single-flight
refresh and replays the request; concurrent requests await the same rotation, because
presenting an already-consumed refresh token revokes the whole family server-side. Tokens
are never logged. A failed refresh raises the session-expired dialog rather than dumping
the user on a login page mid-task.

## Typography

The design calls for Inter (UI), IBM Plex Mono (technical metadata) and Cormorant Garamond
(selective editorial headings). Fonts are **self-hosted, not CDN-loaded**: a public
`<link>` fails silently offline and leaks users to a third party.

To enable them, drop these into `public/fonts/`:

```
inter-400.woff2 inter-500.woff2 inter-600.woff2 inter-700.woff2
ibm-plex-mono-400.woff2 ibm-plex-mono-500.woff2
cormorant-garamond-500.woff2 cormorant-garamond-600.woff2
```

`src/design-system/styles/fonts.css` does **not** declare `@font-face` rules right now.
It documents the fallback stacks instead, so the app uses installed local fonts when
available and otherwise renders with the documented system stacks (`system-ui`,
`ui-monospace`, `Georgia`, etc.) without broken font requests.

## Demo mode

`VITE_DEMO_MODE` only affects capabilities with **no backend endpoint at all**. In this
release that is: team workload and overloaded employees, manager capacity / AI analytics
/ planned-vs-unplanned, notifications, and the assignment-candidate ranking when no
routing outcome has been cached. Every one of those surfaces carries a visible
"Demo data" badge, and no control on a fixture-backed surface posts a fabricated id to
the backend. That includes the notification bell, whose menu header carries the badge.
With `VITE_DEMO_MODE=false` each of those widgets renders a named "not available" state
rather than an empty or endlessly loading one.

`src/demo` holds fixtures for those four capabilities and nothing else - fixtures for
the out-of-scope screens have been deleted.

Everything else - authentication, work items, comments, links, transitions, blocking,
watching, AI analysis and every approval decision - is a real endpoint in both modes.
See `docs/demo-mode.md` and `docs/phase7-api-gaps.md`.

## Tests

`npm test` runs Vitest: 13 spec files, 68 cases, under `__tests__` directories beside
the code they cover. Tests use `src/test/fixtures.ts` and `src/test/apiMock.ts`, which
are test-only and deliberately separate from `src/demo` (demo fixtures are production
code that ships to the browser; test fixtures are not). MSW is not used - the API client
is stubbed directly.

## Documentation

* `docs/design-source-map.md` - Stitch screen to route to component, with deviations
* `docs/route-role-matrix.md` - route, capability, resource permission, data source
* `docs/phase7-api-gaps.md` - what is real, what is missing, what to build next
* `docs/demo-mode.md` - how the fixture seam works

## Accessibility

Semantic landmarks, a skip link, visible focus rings everywhere, keyboard-operable menus
and tabs (arrow-key roving), dialogs with focus trapping and Escape, `aria-describedby`
on every field error, non-colour indicators on every status/priority/risk/confidence
badge, screen-reader labels on all icon-only controls, and a `prefers-reduced-motion`
block that neutralises animation.

## Responsiveness

Desktop >=1440px full grid; laptop 1024-1439px auto-collapses the sidebar; tablet
768-1023px stacks the dashboard; below 768px the sidebar becomes an overlay drawer, tables
render as stacked cards and forms go single-column. Board columns scroll horizontally
rather than squeezing.
