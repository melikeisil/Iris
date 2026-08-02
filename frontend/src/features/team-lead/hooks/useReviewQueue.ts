import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client/endpoints';
import { queryKeys } from '@/api/queryKeys';
import { useAuth } from '@/app/providers/AuthProvider';
import type { Uuid } from '@/api/types';

/** GET /team-lead/review-queue - real endpoint, real data. */
export const useTeamReviewQueue = () => {
  const { status } = useAuth();
  return useQuery({
    queryKey: queryKeys.approvals.teamLeadQueue(),
    queryFn: ({ signal }) => api.approvals.teamLeadQueue(signal),
    enabled: status === 'authenticated',
    staleTime: 20_000,
  });
};

export const useOverdueApprovals = () =>
  useQuery({
    queryKey: queryKeys.approvals.overdue(),
    queryFn: ({ signal }) => api.approvals.overdue(signal),
    staleTime: 30_000,
  });

export const usePendingApprovals = (page = 0) =>
  useQuery({
    queryKey: queryKeys.approvals.pending(page),
    queryFn: ({ signal }) => api.approvals.pending(page, 20, signal),
    staleTime: 20_000,
  });

export const useManagerEscalations = () => {
  const { can } = useAuth();
  return useQuery({
    queryKey: queryKeys.approvals.managerEscalations(),
    queryFn: ({ signal }) => api.approvals.managerEscalations(signal),
    enabled: can('manager.escalations'),
    staleTime: 20_000,
  });
};

/**
 * Every approval mutation invalidates the whole approvals tree plus the affected work
 * item, because approving changes the item's assignee and status too.
 */
const useApprovalMutation = <TVariables>(
  mutationFn: (variables: TVariables) => Promise<unknown>,
  workItemId?: Uuid,
) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.approvals.all });
      if (workItemId) {
        void queryClient.invalidateQueries({ queryKey: queryKeys.workItems.detail(workItemId) });
      }
      void queryClient.invalidateQueries({ queryKey: queryKeys.workItems.all });
    },
  });
};

export const useApproveRecommendation = (workItemId?: Uuid) =>
  useApprovalMutation<{ id: Uuid; assigneeIdOverride?: Uuid | null; note?: string | null; version?: number | null }>(
    ({ id, ...body }) => api.approvals.approve(id, body),
    workItemId,
  );

export const useRejectRecommendation = (workItemId?: Uuid) =>
  useApprovalMutation<{ id: Uuid; reason: string }>(
    ({ id, reason }) => api.approvals.reject(id, reason),
    workItemId,
  );

export const useEscalateRecommendation = (workItemId?: Uuid) =>
  useApprovalMutation<{ id: Uuid; reason: string }>(
    ({ id, reason }) => api.approvals.escalate(id, reason),
    workItemId,
  );
