import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type WorkItemSearchParams } from '@/api/client/endpoints';
import { queryKeys } from '@/api/queryKeys';
import type {
  CreateWorkItemRequest,
  LinkType,
  UpdateWorkItemRequest,
  Uuid,
  WorkItemDetail,
} from '@/api/types';

/**
 * Typed hooks over the real work-item API. Mutations invalidate exactly the queries
 * they can have changed; nothing reloads the page.
 */

export const useWorkItemSearch = (params: WorkItemSearchParams, enabled = true) =>
  useQuery({
    queryKey: queryKeys.workItems.search(params),
    queryFn: ({ signal }) => api.workItems.search(params, signal),
    enabled,
    placeholderData: (previous) => previous,
  });

export const useMyWorkItems = () =>
  useQuery({
    queryKey: queryKeys.workItems.mine(),
    queryFn: ({ signal }) => api.workItems.mine(signal),
    staleTime: 15_000,
  });

export const useCreatedTickets = (page = 0) =>
  useQuery({
    queryKey: queryKeys.workItems.createdByMe(page),
    queryFn: ({ signal }) => api.workItems.createdByMe(page, 25, signal),
  });

export const useWatchedWorkItems = (page = 0) =>
  useQuery({
    queryKey: queryKeys.workItems.watching(page),
    queryFn: ({ signal }) => api.workItems.watching(page, 25, signal),
  });

export const useWorkItem = (id: Uuid | undefined) =>
  useQuery({
    queryKey: queryKeys.workItems.detail(id ?? ''),
    queryFn: ({ signal }) => api.workItems.get(id as Uuid, signal),
    enabled: Boolean(id),
  });

export const useJiraStatus = () =>
  useQuery({
    queryKey: ['jira', 'status'],
    queryFn: ({ signal }) => api.jira.status(signal),
    staleTime: 60_000,
  });

export const useWorkItemChildren = (id: Uuid | undefined, enabled = true) =>
  useQuery({
    queryKey: queryKeys.workItems.children(id ?? ''),
    queryFn: ({ signal }) => api.workItems.children(id as Uuid, signal),
    enabled: Boolean(id) && enabled,
  });

export const useWorkItemLinks = (id: Uuid | undefined, enabled = true) =>
  useQuery({
    queryKey: queryKeys.workItems.links(id ?? ''),
    queryFn: ({ signal }) => api.workItems.links(id as Uuid, signal),
    enabled: Boolean(id) && enabled,
  });

export const useWorkItemComments = (id: Uuid | undefined, enabled = true) =>
  useQuery({
    queryKey: queryKeys.workItems.comments(id ?? ''),
    queryFn: ({ signal }) => api.workItems.comments(id as Uuid, signal),
    enabled: Boolean(id) && enabled,
  });

export const usePossibleDuplicates = (id: Uuid | undefined, enabled = true) =>
  useQuery({
    queryKey: queryKeys.workItems.duplicates(id ?? ''),
    queryFn: ({ signal }) => api.workItems.possibleDuplicates(id as Uuid, signal),
    enabled: Boolean(id) && enabled,
  });

// ------------------------------------------------------------------ mutations

const useWorkItemMutation = <TVariables, TData>(
  mutationFn: (variables: TVariables) => Promise<TData>,
  id?: Uuid,
) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => {
      if (id) void queryClient.invalidateQueries({ queryKey: queryKeys.workItems.detail(id) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.workItems.all });
    },
  });
};

export const useCreateWorkItem = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateWorkItemRequest) => api.workItems.create(body),
    onSuccess: (created) => {
      queryClient.setQueryData(queryKeys.workItems.detail(created.id), created);
      void queryClient.invalidateQueries({ queryKey: queryKeys.workItems.all });
    },
  });
};

export const useUpdateWorkItem = (id: Uuid) =>
  useWorkItemMutation((body: UpdateWorkItemRequest) => api.workItems.update(id, body), id);

/**
 * Transitions carry a transition NAME, never a status. The optimistic-locking `version`
 * is always sent so a concurrent edit surfaces as 409 CONCURRENT_MODIFICATION rather
 * than silently overwriting someone.
 */
export const useTransitionWorkItem = (id: Uuid) =>
  useWorkItemMutation(
    (body: { transition: string; reason?: string | null; assigneeId?: Uuid | null; reviewerId?: Uuid | null; version?: number | null }) =>
      api.workItems.transition(id, body),
    id,
  );

export const useBlockWorkItem = (id: Uuid) =>
  useWorkItemMutation(
    (body: { reason: string; blockedByWorkItemId?: Uuid | null; version?: number | null }) =>
      api.workItems.block(id, body),
    id,
  );

export const useAddComment = (id: Uuid) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ body, internal }: { body: string; internal: boolean }) =>
      api.workItems.addComment(id, body, internal),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.workItems.comments(id) });
    },
  });
};

export const useToggleWatch = (id: Uuid) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (watching: boolean) =>
      watching ? api.workItems.unwatch(id) : api.workItems.watch(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.workItems.detail(id) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.workItems.watching(0) });
    },
  });
};

export const useCreateLink = (id: Uuid) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ linkType, targetId, note }: { linkType: LinkType; targetId: Uuid; note?: string }) =>
      api.workItems.addLink(id, linkType, targetId, note),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.workItems.links(id) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.workItems.detail(id) });
    },
  });
};

export const useRemoveLink = (id: Uuid) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (linkId: Uuid) => api.workItems.removeLink(id, linkId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.workItems.links(id) });
    },
  });
};

export const useUpdateRemainingHours = (id: Uuid) =>
  useWorkItemMutation((remainingHours: number | null) => api.workItems.setRemainingHours(id, remainingHours), id);

export const useCreateJiraIssue = (id: Uuid) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.workItems.createInJira(id),
    onSuccess: (jira) => {
      queryClient.setQueryData<WorkItemDetail | undefined>(queryKeys.workItems.detail(id), (current) =>
        current
          ? {
              ...current,
              jira: {
                issueKey: jira.issueKey,
                issueUrl: jira.issueUrl,
                createdAt: jira.createdAt,
              },
            }
          : current,
      );
      void queryClient.invalidateQueries({ queryKey: queryKeys.workItems.detail(id) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.workItems.all });
    },
  });
};
