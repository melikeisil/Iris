import type { WorkItemSearchParams } from './client/endpoints';

/**
 * Stable, hierarchical query keys. Invalidation targets a prefix, so
 * `queryClient.invalidateQueries({ queryKey: queryKeys.workItems.all })` catches
 * every list and detail without anybody having to remember them all.
 */
export const queryKeys = {
  currentUser: ['currentUser'] as const,

  workItems: {
    all: ['workItems'] as const,
    search: (params: WorkItemSearchParams) => ['workItems', 'search', params] as const,
    mine: () => ['workItems', 'mine'] as const,
    createdByMe: (page: number) => ['workItems', 'createdByMe', page] as const,
    watching: (page: number) => ['workItems', 'watching', page] as const,
    detail: (id: string) => ['workItems', 'detail', id] as const,
    children: (id: string) => ['workItems', 'detail', id, 'children'] as const,
    links: (id: string) => ['workItems', 'detail', id, 'links'] as const,
    comments: (id: string) => ['workItems', 'detail', id, 'comments'] as const,
    duplicates: (id: string) => ['workItems', 'detail', id, 'duplicates'] as const,
  },

  analysis: {
    all: ['analysis'] as const,
    history: (workItemId: string) => ['analysis', 'history', workItemId] as const,
    recommendation: (id: string) => ['analysis', 'recommendation', id] as const,
    provider: ['analysis', 'provider'] as const,
  },

  approvals: {
    all: ['approvals'] as const,
    pending: (page: number) => ['approvals', 'pending', page] as const,
    overdue: () => ['approvals', 'overdue'] as const,
    teamLeadQueue: () => ['approvals', 'teamLeadQueue'] as const,
    processOwnerQueue: () => ['approvals', 'processOwnerQueue'] as const,
    managerEscalations: () => ['approvals', 'managerEscalations'] as const,
  },

  routing: {
    outcome: (workItemId: string) => ['routing', workItemId] as const,
  },

  reports: {
    all: ['reports'] as const,
    teamWorkload: (teamId: string | null) => ['reports', 'teamWorkload', teamId] as const,
    departmentCapacity: (departmentId: string | null) =>
      ['reports', 'departmentCapacity', departmentId] as const,
    aiAnalytics: () => ['reports', 'aiAnalytics'] as const,
  },

  weeklyReport: {
    all: ['weeklyReport'] as const,
    preview: (id: string) => ['weeklyReport', 'preview', id] as const,
    history: (page: number) => ['weeklyReport', 'history', page] as const,
  },

  admin: {
    all: ['admin'] as const,
    users: (params: Record<string, unknown>) => ['admin', 'users', params] as const,
    user: (id: string) => ['admin', 'users', id] as const,
    userSkills: (id: string) => ['admin', 'users', id, 'skills'] as const,
    departments: () => ['admin', 'departments'] as const,
    teams: () => ['admin', 'teams'] as const,
    teamMembers: (id: string) => ['admin', 'teams', id, 'members'] as const,
    projects: () => ['admin', 'projects'] as const,
    skills: () => ['admin', 'skills'] as const,
  },

  /** Demo-mode adapters. Namespaced so they are obvious in the devtools. */
  demo: {
    all: ['demo'] as const,
    teamWorkload: (teamId: string | null) => ['demo', 'teamWorkload', teamId] as const,
    managerAnalytics: () => ['demo', 'managerAnalytics'] as const,
    notifications: () => ['demo', 'notifications'] as const,
  },
} as const;
