# 15 — Demo Runbook

This is the presenter-facing Phase 10 script for a clean local demo.

## 1. Bring the stack up

```bash
cp .env.example .env
docker compose up --build -d
make demo
```

`make demo` runs the API smoke harness in [scripts/demo_smoke.sh](../scripts/demo_smoke.sh). It verifies:

- backend health
- AI readiness and active provider
- login with seeded accounts
- create → analyze → route for three demo scenarios
- current Jira readiness response

## 2. Accounts to keep ready

All seeded accounts use `DEMO_PASSWORD` from `.env`:

| Person | Email | Why to use it |
|---|---|---|
| Employee | `mert.kaya@acme.io` | requester flow |
| Team Lead | `ayse.demir@acme.io` | department review and candidate ranking |
| Manager | `elif.yilmaz@acme.io` | manager-only screens and escalations |
| Admin | `admin@acme.io` | configuration surfaces only |

## 3. Safest live sequence

### Scenario A — AI catches incomplete onboarding input

Login as `mert.kaya@acme.io`.

Create:

- Title: `New employee onboarding`
- Description: `A new software developer will start on Monday. Prepare all required onboarding activities.`

Expected talking points:

- AI analysis runs
- the system identifies missing information
- routing explains why more information is needed

This is the safest “AI quality gate” story because it does not depend on a perfect downstream planning flow.

### Scenario B — richer development work for ranking and review

Login as `ayse.demir@acme.io`.

Create:

- Title: `Profile photo upload`
- Description: `Users must be able to upload a profile photo. Support JPG and PNG up to 5 MB, store originals in object storage, generate thumbnails, and preserve existing avatars during migration.`

Expected talking points:

- AI classification and confidence
- candidate ranking and score explainability
- policy routing outcome

### Scenario C — manager-facing sensitive work

Login as `elif.yilmaz@acme.io`.

Create:

- Title: `Payments fail intermittently during checkout`
- Description: `Production customers intermittently fail checkout in the payments flow. Investigate root cause, ship a fix, and verify the incident is resolved.`

Expected talking points:

- production-sensitive classification
- stricter routing than routine work
- manager overview and escalations context

## 4. Jira expectations

Right now the repository supports two honest demo modes:

### Mode 1 — No real Jira connection configured

This is the default state when the Jira variables in `.env` are left empty.

Use this line in the presentation:

> “The product is fully operable without a Jira connection; Jira integration is additive and separately health-checked.”

### Mode 2 — Real Jira demo

Before the presentation, fill:

```env
JIRA_BASE_URL=
JIRA_EMAIL=
JIRA_API_TOKEN=
JIRA_PROJECT_KEY=
JIRA_ISSUE_TYPE=Task
```

Then rebuild:

```bash
docker compose up -d --build backend frontend
```

Only after that should you demo `Create in Jira` from Work Item Detail.

## 5. Known live caveats

- If `GET /api/v1/jira/status` fails, the running backend container is stale relative to source or the build is outdated.
- A TEAM_LEAD may still get `403` on `jira-create` when the item is outside their actual scope, even if the role is present.
- The frontend health check may show unhealthy while the UI is still usable; trust a direct page load plus `make demo` more than the container health flag alone.

## 6. Presenter fallback lines

Use these exact fallbacks if needed:

- “This scenario is using the real Gemini provider.”
- “Jira is currently running in disconnected mode, so the platform shows readiness without leaking or requiring credentials.”
- “Policy decisions are deterministic in the backend; the LLM proposes, but it does not authorize.”
