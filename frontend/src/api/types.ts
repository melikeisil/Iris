/**
 * Hand-written mirrors of the backend DTOs.
 *
 * Every shape here was read off the controller/DTO source under
 * backend/src/main/java/com/smartwork - nothing is guessed. Where the backend types a
 * field as an enum it is narrowed here; where it types it as a free string (because an
 * older engine version may have written a value the current enum no longer has, as with
 * workflow status codes) it stays a string.
 */

export type Uuid = string;
/** ISO-8601 instant. */
export type IsoInstant = string;
/** ISO-8601 local date, yyyy-MM-dd. */
export type IsoDate = string;

// ------------------------------------------------------------------ enums

export const SYSTEM_ROLES = ['EMPLOYEE', 'TEAM_LEAD', 'MANAGER', 'ADMIN'] as const;
export type SystemRole = (typeof SYSTEM_ROLES)[number];

/**
 * Resource-scoped relationships. Deliberately NOT system roles - see
 * com.smartwork.common.security.ResourceRole. PROCESS_OWNER is one of these.
 */
export const RESOURCE_ROLES = [
  'REQUESTER',
  'ASSIGNEE',
  'PROCESS_OWNER',
  'WATCHER',
  'APPROVER',
  'REVIEWER',
] as const;
export type ResourceRole = (typeof RESOURCE_ROLES)[number];

export const WORK_ITEM_TYPES = [
  'REQUEST',
  'INCIDENT',
  'EPIC',
  'FEATURE',
  'STORY',
  'BUG',
  'TASK',
  'TECHNICAL_DEBT',
  'RESEARCH',
  'TEST',
  'DOCUMENTATION',
  'SUBTASK',
] as const;
export type WorkItemType = (typeof WORK_ITEM_TYPES)[number];

export const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;
export type Priority = (typeof PRIORITIES)[number];

export const RISK_LEVELS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;
export type RiskLevel = (typeof RISK_LEVELS)[number];

export const COMPLEXITIES = ['LOW', 'MEDIUM', 'HIGH'] as const;
export type Complexity = (typeof COMPLEXITIES)[number];

export const STATE_CATEGORIES = [
  'TODO',
  'IN_PROGRESS',
  'BLOCKED',
  'REVIEW',
  'TESTING',
  'DONE',
] as const;
export type StateCategory = (typeof STATE_CATEGORIES)[number];

export const WORK_FAMILIES = [
  'SERVICE_WORK',
  'PROCESS_WORK',
  'DEVELOPMENT_WORK',
  'INCIDENT_WORK',
  'GENERAL_OPERATIONAL_WORK',
] as const;
export type WorkFamily = (typeof WORK_FAMILIES)[number];

export const ORIGIN_TYPES = [
  'USER_CREATED',
  'JIRA_IMPORTED',
  'TICKET_GENERATED',
  'AI_GENERATED',
  'PROCESS_TEMPLATE',
  'PROJECT_PLANNING',
  'INCIDENT_GENERATED',
  'RECURRING',
] as const;
export type OriginType = (typeof ORIGIN_TYPES)[number];

export const LINK_TYPES = [
  'PARENT_OF',
  'CHILD_OF',
  'BLOCKS',
  'DEPENDS_ON',
  'DUPLICATES',
  'RELATES_TO',
  'RESOLVES',
  'RESOLVED_BY',
  'CAUSED_BY',
  'IMPLEMENTS',
  'GENERATED_FROM',
  'TESTS',
] as const;
export type LinkType = (typeof LINK_TYPES)[number];

export const EMPLOYEE_LEVELS = ['INTERN', 'JUNIOR', 'MID', 'SENIOR', 'LEAD'] as const;
export type EmployeeLevel = (typeof EMPLOYEE_LEVELS)[number];

/** Exactly the strings CurrentUserAssembler.capabilities() can emit. */
export const CAPABILITIES = [
  'work_item.create',
  'work_item.view_own',
  'team.dashboard',
  'team.review_queue',
  'team.assign',
  'team.backlog',
  'sprint.manage',
  'manager.dashboard',
  'manager.escalations',
  'manager.capacity',
  'manager.reports',
  'policy.thresholds',
  'admin.users',
  'admin.organization',
  'admin.skills',
  'admin.projects',
  'admin.jira',
  'admin.audit',
] as const;
export type Capability = (typeof CAPABILITIES)[number];

// ------------------------------------------------------------------- auth

export interface DepartmentRef {
  id: Uuid;
  code: string;
  name: string;
}

export interface TeamRef {
  id: Uuid;
  code: string;
  name: string;
  isLead: boolean;
}

export interface CurrentUser {
  id: Uuid;
  email: string;
  fullName: string;
  employeeLevel: EmployeeLevel | string;
  organizationId: Uuid;
  department: DepartmentRef | null;
  teams: TeamRef[];
  roles: SystemRole[];
  organizationWide: boolean;
  capabilities: Capability[];
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  /** Seconds until the access token expires. */
  expiresIn: number;
  tokenType: string;
  user: CurrentUser;
}

// ------------------------------------------------------------- work items

export interface WorkItemSummary {
  id: Uuid;
  referenceKey: string;
  workItemType: WorkItemType;
  workFamily: WorkFamily;
  originType: OriginType;
  /** Workflow-profile specific code, e.g. BACKLOG, IN_PROGRESS, CODE_REVIEW. */
  status: string;
  statusCategory: StateCategory;
  summary: string;
  priority: Priority;
  riskLevel: RiskLevel | null;
  estimatedHours: number | null;
  storyPoints: number | null;
  dueDate: IsoDate | null;
  progressPercent: number | null;
  assigneeId: Uuid | null;
  teamId: Uuid | null;
  departmentId: Uuid | null;
  projectId: Uuid | null;
  sprintId: Uuid | null;
  parentId: Uuid | null;
  jiraIssueKey: string | null;
  blocked: boolean;
  updatedAt: IsoInstant;
}

export interface Sensitivity {
  securitySensitive: boolean;
  productionImpacting: boolean;
  budgetSensitive: boolean;
  complianceSensitive: boolean;
}

export interface ParentRef {
  id: Uuid;
  referenceKey: string;
  workItemType: WorkItemType;
  summary: string;
}

export interface ChildrenSummary {
  total: number;
  done: number;
  blocked: number;
  progressStrategy: string | null;
}

export interface BlockedInfo {
  since: IsoInstant;
  reason: string;
}

export interface JiraRef {
  issueKey: string | null;
  issueUrl: string | null;
  createdAt: IsoInstant | null;
}

export interface JiraLinkResponse {
  issueKey: string;
  issueUrl: string;
  createdAt: IsoInstant;
}

export interface JiraStatus {
  configured: boolean;
  baseUrl: string | null;
  projectKey: string | null;
  issueType: string | null;
}

export interface TeamMemberLoad {
  userId: Uuid;
  fullName: string;
  role: string;
  capacityHours: number;
  assignedHours: number;
  openItems: number;
  blockedItems: number;
  skills: string[];
}

export interface TeamWorkloadReport {
  teamId: Uuid;
  teamName: string;
  members: TeamMemberLoad[];
  plannedRatio: number;
  unplannedRatio: number;
}

export interface DepartmentTeamCapacity {
  teamId: Uuid;
  teamName: string;
  capacityHours: number;
  assignedHours: number;
  utilizationRatio: number;
  blockedItems: number;
}

export interface DepartmentCapacityReport {
  departmentId: Uuid;
  departmentName: string;
  totalCapacityHours: number;
  totalAssignedHours: number;
  utilizationRatio: number;
  teams: DepartmentTeamCapacity[];
}

export interface AiAnalyticsReport {
  aiAcceptanceRate: number;
  aiOverrideRate: number;
  autoAssignedShare: number;
  averageDecisionLatencyMinutes: number;
  plannedVsUnplanned: {
    planned: number;
    unplanned: number;
  };
  policyExceptionsLast30Days: number;
}

// ---------------------------------------------------------------- weekly reports

export interface WeeklyReportCompletedTask {
  referenceKey: string;
  summary: string;
  completedBy: Uuid | null;
  completedByName: string | null;
  completedAt: string;
}

export interface WeeklyReportActiveTask {
  referenceKey: string;
  summary: string;
  priority: string;
  dueDate: string | null;
}

export interface WeeklyReportEmployeeActiveTasks {
  employeeId: Uuid;
  employeeName: string;
  tasks: WeeklyReportActiveTask[];
}

export interface WeeklyReportBlockedTask {
  referenceKey: string;
  summary: string;
  assigneeName: string | null;
  blockReason: string;
  blockedSince: string;
}

export interface WeeklyReportDueTask {
  referenceKey: string;
  summary: string;
  assigneeName: string | null;
  dueDate: string;
  priority: string;
}

export interface WeeklyReportDepartmentWorkload {
  departmentId: Uuid;
  departmentName: string;
  totalCapacityHours: number;
  assignedHours: number;
  remainingCapacityHours: number;
}

export interface WeeklyReportParentTicketProgress {
  referenceKey: string;
  summary: string;
  progressPercent: number;
  childCount: number;
  childDoneCount: number;
}

export interface WeeklyReportReassignmentChange {
  referenceKey: string;
  summary: string;
  newAssigneeName: string;
  source: string;
  reason: string | null;
  assignedAt: string;
}

export interface WeeklyReportApprovalChange {
  referenceKey: string;
  summary: string;
  route: string;
  status: string;
  decidedByName: string | null;
  decidedAt: string;
}

export interface WeeklyReportMetrics {
  periodStart: string;
  periodEnd: string;
  departmentId: Uuid | null;
  departmentName: string | null;
  statusCounts: Record<string, number>;
  completedThisWeek: WeeklyReportCompletedTask[];
  activeByEmployee: WeeklyReportEmployeeActiveTasks[];
  blockedTasks: WeeklyReportBlockedTask[];
  overdueTasks: WeeklyReportDueTask[];
  slaRiskTasks: WeeklyReportDueTask[];
  departmentWorkload: WeeklyReportDepartmentWorkload[];
  parentTicketProgress: WeeklyReportParentTicketProgress[];
  reassignments: WeeklyReportReassignmentChange[];
  approvalChanges: WeeklyReportApprovalChange[];
}

export type WeeklyReportStatus = 'DRAFT' | 'SENDING' | 'SENT' | 'SEND_FAILED';
export type WeeklyReportEmailStatus = 'NOT_SENT' | 'PENDING' | 'SENT' | 'FAILED';

export interface WeeklyReport {
  id: Uuid;
  departmentId: Uuid | null;
  periodStart: string;
  periodEnd: string;
  generatedBy: Uuid;
  generatedAt: string;
  metrics: WeeklyReportMetrics;
  aiSummary: string | null;
  aiRecommendedActions: string[] | null;
  aiProvider: string | null;
  defaultRecipients: string[];
  recipients: string[] | null;
  status: WeeklyReportStatus;
  emailStatus: WeeklyReportEmailStatus;
  emailFailureReason: string | null;
  sentAt: string | null;
  version: number;
}

export interface WeeklyReportSummary {
  id: Uuid;
  departmentId: Uuid | null;
  periodStart: string;
  periodEnd: string;
  generatedAt: string;
  status: WeeklyReportStatus;
  emailStatus: WeeklyReportEmailStatus;
  emailFailureReason: string | null;
  sentAt: string | null;
}

export interface WeeklyReportHistory {
  items: WeeklyReportSummary[];
  page: number;
  size: number;
  totalElements: number;
}

/** Per-item permissions travel with the item; the UI renders exactly these. */
export interface WorkItemPermissions {
  canEdit: boolean;
  canAssign: boolean;
  canComment: boolean;
  canLink: boolean;
  canSeeInternalComments: boolean;
}

export interface AvailableTransition {
  code: string;
  label: string;
  toState: string;
  requiresReason: boolean;
  enabled: boolean;
  blockedReasons: string[];
}

export interface WorkItemDetail {
  id: Uuid;
  referenceKey: string;
  workItemType: WorkItemType;
  workFamily: WorkFamily;
  originType: OriginType;
  status: string;
  statusCategory: StateCategory;
  workflowCode: string | null;
  summary: string;
  description: string | null;
  acceptanceCriteria: string | null;
  priority: Priority;
  priorityScore: number | null;
  riskLevel: RiskLevel | null;
  complexity: Complexity | null;
  estimatedHours: number | null;
  remainingHours: number | null;
  storyPoints: number | null;
  dueDate: IsoDate | null;
  progressPercent: number | null;
  aiConfidence: number | null;
  sensitivity: Sensitivity | null;
  closureType: string | null;
  projectId: Uuid | null;
  sprintId: Uuid | null;
  parent: ParentRef | null;
  departmentId: Uuid | null;
  teamId: Uuid | null;
  assigneeId: Uuid | null;
  reporterId: Uuid | null;
  processOwnerId: Uuid | null;
  reviewerId: Uuid | null;
  childrenSummary: ChildrenSummary | null;
  blocked: BlockedInfo | null;
  jira: JiraRef | null;
  /** Resource roles the caller holds on this item. */
  relationships: ResourceRole[];
  permissions: WorkItemPermissions;
  availableTransitions: AvailableTransition[];
  createdAt: IsoInstant;
  updatedAt: IsoInstant;
  version: number;
}

export interface WorkItemComment {
  id: Uuid;
  authorId: Uuid;
  body: string;
  internal: boolean;
  createdAt: IsoInstant;
  updatedAt: IsoInstant;
}

export interface TransitionResult {
  id: Uuid;
  referenceKey: string;
  fromState: string;
  toState: string;
  statusCategory: StateCategory;
  transition: string;
  version: number;
}

export interface ChildrenResponse {
  children: WorkItemSummary[];
  progressPercent: number | null;
  progressStrategy: string | null;
  hasBlockedChildren: boolean;
}

export interface LinkView {
  linkId: Uuid;
  linkType: LinkType;
  workItemId: Uuid;
  referenceKey: string;
  workItemType: WorkItemType;
  summary: string;
  status: string;
  blocking: boolean;
  note: string | null;
}

export interface LinkGraph {
  outgoing: LinkView[];
  incoming: LinkView[];
}

export interface CreateWorkItemRequest {
  workItemType: WorkItemType;
  summary: string;
  description?: string | null;
  acceptanceCriteria?: string | null;
  projectId?: Uuid | null;
  sprintId?: Uuid | null;
  parentId?: Uuid | null;
  departmentId?: Uuid | null;
  teamId?: Uuid | null;
  priority?: Priority | null;
  complexity?: Complexity | null;
  estimatedHours?: number | null;
  storyPoints?: number | null;
  dueDate?: IsoDate | null;
  originType?: OriginType | null;
}

export interface UpdateWorkItemRequest {
  summary: string;
  description?: string | null;
  acceptanceCriteria?: string | null;
  priority: Priority;
  complexity?: Complexity | null;
  estimatedHours?: number | null;
  storyPoints?: number | null;
  dueDate?: IsoDate | null;
  sensitivity?: {
    security: boolean;
    production: boolean;
    budget: boolean;
    compliance: boolean;
  } | null;
  version?: number | null;
}

export interface TransitionRequest {
  transition: string;
  reason?: string | null;
  assigneeId?: Uuid | null;
  reviewerId?: Uuid | null;
  version?: number | null;
}

export interface BlockRequest {
  reason: string;
  blockedByWorkItemId?: Uuid | null;
  version?: number | null;
}

// --------------------------------------------------------------- analysis

export interface Provenance {
  provider: string | null;
  model: string | null;
  latencyMs: number | null;
  isMock: boolean;
}

/**
 * `analysis` and `plan` are free-form JSON produced by the AI service. They are typed
 * as unknown-valued records on purpose: the frontend reads them defensively through
 * lib/aiPayload.ts rather than pretending to a contract the backend does not enforce.
 */
export interface Recommendation {
  id: Uuid;
  workItemId: Uuid;
  type: string;
  status: string;
  confidence: number | null;
  analysis: Record<string, unknown> | null;
  plan: Record<string, unknown> | null;
  provenance: Provenance | null;
  errorDetail: string | null;
  createdAt: IsoInstant;
}

export interface SuggestedItem {
  tempId: string;
  title: string;
  description: string | null;
  workItemType: WorkItemType | null;
  departmentCode: string | null;
  teamCode: string | null;
  requiredSkills: string[] | null;
  estimatedHours: number | null;
  priority: Priority | null;
  riskLevel: RiskLevel | null;
  dependencies: string[] | null;
  explanation: string | null;
}

export interface Plan {
  source: string;
  recommendationId: Uuid | null;
  matchedTemplateCode: string | null;
  suggestedItems: SuggestedItem[];
  warnings: string[];
  needsProcessOwnerReview: boolean;
}

export interface AppliedPlan {
  processDefinitionId: Uuid | null;
  created: { id: Uuid; referenceKey: string; summary: string }[];
  dependencyLinksCreated: number;
  processOwnerId: Uuid | null;
}

export interface ProviderInfo {
  provider?: string;
  model?: string;
  mock?: boolean;
  [key: string]: unknown;
}

/**
 * One answer to one clarification question raised by the latest analysis.
 * `field` is echoed back from the question so the AI service can match answers to
 * questions on reanalysis - see AnalysisDtos.AnswerInput in the backend.
 */
export interface ClarificationAnswerInput {
  questionId: string;
  field: string | null;
  answer: string;
}

export interface SubmitAdditionalInformationRequest {
  answers: ClarificationAnswerInput[];
}

export interface AdditionalInformationResponse {
  workItemId: Uuid;
  referenceKey: string;
  status: string;
  recommendation: Recommendation;
}

// ----------------------------------------------------------------- policy

export interface ReasonResponse {
  code: string;
  meaning: string;
}

export interface ApprovalRequest {
  id: Uuid;
  workItemId: Uuid;
  recommendationId: Uuid | null;
  route: string;
  status: string;
  reasons: ReasonResponse[];
  assignedToScope: string | null;
  assignedToUserId: Uuid | null;
  policyException: boolean;
  dueAt: IsoInstant | null;
  overdue: boolean;
  escalatedFromId: Uuid | null;
  escalationReason: string | null;
  decidedBy: Uuid | null;
  decisionNote: string | null;
  decidedAt: IsoInstant | null;
  createdAt: IsoInstant;
}

export interface RoutingDecision {
  route: string;
  reasons: ReasonResponse[];
  explanation: string | null;
  suggestedSlaMinutes: number | null;
  policyId: Uuid | null;
  policyVersion: number | null;
  engineVersion: number;
}

/** The deterministic backend score. The frontend formats it and never recomputes it. */
export interface AssignmentCandidate {
  candidateUserId: Uuid;
  fullName: string;
  rank: number;
  totalScore: number;
  scores: {
    skill: number;
    capacity: number;
    team: number;
    experience: number;
    component: number;
    deadline: number;
    fairness: number;
  };
  workloadRatio: number | null;
  explanation: string | null;
}

export interface ExcludedCandidate {
  candidateUserId: Uuid;
  reason: string;
  meaning: string;
}

export interface RoutingOutcome {
  workItemId: Uuid;
  decision: RoutingDecision;
  assignedWithoutHuman: boolean;
  assigneeId: Uuid | null;
  assigneeName: string | null;
  approvalRequest: ApprovalRequest | null;
  candidatePoolTier: string | null;
  candidates: AssignmentCandidate[];
  excludedCandidates: ExcludedCandidate[];
}

// ------------------------------------------------------------------ admin

export interface AdminUser {
  id: Uuid;
  email: string;
  fullName: string;
  employeeLevel: string;
  departmentId: Uuid | null;
  roles: SystemRole[];
  organizationWide: boolean;
  active: boolean;
}

export interface Department {
  id: Uuid;
  organizationId: Uuid;
  code: string;
  name: string;
  [key: string]: unknown;
}

export interface Team {
  id: Uuid;
  organizationId: Uuid;
  departmentId: Uuid;
  code: string;
  name: string;
  [key: string]: unknown;
}

export interface TeamMember {
  userId: Uuid;
  allocationPercent: number;
  primaryTeam: boolean;
}

export interface Project {
  id: Uuid;
  organizationId: Uuid;
  departmentId: Uuid | null;
  code: string;
  name: string;
  [key: string]: unknown;
}

export interface Skill {
  id: Uuid;
  code: string;
  name: string;
  category: string;
  active: boolean;
}

export interface UserSkill {
  skillId: Uuid;
  code: string;
  name: string;
  proficiency: number;
  yearsExperience: number | null;
  lastUsedAt: IsoDate | null;
}

// ------------------------------------------------------------- pagination

export interface PageMetadata {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PageResponse<T> {
  content: T[];
  page: PageMetadata;
  sort: { field: string; direction: string }[];
}
