/**
 * Every route in one place. Components link through these, never string literals.
 *
 * The MVP is exactly nine routes. Anything not listed here is out of scope and must
 * not be reachable; see frontend/README.md, "Scope".
 */
export const paths = {
  login: '/login',

  dashboard: '/dashboard',
  newRequest: '/requests/new',
  myWork: '/my-work',
  aiReview: '/ai-review',
  manager: '/manager',
  help: '/help',
  settings: '/settings',

  workItem: (id: string) => `/work-items/${id}`,
  workItemPattern: '/work-items/:workItemId',

  forbidden: '/403',
  notFound: '/404',
} as const;

export type Paths = typeof paths;
