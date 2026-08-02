/**
 * Types for capabilities that have no backend endpoint yet.
 *
 * These are deliberately kept in their own module tree so that the day a real endpoint
 * lands, the swap is: change the repository function in src/demo/repository.ts to call
 * `api.*`, delete the fixture, delete the entry in docs/phase7-api-gaps.md. No
 * presentation component imports anything from this folder except through the hooks in
 * src/demo/hooks.ts, and every one of them renders a "Demo data" badge.
 */
import type { Uuid } from '@/api/types';

export interface DemoTeamMemberLoad {
  userId: Uuid;
  fullName: string;
  role: string;
  capacityHours: number;
  assignedHours: number;
  openItems: number;
  blockedItems: number;
  skills: string[];
}

export interface DemoTeamWorkload {
  teamName: string;
  members: DemoTeamMemberLoad[];
  plannedRatio: number;
  unplannedRatio: number;
}

export interface DemoManagerAnalytics {
  aiAcceptanceRate: number;
  aiOverrideRate: number;
  autoAssignedShare: number;
  averageDecisionLatencyMinutes: number;
  plannedVsUnplanned: { planned: number; unplanned: number };
  departments: {
    code: string;
    name: string;
    utilisation: number;
    openCritical: number;
    slaAtRisk: number;
  }[];
  policyExceptionsLast30Days: number;
}

export interface DemoNotification {
  id: string;
  kind: 'comment' | 'review' | 'assignment' | 'ai' | 'policy';
  title: string;
  detail: string;
  workItemKey: string | null;
  workItemId: Uuid | null;
  createdAt: string;
  read: boolean;
}
