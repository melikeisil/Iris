# Route, role and data-source matrix

Capabilities come from `GET /auth/me`. Hiding a route is presentation, not security -
the backend re-authorises every request.

| Route | Visible to | Guard | Data source |
| --- | --- | --- | --- |
| `/login` | anonymous | none | `POST /auth/login` |
| `/dashboard` | any signed-in user | `RequireAuth` | `GET /work-items/my`, `/created-by-me`, `/watching`, `/approvals/pending`; team lead adds `/team-lead/review-queue` + `/approvals/overdue`; manager adds `/manager/escalations` + `GET /work-items` counts. Team workload panel is **demo data**. |
| `/requests/new` | `work_item.create` (all roles hold it) | `RequireAuth` | `POST /work-items`, `POST /work-items/{id}/analyze` |
| `/my-work` | any signed-in user | `RequireAuth` | `GET /work-items/my`, `/created-by-me`, `/watching` |
| `/work-items/:id` | anyone the backend lets read the item | `RequireAuth` + per-item `permissions` | `GET /work-items/{id}`, `/children`, `/links`, `/comments`, `/recommendations`; `POST /transition`, `/block`, `/watch`, `/comments`, `/analyze` |
| `/ai-review` | `team.review_queue` or `manager.dashboard` | `RequireCapability` | `GET /team-lead/review-queue`; `POST /approvals/{id}/approve\|reject\|escalate`. Candidate ranking is real only when a routing outcome is cached, otherwise **demo data**. |
| `/manager` | `manager.dashboard` or `manager.escalations` | `RequireCapability` | `GET /manager/escalations`, `/approvals/overdue`, `GET /work-items` counts; `POST /approvals/{id}/approve\|reject`. Delivery and AI-analytics panels are **demo data**. |

## Navigation by role

| Role | Nav items |
| --- | --- |
| EMPLOYEE | Dashboard, Create Request, My Work |
| TEAM_LEAD | Dashboard, Create Request, AI Review, My Work |
| MANAGER | Dashboard, Create Request, AI Review, My Work, Manager Overview |
| ADMIN | Same as EMPLOYEE. There are no admin screens in this release. |

A user may hold several roles; every check is a set-membership test, never
`roles[0]`.

`PROCESS_OWNER` is **not** a system role in this backend - it is a resource-scoped
relationship (`ResourceRole`), so it grants no navigation and no route.
