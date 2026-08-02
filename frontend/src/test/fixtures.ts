import type {
  AdditionalInformationResponse,
  ApprovalRequest,
  Capability,
  CurrentUser,
  PageResponse,
  Recommendation,
  SystemRole,
  TokenResponse,
  TransitionResult,
  WeeklyReport,
  WeeklyReportHistory,
  WorkItemDetail,
  WorkItemSummary,
} from '@/api/types';

/**
 * Typed fixtures for the tests.
 *
 * Every shape here is a real DTO from src/api/types.ts, built through a helper that
 * takes a `Partial` override, so a test states only the fields it actually asserts on
 * and a DTO change breaks compilation rather than producing a silently wrong test.
 *
 * These are test-only. Demo-mode data lives in src/demo and must stay production code.
 */

export const makeUser = (overrides: Partial<CurrentUser> = {}): CurrentUser => ({
  id: 'user-1',
  email: 'ada@novatech.test',
  fullName: 'Ada Lovelace',
  employeeLevel: 'SENIOR',
  organizationId: 'org-1',
  department: { id: 'dept-1', code: 'ENG', name: 'Engineering' },
  teams: [{ id: 'team-1', code: 'PLAT', name: 'Platform', isLead: false }],
  roles: ['EMPLOYEE'],
  organizationWide: false,
  capabilities: ['work_item.create', 'work_item.view_own'],
  ...overrides,
});

const EMPLOYEE_CAPABILITIES: Capability[] = ['work_item.create', 'work_item.view_own'];

const withRole = (
  roles: SystemRole[],
  capabilities: Capability[],
  overrides: Partial<CurrentUser>,
): CurrentUser => makeUser({ roles, capabilities, ...overrides });

export const employeeUser = (overrides: Partial<CurrentUser> = {}): CurrentUser =>
  withRole(['EMPLOYEE'], EMPLOYEE_CAPABILITIES, overrides);

export const teamLeadUser = (overrides: Partial<CurrentUser> = {}): CurrentUser =>
  withRole(
    ['EMPLOYEE', 'TEAM_LEAD'],
    [...EMPLOYEE_CAPABILITIES, 'team.dashboard', 'team.review_queue', 'team.assign'],
    { fullName: 'Lea Demir', ...overrides },
  );

export const managerUser = (overrides: Partial<CurrentUser> = {}): CurrentUser =>
  withRole(
    ['EMPLOYEE', 'MANAGER'],
    [
      ...EMPLOYEE_CAPABILITIES,
      'manager.dashboard',
      'manager.escalations',
      'manager.capacity',
    ],
    { fullName: 'Mira Yilmaz', ...overrides },
  );

export const makeTokenResponse = (overrides: Partial<TokenResponse> = {}): TokenResponse => ({
  accessToken: 'issued-access',
  refreshToken: 'issued-refresh',
  expiresIn: 900,
  tokenType: 'Bearer',
  user: employeeUser(),
  ...overrides,
});

export const makePage = <T>(content: T[]): PageResponse<T> => ({
  content,
  page: { number: 0, size: 25, totalElements: content.length, totalPages: 1 },
  sort: [],
});

export const emptyPage = <T>(): PageResponse<T> => makePage<T>([]);

export const makeWorkItemSummary = (
  overrides: Partial<WorkItemSummary> = {},
): WorkItemSummary => ({
  id: 'wi-1',
  referenceKey: 'TASK-101',
  workItemType: 'TASK',
  workFamily: 'DEVELOPMENT_WORK',
  originType: 'USER_CREATED',
  status: 'IN_PROGRESS',
  statusCategory: 'IN_PROGRESS',
  summary: 'Rotate the signing keys',
  priority: 'MEDIUM',
  riskLevel: null,
  estimatedHours: 4,
  storyPoints: null,
  dueDate: null,
  progressPercent: null,
  assigneeId: 'user-1',
  teamId: 'team-1',
  departmentId: 'dept-1',
  projectId: null,
  sprintId: null,
  parentId: null,
  jiraIssueKey: null,
  blocked: false,
  updatedAt: '2026-01-05T10:00:00Z',
  ...overrides,
});

export const makeWorkItemDetail = (overrides: Partial<WorkItemDetail> = {}): WorkItemDetail => ({
  id: 'wi-1',
  referenceKey: 'TASK-101',
  workItemType: 'TASK',
  workFamily: 'DEVELOPMENT_WORK',
  originType: 'USER_CREATED',
  status: 'IN_PROGRESS',
  statusCategory: 'IN_PROGRESS',
  workflowCode: 'DEV_DEFAULT',
  summary: 'Rotate the signing keys',
  description: 'The current signing keys expire at the end of the quarter.',
  acceptanceCriteria: null,
  priority: 'HIGH',
  priorityScore: null,
  riskLevel: 'MEDIUM',
  complexity: 'MEDIUM',
  estimatedHours: 4,
  remainingHours: 2,
  storyPoints: null,
  dueDate: null,
  progressPercent: null,
  aiConfidence: null,
  sensitivity: null,
  closureType: null,
  projectId: null,
  sprintId: null,
  parent: null,
  departmentId: 'dept-1',
  teamId: 'team-1',
  assigneeId: 'user-1',
  reporterId: 'user-1',
  processOwnerId: null,
  reviewerId: null,
  childrenSummary: null,
  blocked: null,
  jira: null,
  relationships: ['ASSIGNEE'],
  permissions: {
    canEdit: true,
    canAssign: false,
    canComment: true,
    canLink: false,
    canSeeInternalComments: false,
  },
  availableTransitions: [
    {
      code: 'SUBMIT_FOR_REVIEW',
      label: 'Submit for review',
      toState: 'CODE_REVIEW',
      requiresReason: false,
      enabled: true,
      blockedReasons: [],
    },
  ],
  createdAt: '2026-01-01T09:00:00Z',
  updatedAt: '2026-01-05T10:00:00Z',
  version: 7,
  ...overrides,
});

export const makeTransitionResult = (
  overrides: Partial<TransitionResult> = {},
): TransitionResult => ({
  id: 'wi-1',
  referenceKey: 'TASK-101',
  fromState: 'IN_PROGRESS',
  toState: 'CODE_REVIEW',
  statusCategory: 'REVIEW',
  transition: 'SUBMIT_FOR_REVIEW',
  version: 8,
  ...overrides,
});

export const makeApproval = (overrides: Partial<ApprovalRequest> = {}): ApprovalRequest => ({
  id: 'approval-1',
  workItemId: 'wi-1',
  recommendationId: null,
  route: 'TEAM_LEAD_REVIEW',
  status: 'PENDING',
  reasons: [{ code: 'HIGH_RISK', meaning: 'The item was scored as high risk' }],
  assignedToScope: 'TEAM',
  assignedToUserId: null,
  policyException: false,
  dueAt: null,
  overdue: false,
  escalatedFromId: null,
  escalationReason: null,
  decidedBy: null,
  decisionNote: null,
  decidedAt: null,
  createdAt: '2026-01-05T09:00:00Z',
  ...overrides,
});

export const makeRecommendation = (overrides: Partial<Recommendation> = {}): Recommendation => ({
  id: 'rec-1',
  workItemId: 'wi-1',
  type: 'ANALYSIS',
  status: 'COMPLETED',
  confidence: 0.92,
  analysis: null,
  plan: null,
  provenance: null,
  errorDetail: null,
  createdAt: '2026-01-05T09:30:00Z',
  ...overrides,
});

/**
 * An ANALYSIS recommendation that requested more information, shaped exactly like the
 * backend stores it - snake_case keys straight from the AI service's
 * `clarification_questions` (see AiServiceDtos.ClarificationQuestion).
 */
export const makeClarificationRecommendation = (
  overrides: Partial<Recommendation> = {},
): Recommendation =>
  makeRecommendation({
    status: 'PENDING',
    confidence: null,
    analysis: {
      requires_more_information: true,
      clarification_questions: [
        {
          id: 'q1',
          question: 'Which database and tables does the intern need access to?',
          field: 'scope',
          required: true,
        },
        {
          id: 'q2',
          question: 'Is this access temporary, and if so, for how long?',
          field: 'duration',
          required: false,
        },
      ],
    },
    ...overrides,
  });

export const makeAdditionalInformationResponse = (
  overrides: Partial<AdditionalInformationResponse> = {},
): AdditionalInformationResponse => ({
  workItemId: 'wi-1',
  referenceKey: 'TASK-101',
  status: 'AI_ANALYSIS',
  recommendation: makeClarificationRecommendation(),
  ...overrides,
});

export const makeWeeklyReport = (overrides: Partial<WeeklyReport> = {}): WeeklyReport => ({
  id: 'report-1',
  departmentId: null,
  periodStart: '2026-07-27',
  periodEnd: '2026-08-03',
  generatedBy: 'user-1',
  generatedAt: '2026-08-03T09:00:00Z',
  metrics: {
    periodStart: '2026-07-27',
    periodEnd: '2026-08-03',
    departmentId: null,
    departmentName: null,
    statusCounts: { DONE: 3, IN_PROGRESS: 2, BLOCKED: 1 },
    completedThisWeek: [
      {
        referenceKey: 'TCK-101',
        summary: 'Reset locked account',
        completedBy: 'user-2',
        completedByName: 'Alex Rivera',
        completedAt: '2026-08-01T10:00:00Z',
      },
    ],
    activeByEmployee: [
      {
        employeeId: 'user-2',
        employeeName: 'Alex Rivera',
        tasks: [{ referenceKey: 'TCK-102', summary: 'Investigate outage', priority: 'HIGH', dueDate: null }],
      },
    ],
    blockedTasks: [
      {
        referenceKey: 'TCK-103',
        summary: 'Migrate database',
        assigneeName: 'Alex Rivera',
        blockReason: 'Waiting on vendor access',
        blockedSince: '2026-07-30T08:00:00Z',
      },
    ],
    overdueTasks: [],
    slaRiskTasks: [],
    departmentWorkload: [
      {
        departmentId: 'dept-1',
        departmentName: 'Engineering',
        totalCapacityHours: 160,
        assignedHours: 120,
        remainingCapacityHours: 40,
      },
    ],
    parentTicketProgress: [],
    reassignments: [],
    approvalChanges: [],
  },
  aiSummary: 'Three tasks completed this week; one remains blocked on vendor access.',
  aiRecommendedActions: ['Follow up on the vendor access blocker.'],
  aiProvider: 'mock',
  defaultRecipients: ['manager@acme.test'],
  recipients: null,
  status: 'DRAFT',
  emailStatus: 'NOT_SENT',
  emailFailureReason: null,
  sentAt: null,
  version: 0,
  ...overrides,
});

export const makeWeeklyReportHistory = (
  overrides: Partial<WeeklyReportHistory> = {},
): WeeklyReportHistory => ({
  items: [],
  page: 0,
  size: 20,
  totalElements: 0,
  ...overrides,
});

/** A promise that never settles, for asserting loading states. */
export const pending = <T>(): Promise<T> => new Promise<T>(() => undefined);
