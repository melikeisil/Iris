import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, type WorkItemSearchParams } from '@/api/client/endpoints';
import { queryKeys } from '@/api/queryKeys';
import type { SystemRole } from '@/api/types';
import { useAuth } from '@/app/providers/AuthProvider';
import {
  useCreatedTickets,
  useMyWorkItems,
  useWatchedWorkItems,
} from '@/features/work-items/hooks/useWorkItems';
import {
  useManagerEscalations,
  useOverdueApprovals,
  usePendingApprovals,
  useTeamReviewQueue,
} from '@/features/team-lead/hooks/useReviewQueue';
import { toWorkItemRows, NEEDS_INFO, type WorkItemRow } from '@/features/work-items/viewModel';
import { byUrgency, isDueToday, isOverdue } from '@/lib/workflow';

/**
 * The dashboard's view model.
 *
 * There is no `/dashboard` endpoint and this does not pretend there is: each panel
 * composes real endpoints and degrades independently, so an approvals hiccup leaves the
 * rest of the page working.
 *
 * Two derivations are presentation heuristics over real data, documented in
 * docs/phase7-api-gaps.md:
 *  - "needs information" matches reported items whose workflow status names an
 *    information wait (INFO / CLARIF / WAITING). No endpoint lists clarification
 *    requests, and the status is the authoritative signal the backend already exposes.
 *  - "awaiting my verification" is reported items in the REVIEW category, which is
 *    where the service workflow parks work awaiting requester sign-off.
 */

/** Which widget set the dashboard shows. A user may hold several roles. */
export type DashboardAudience = 'MANAGER' | 'TEAM_LEAD' | 'EMPLOYEE';

export const audienceFor = (
  hasRole: (...roles: SystemRole[]) => boolean,
  can: (capability: 'manager.dashboard' | 'team.review_queue') => boolean,
): DashboardAudience => {
  if (can('manager.dashboard') || hasRole('MANAGER')) return 'MANAGER';
  if (can('team.review_queue') || hasRole('TEAM_LEAD')) return 'TEAM_LEAD';
  return 'EMPLOYEE';
};

export interface PersonalWork {
  assignedOpen: WorkItemRow[];
  todaysFocus: WorkItemRow[];
  dueSoonCount: number;
  blockedCount: number;
  needsInformationCount: number;
  reportedOpen: WorkItemRow[];
  watching: WorkItemRow[];
  reviewsRequestedCount: number;
}

/**
 * Server-side counts, from GET /work-items with `size=1`, read off `page.totalElements`.
 * Only filters the endpoint genuinely supports are used - there is no "not done"
 * filter, so the open/completed split is derived separately (see `useManagerSplit`).
 */
const useScopedCount = (
  key: string,
  params: WorkItemSearchParams,
  enabled: boolean,
) =>
  useQuery({
    queryKey: [...queryKeys.workItems.all, 'count', key, params] as const,
    queryFn: ({ signal }) => api.workItems.search({ ...params, page: 0, size: 1 }, signal),
    enabled,
    staleTime: 60_000,
    select: (page) => page.page.totalElements,
  });

/**
 * The open / completed split.
 *
 * `GET /work-items` has no status-category filter and no aggregate endpoint exists, so
 * this reads the most recently updated page and counts categories client-side. The
 * page size is exposed on the result so the panel can say plainly that the split covers
 * the most recent N items rather than implying an organisation-wide total.
 */
const MANAGER_SPLIT_SIZE = 200;

const useManagerSplit = (enabled: boolean) =>
  useQuery({
    queryKey: [...queryKeys.workItems.all, 'manager-split', MANAGER_SPLIT_SIZE] as const,
    queryFn: ({ signal }) =>
      api.workItems.search({ page: 0, size: MANAGER_SPLIT_SIZE, sort: 'updatedAt,desc' }, signal),
    enabled,
    staleTime: 60_000,
    select: (page) => {
      const items = page.content;
      return {
        sampled: items.length,
        total: page.page.totalElements,
        truncated: page.page.totalElements > items.length,
        open: items.filter((item) => item.statusCategory !== 'DONE').length,
        completed: items.filter((item) => item.statusCategory === 'DONE').length,
      };
    },
  });

export const useDashboard = () => {
  const { can, hasRole, user } = useAuth();
  const audience = audienceFor(hasRole, (capability) => can(capability));

  // ------------------------------------------------------------ personal work
  const assigned = useMyWorkItems();
  const reported = useCreatedTickets(0);
  const watching = useWatchedWorkItems(0);
  const pendingApprovals = usePendingApprovals(0);

  const personal = useMemo<PersonalWork>(() => {
    const assignedItems = assigned.data ?? [];
    const reportedItems = reported.data?.content ?? [];
    const open = assignedItems.filter((item) => item.statusCategory !== 'DONE');

    return {
      assignedOpen: toWorkItemRows(open),
      todaysFocus: toWorkItemRows(
        [...open]
          .filter(
            (item) =>
              item.blocked ||
              isOverdue(item.dueDate) ||
              isDueToday(item.dueDate) ||
              item.priority === 'CRITICAL' ||
              item.priority === 'HIGH',
          )
          .sort(byUrgency)
          .slice(0, 6),
      ),
      dueSoonCount: open.filter((item) => isDueToday(item.dueDate) || isOverdue(item.dueDate))
        .length,
      blockedCount: open.filter((item) => item.blocked).length,
      needsInformationCount: reportedItems.filter((item) => NEEDS_INFO.test(item.status)).length,
      reportedOpen: toWorkItemRows(
        reportedItems.filter((item) => item.statusCategory !== 'DONE'),
      ),
      watching: toWorkItemRows(watching.data?.content ?? []),
      reviewsRequestedCount: pendingApprovals.data?.page.totalElements ?? 0,
    };
  }, [assigned.data, reported.data, watching.data, pendingApprovals.data]);

  // ---------------------------------------------------------------- team lead
  const reviewQueue = useTeamReviewQueue();
  const overdueApprovals = useOverdueApprovals();

  // ------------------------------------------------------------------ manager
  const escalations = useManagerEscalations();
  const isManager = audience === 'MANAGER';
  const split = useManagerSplit(isManager);
  const blockedCount = useScopedCount('blocked', { blocked: true }, isManager);
  const overdueCount = useScopedCount(
    'overdue',
    { dueBefore: new Date().toISOString().slice(0, 10) },
    isManager,
  );

  return {
    audience,
    user,
    personal,
    teamLead: {
      reviewQueue,
      pendingApprovals,
      overdueApprovals,
    },
    manager: {
      escalations,
      split,
      blockedCount,
      overdueCount,
    },
    isLoading: assigned.isLoading,
    error: assigned.error,
    refetchAll: () => {
      void assigned.refetch();
      void reported.refetch();
      void watching.refetch();
      void pendingApprovals.refetch();
    },
  };
};
