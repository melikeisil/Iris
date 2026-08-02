import type { DemoManagerAnalytics, DemoNotification, DemoTeamWorkload } from './types';

/**
 * Fixtures for the Phase 8/9 gaps. Everything here is invented and is labelled as such
 * wherever it reaches the screen. Nothing here is ever merged into a real API response.
 */

const hoursAgo = (hours: number) => new Date(Date.now() - hours * 3_600_000).toISOString();

export const demoTeamWorkload: DemoTeamWorkload = {
  teamName: 'Platform',
  plannedRatio: 0.68,
  unplannedRatio: 0.32,
  members: [
    {
      userId: '00000000-0000-4000-8000-0000000d2001',
      fullName: 'Burak Aydın',
      role: 'Lead engineer',
      capacityHours: 40,
      assignedHours: 38,
      openItems: 6,
      blockedItems: 1,
      skills: ['Java', 'Spring Boot', 'Kubernetes'],
    },
    {
      userId: '00000000-0000-4000-8000-0000000d2002',
      fullName: 'Cem Polat',
      role: 'Senior engineer',
      capacityHours: 40,
      assignedHours: 44,
      openItems: 7,
      blockedItems: 2,
      skills: ['Go', 'Terraform', 'Observability'],
    },
    {
      userId: '00000000-0000-4000-8000-0000000d2003',
      fullName: 'Mert Kaya',
      role: 'Engineer',
      capacityHours: 40,
      assignedHours: 22,
      openItems: 3,
      blockedItems: 0,
      skills: ['Java', 'PostgreSQL'],
    },
    {
      userId: '00000000-0000-4000-8000-0000000d2004',
      fullName: 'Ece Yıldız',
      role: 'Junior engineer',
      capacityHours: 40,
      assignedHours: 12,
      openItems: 2,
      blockedItems: 0,
      skills: ['Java', 'Testing'],
    },
  ],
};

export const demoManagerAnalytics: DemoManagerAnalytics = {
  aiAcceptanceRate: 0.78,
  aiOverrideRate: 0.16,
  autoAssignedShare: 0.41,
  averageDecisionLatencyMinutes: 47,
  plannedVsUnplanned: { planned: 63, unplanned: 37 },
  policyExceptionsLast30Days: 9,
  departments: [
    { code: 'ENGINEERING', name: 'Engineering', utilisation: 0.91, openCritical: 3, slaAtRisk: 4 },
    { code: 'IT', name: 'IT', utilisation: 0.76, openCritical: 1, slaAtRisk: 2 },
    { code: 'HR', name: 'HR', utilisation: 0.52, openCritical: 0, slaAtRisk: 0 },
    { code: 'FINANCE', name: 'Finance', utilisation: 0.61, openCritical: 0, slaAtRisk: 1 },
  ],
};

export const demoNotifications: DemoNotification[] = [
  {
    id: 'n-1',
    kind: 'review',
    title: 'Review requested',
    detail: 'A team lead review is waiting on the identity refactor story.',
    workItemKey: 'STORY-148',
    workItemId: null,
    createdAt: hoursAgo(1),
    read: false,
  },
  {
    id: 'n-2',
    kind: 'comment',
    title: 'New comment',
    detail: 'A comment was added to the access-control integration tests task.',
    workItemKey: 'TASK-491',
    workItemId: null,
    createdAt: hoursAgo(3),
    read: false,
  },
  {
    id: 'n-3',
    kind: 'ai',
    title: 'Analysis completed',
    detail: 'Two additional test cases were suggested for the failover bug.',
    workItemKey: 'BUG-463',
    workItemId: null,
    createdAt: hoursAgo(9),
    read: true,
  },
  {
    id: 'n-4',
    kind: 'policy',
    title: 'Policy route changed',
    detail: 'A production-impacting change moved to manager approval.',
    workItemKey: 'BUG-469',
    workItemId: null,
    createdAt: hoursAgo(26),
    read: true,
  },
];

/**
 * Assignment candidate ranking.
 *
 * The backend computes and persists this (assignment.domain.ScoringEngine,
 * RecommendationCandidateRepository) but exposes it only as the response of the
 * side-effecting POST /work-items/{id}/route. There is no read endpoint, so a reviewer
 * opening their queue cannot fetch the ranking that was already calculated for them.
 *
 * These fixtures exist so the review screen is demonstrable; they are rendered behind a
 * "Demo data" badge and the assignee override is disabled when they are in use, because
 * approving with a fabricated candidate id would be a real, harmful lie.
 *
 * Recommended endpoint: GET /work-items/{id}/assignment-candidates.
 */
export const demoAssignmentCandidates = [
  {
    candidateUserId: '00000000-0000-4000-8000-0000000d3001',
    fullName: 'Cem Polat',
    rank: 1,
    totalScore: 91,
    scores: { skill: 95, capacity: 62, team: 88, experience: 90, component: 96, deadline: 84, fairness: 70 },
    workloadRatio: 0.88,
    explanation: 'Primary maintainer of the auth-service component; strongest skill match.',
  },
  {
    candidateUserId: '00000000-0000-4000-8000-0000000d3002',
    fullName: 'Deniz Aslan',
    rank: 2,
    totalScore: 87,
    scores: { skill: 88, capacity: 45, team: 90, experience: 92, component: 74, deadline: 80, fairness: 66 },
    workloadRatio: 1.05,
    explanation: 'High skill match but already over capacity this sprint.',
  },
  {
    candidateUserId: '00000000-0000-4000-8000-0000000d3003',
    fullName: 'Mert Kaya',
    rank: 3,
    totalScore: 83,
    scores: { skill: 74, capacity: 94, team: 86, experience: 70, component: 62, deadline: 92, fairness: 88 },
    workloadRatio: 0.55,
    explanation: 'Good availability and fairness; less familiar with this component.',
  },
] as const;

export const demoExcludedCandidates = [
  {
    candidateUserId: '00000000-0000-4000-8000-0000000d3004',
    reason: 'LEVEL_RESTRICTION',
    meaning: 'Employee level does not permit work at this risk level.',
  },
  {
    candidateUserId: '00000000-0000-4000-8000-0000000d3005',
    reason: 'ON_LEAVE',
    meaning: 'Unavailable for the whole of the deadline window.',
  },
] as const;
