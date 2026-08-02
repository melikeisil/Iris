import { http, type QueryParams } from './http';

// Re-exported so feature hooks can type their own parameter objects without reaching
// into the transport module.
export type { QueryParams };
import type {
  AdditionalInformationResponse,
  AppliedPlan,
  ApprovalRequest,
  ChildrenResponse,
  CreateWorkItemRequest,
  CurrentUser,
  LinkGraph,
  LinkType,
  PageResponse,
  Plan,
  Priority,
  ProviderInfo,
  Recommendation,
  RiskLevel,
  RoutingOutcome,
  SubmitAdditionalInformationRequest,
  TokenResponse,
  TransitionResult,
  UpdateWorkItemRequest,
  Uuid,
  JiraLinkResponse,
  JiraStatus,
  TeamWorkloadReport,
  DepartmentCapacityReport,
  AiAnalyticsReport,
  WeeklyReport,
  WeeklyReportHistory,
  WorkItemComment,
  WorkItemDetail,
  WorkItemSummary,
} from '../types';

/**
 * Every real Phase 1-6 endpoint, one function each. Paths and shapes were taken from
 * the controller source; nothing here is speculative. Endpoints that do not exist yet
 * live in src/demo, never here.
 */

export interface WorkItemSearchParams extends QueryParams {
  type?: string[];
  status?: string[];
  priority?: string[];
  family?: string[];
  origin?: string[];
  teamId?: string;
  departmentId?: string;
  assigneeId?: string;
  projectId?: string;
  sprintId?: string;
  parentId?: string;
  unassigned?: boolean;
  blocked?: boolean;
  dueBefore?: string;
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const api = {
  auth: {
    login: (email: string, password: string) =>
      http.post<TokenResponse>('/auth/login', { email, password }, { anonymous: true }),
    logout: (refreshToken: string) =>
      http.post<void>('/auth/logout', { refreshToken }, { anonymous: true }),
    me: (signal?: AbortSignal) => http.get<CurrentUser>('/auth/me', { signal }),
  },

  jira: {
    status: (signal?: AbortSignal) => http.get<JiraStatus>('/jira/status', { signal }),
  },

  workItems: {
    create: (body: CreateWorkItemRequest) => http.post<WorkItemDetail>('/work-items', body),
    search: (params: WorkItemSearchParams, signal?: AbortSignal) =>
      http.get<PageResponse<WorkItemSummary>>('/work-items', { query: params, signal }),
    get: (id: Uuid, signal?: AbortSignal) =>
      http.get<WorkItemDetail>(`/work-items/${id}`, { signal }),
    update: (id: Uuid, body: UpdateWorkItemRequest) =>
      http.put<WorkItemDetail>(`/work-items/${id}`, body),
    override: (
      id: Uuid,
      body: { departmentId?: Uuid | null; teamId?: Uuid | null; priority?: Priority | null; riskLevel?: RiskLevel | null; route?: string | null }
    ) => http.post<WorkItemDetail>(`/work-items/${id}/override`, body),
    mine: (signal?: AbortSignal) => http.get<WorkItemSummary[]>('/work-items/my', { signal }),
    createdByMe: (page = 0, size = 25, signal?: AbortSignal) =>
      http.get<PageResponse<WorkItemSummary>>('/work-items/created-by-me', {
        query: { page, size },
        signal,
      }),
    watching: (page = 0, size = 25, signal?: AbortSignal) =>
      http.get<PageResponse<WorkItemSummary>>('/work-items/watching', {
        query: { page, size },
        signal,
      }),
    children: (id: Uuid, signal?: AbortSignal) =>
      http.get<ChildrenResponse>(`/work-items/${id}/children`, { signal }),
    possibleDuplicates: (id: Uuid, signal?: AbortSignal) =>
      http.get<WorkItemSummary[]>(`/work-items/${id}/possible-duplicates`, { signal }),
    transition: (
      id: Uuid,
      body: { transition: string; reason?: string | null; assigneeId?: Uuid | null; reviewerId?: Uuid | null; version?: number | null },
    ) => http.post<TransitionResult>(`/work-items/${id}/transition`, body),
    block: (id: Uuid, body: { reason: string; blockedByWorkItemId?: Uuid | null; version?: number | null }) =>
      http.post<TransitionResult>(`/work-items/${id}/block`, body),
    setRemainingHours: (id: Uuid, remainingHours: number | null) =>
      http.put<void>(`/work-items/${id}/remaining-hours`, { remainingHours }),
    comments: (id: Uuid, signal?: AbortSignal) =>
      http.get<WorkItemComment[]>(`/work-items/${id}/comments`, { signal }),
    addComment: (id: Uuid, body: string, internal: boolean) =>
      http.post<WorkItemComment>(`/work-items/${id}/comments`, { body, internal }),
    watch: (id: Uuid) => http.post<void>(`/work-items/${id}/watch`),
    unwatch: (id: Uuid) => http.delete<void>(`/work-items/${id}/watch`),
    links: (id: Uuid, signal?: AbortSignal) =>
      http.get<LinkGraph>(`/work-items/${id}/links`, { signal }),
    addLink: (id: Uuid, linkType: LinkType, targetId: Uuid, note?: string) =>
      http.post<LinkGraph>(`/work-items/${id}/links`, { linkType, targetId, note: note ?? null }),
    removeLink: (id: Uuid, linkId: Uuid) => http.delete<void>(`/work-items/${id}/links/${linkId}`),
    createInJira: (id: Uuid) =>
      http.post<JiraLinkResponse>(`/work-items/${id}/jira-create`),
  },

  analysis: {
    analyze: (id: Uuid) => http.post<Recommendation>(`/work-items/${id}/analyze`),
    history: (id: Uuid, signal?: AbortSignal) =>
      http.get<Recommendation[]>(`/work-items/${id}/recommendations`, { signal }),
    proposePlan: (id: Uuid) => http.post<Plan>(`/work-items/${id}/plan`),
    applyPlan: (id: Uuid, templateId: Uuid) =>
      http.post<AppliedPlan>(`/work-items/${id}/apply-plan`, undefined, {
        query: { templateId },
      }),
    recommendation: (id: Uuid, signal?: AbortSignal) =>
      http.get<Recommendation>(`/recommendations/${id}`, { signal }),
    provider: (signal?: AbortSignal) => http.get<ProviderInfo>('/ai/provider', { signal }),
    submitAdditionalInformation: (id: Uuid, body: SubmitAdditionalInformationRequest) =>
      http.post<AdditionalInformationResponse>(`/work-items/${id}/additional-information`, body),
  },

  routing: {
    route: (id: Uuid) => http.post<RoutingOutcome>(`/work-items/${id}/route`),
  },

  approvals: {
    pending: (page = 0, size = 20, signal?: AbortSignal) =>
      http.get<PageResponse<ApprovalRequest>>('/approvals/pending', {
        query: { page, size },
        signal,
      }),
    overdue: (signal?: AbortSignal) =>
      http.get<ApprovalRequest[]>('/approvals/overdue', { signal }),
    teamLeadQueue: (signal?: AbortSignal) =>
      http.get<ApprovalRequest[]>('/team-lead/review-queue', { signal }),
    processOwnerQueue: (signal?: AbortSignal) =>
      http.get<ApprovalRequest[]>('/process-owner/review-queue', { signal }),
    managerEscalations: (signal?: AbortSignal) =>
      http.get<ApprovalRequest[]>('/manager/escalations', { signal }),
    approve: (id: Uuid, body: { assigneeIdOverride?: Uuid | null; note?: string | null; version?: number | null }) =>
      http.post<ApprovalRequest>(`/approvals/${id}/approve`, body),
    reject: (id: Uuid, reason: string) =>
      http.post<ApprovalRequest>(`/approvals/${id}/reject`, { reason }),
    escalate: (id: Uuid, reason: string) =>
      http.post<ApprovalRequest>(`/approvals/${id}/escalate`, { reason }),
  },

  reports: {
    teamWorkload: (teamId?: Uuid | null, signal?: AbortSignal) =>
      http.get<TeamWorkloadReport>('/team-lead/workload', {
        query: teamId ? { teamId } : undefined,
        signal,
      }),
    departmentCapacity: (departmentId?: Uuid | null, signal?: AbortSignal) =>
      http.get<DepartmentCapacityReport>('/manager/capacity', {
        query: departmentId ? { departmentId } : undefined,
        signal,
      }),
    aiAnalytics: (signal?: AbortSignal) =>
      http.get<AiAnalyticsReport>('/reports/ai', { signal }),
  },

  weeklyReport: {
    generate: (departmentId?: Uuid | null) =>
      http.post<WeeklyReport>('/manager/reports/weekly/generate', { departmentId: departmentId ?? null }),
    preview: (id: Uuid, signal?: AbortSignal) =>
      http.get<WeeklyReport>(`/manager/reports/weekly/${id}/preview`, { signal }),
    send: (id: Uuid, recipients: string[]) =>
      http.post<WeeklyReport>(`/manager/reports/weekly/${id}/send`, { recipients }),
    history: (page = 0, size = 20, signal?: AbortSignal) =>
      http.get<WeeklyReportHistory>('/manager/reports/weekly/history', {
        query: { page, size },
        signal,
      }),
  },

  // The /admin/* reference-data endpoints (users, departments, teams, projects, skills)
  // exist in the backend but no MVP route consumes them: there are no admin screens, and
  // a plain EMPLOYEE is not authorised to read them anyway. Re-add the block here when a
  // screen genuinely needs it rather than keeping an unused surface in the client.
} as const;
