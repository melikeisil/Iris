import { vi } from 'vitest';
import {
  emptyPage,
  employeeUser,
  makeAdditionalInformationResponse,
  makeRecommendation,
  makeTokenResponse,
  makeTransitionResult,
  makeWeeklyReport,
  makeWeeklyReportHistory,
  makeWorkItemDetail,
} from './fixtures';

/**
 * A stand-in for `src/api/client/endpoints`.
 *
 * MSW is not available here, so the seam every test mocks is the endpoint module: it is
 * the single place the app's transport is described, and mocking it keeps assertions on
 * the DTOs the backend actually receives (`api.workItems.create` was called with
 * `workItemType`, `summary`, ...) instead of on URLs and JSON strings.
 *
 * Every function has a benign default so a page under test does not explode on the six
 * endpoints it happens to call but the test does not care about. React Query rejects an
 * `undefined` query result, so no default is left unset.
 *
 * Use it as:
 *
 *   vi.mock('@/api/client/endpoints', async () => {
 *     const { createApiMock } = await import('@/test/apiMock');
 *     return { api: createApiMock() };
 *   });
 *
 * then reach for the mocks through `vi.mocked(api.x.y)` on the normal import, which
 * keeps the real signatures and therefore type-checks the fixtures.
 */
export const createApiMock = () => ({
  auth: {
    login: vi.fn().mockResolvedValue(makeTokenResponse()),
    logout: vi.fn().mockResolvedValue(undefined),
    me: vi.fn().mockResolvedValue(employeeUser()),
  },

  jira: {
    status: vi.fn().mockResolvedValue({
      configured: true,
      baseUrl: 'https://example.atlassian.net',
      projectKey: 'IRIS',
      issueType: 'Task',
    }),
  },

  workItems: {
    create: vi.fn().mockResolvedValue(makeWorkItemDetail()),
    search: vi.fn().mockResolvedValue(emptyPage()),
    get: vi.fn().mockResolvedValue(makeWorkItemDetail()),
    update: vi.fn().mockResolvedValue(makeWorkItemDetail()),
    mine: vi.fn().mockResolvedValue([]),
    createdByMe: vi.fn().mockResolvedValue(emptyPage()),
    watching: vi.fn().mockResolvedValue(emptyPage()),
    children: vi.fn().mockResolvedValue({
      children: [],
      progressPercent: null,
      progressStrategy: null,
      hasBlockedChildren: false,
    }),
    possibleDuplicates: vi.fn().mockResolvedValue([]),
    transition: vi.fn().mockResolvedValue(makeTransitionResult()),
    block: vi.fn().mockResolvedValue(makeTransitionResult()),
    setRemainingHours: vi.fn().mockResolvedValue(undefined),
    comments: vi.fn().mockResolvedValue([]),
    addComment: vi.fn().mockResolvedValue(undefined),
    watch: vi.fn().mockResolvedValue(undefined),
    unwatch: vi.fn().mockResolvedValue(undefined),
    links: vi.fn().mockResolvedValue({ outgoing: [], incoming: [] }),
    addLink: vi.fn().mockResolvedValue({ outgoing: [], incoming: [] }),
    removeLink: vi.fn().mockResolvedValue(undefined),
    createInJira: vi.fn().mockResolvedValue({
      issueKey: 'IRIS-123',
      issueUrl: 'https://example.atlassian.net/browse/IRIS-123',
      createdAt: '2026-01-05T10:00:00Z',
    }),
  },

  analysis: {
    analyze: vi.fn().mockResolvedValue(makeRecommendation()),
    history: vi.fn().mockResolvedValue([]),
    proposePlan: vi.fn().mockResolvedValue({
      source: 'TEMPLATE',
      recommendationId: null,
      matchedTemplateCode: null,
      suggestedItems: [],
      warnings: [],
      needsProcessOwnerReview: false,
    }),
    applyPlan: vi.fn().mockResolvedValue({
      processDefinitionId: null,
      created: [],
      dependencyLinksCreated: 0,
      processOwnerId: null,
    }),
    recommendation: vi.fn().mockResolvedValue(makeRecommendation()),
    provider: vi.fn().mockResolvedValue({ provider: 'mock', mock: true }),
    submitAdditionalInformation: vi.fn().mockResolvedValue(makeAdditionalInformationResponse()),
  },

  routing: {
    route: vi.fn().mockResolvedValue({
      workItemId: 'wi-1',
      decision: {
        route: 'AUTO_ASSIGN',
        reasons: [],
        explanation: null,
        suggestedSlaMinutes: null,
        policyId: null,
        policyVersion: null,
        engineVersion: 1,
      },
      assignedWithoutHuman: true,
      assigneeId: null,
      assigneeName: null,
      approvalRequest: null,
      candidatePoolTier: null,
      candidates: [],
      excludedCandidates: [],
    }),
  },

  approvals: {
    pending: vi.fn().mockResolvedValue(emptyPage()),
    overdue: vi.fn().mockResolvedValue([]),
    teamLeadQueue: vi.fn().mockResolvedValue([]),
    processOwnerQueue: vi.fn().mockResolvedValue([]),
    managerEscalations: vi.fn().mockResolvedValue([]),
    approve: vi.fn().mockResolvedValue(undefined),
    reject: vi.fn().mockResolvedValue(undefined),
    escalate: vi.fn().mockResolvedValue(undefined),
  },

  reports: {
    teamWorkload: vi.fn().mockResolvedValue({
      teamId: 'team-1',
      teamName: 'Platform',
      members: [],
      plannedRatio: 0.68,
      unplannedRatio: 0.32,
    }),
    departmentCapacity: vi.fn().mockResolvedValue({
      departmentId: 'dept-1',
      departmentName: 'Engineering',
      totalCapacityHours: 160,
      totalAssignedHours: 116,
      utilizationRatio: 0.73,
      teams: [],
    }),
    aiAnalytics: vi.fn().mockResolvedValue({
      aiAcceptanceRate: 0.78,
      aiOverrideRate: 0.16,
      autoAssignedShare: 0.41,
      averageDecisionLatencyMinutes: 47,
      plannedVsUnplanned: { planned: 63, unplanned: 37 },
      policyExceptionsLast30Days: 9,
    }),
  },

  weeklyReport: {
    generate: vi.fn().mockResolvedValue(makeWeeklyReport()),
    preview: vi.fn().mockResolvedValue(makeWeeklyReport()),
    send: vi.fn().mockResolvedValue(makeWeeklyReport({ status: 'SENDING', emailStatus: 'PENDING' })),
    history: vi.fn().mockResolvedValue(makeWeeklyReportHistory()),
  },
});
