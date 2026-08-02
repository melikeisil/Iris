import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client/endpoints';
import { queryKeys } from '@/api/queryKeys';
import type { ClarificationAnswerInput, Uuid } from '@/api/types';
import { toAnalysisView } from '@/lib/aiPayload';

/**
 * AI analysis always goes through Spring Boot. The React app never calls the FastAPI
 * service directly - the backend owns the service token and the org context.
 */
export const useRecommendationHistory = (workItemId: Uuid | undefined, enabled = true) =>
  useQuery({
    queryKey: queryKeys.analysis.history(workItemId ?? ''),
    queryFn: ({ signal }) => api.analysis.history(workItemId as Uuid, signal),
    enabled: Boolean(workItemId) && enabled,
  });

export const useLatestAnalysis = (workItemId: Uuid | undefined, enabled = true) => {
  const query = useRecommendationHistory(workItemId, enabled);
  return { ...query, view: toAnalysisView(query.data?.[0] ?? null), latest: query.data?.[0] ?? null };
};

/**
 * POST /work-items/{id}/analyze never fails the request when the AI service is down -
 * it stores a FAILED recommendation with the reason. The UI therefore reads
 * `status === 'FAILED'` rather than relying on an HTTP error.
 */
export const useAnalyzeWorkItem = (workItemId?: Uuid) => {
  const queryClient = useQueryClient();
  return useMutation({
    // The id may be passed at call time. A freshly created work item's id is not yet
    // in state when the create flow chains straight into analysis, so relying on the
    // closure alone would post to /work-items/undefined/analyze.
    mutationFn: (id?: Uuid) => api.analysis.analyze((id ?? workItemId) as Uuid),
    onSuccess: (_recommendation, id) => {
      const target = id ?? workItemId ?? '';
      void queryClient.invalidateQueries({ queryKey: queryKeys.analysis.history(target) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.workItems.detail(target) });
    },
  });
};

export const useProposePlan = (workItemId: Uuid | undefined) =>
  useMutation({ mutationFn: () => api.analysis.proposePlan(workItemId as Uuid) });

/**
 * Answers the latest analysis's clarification questions. The backend both persists the
 * answers and moves the item WAITING_INFORMATION -> AI_ANALYSIS in one call, so both the
 * recommendation history and the item detail (status, version) need refetching on success.
 */
export const useSubmitAdditionalInformation = (workItemId: Uuid | undefined) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (answers: ClarificationAnswerInput[]) =>
      api.analysis.submitAdditionalInformation(workItemId as Uuid, { answers }),
    onSuccess: () => {
      const target = workItemId ?? '';
      void queryClient.invalidateQueries({ queryKey: queryKeys.analysis.history(target) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.workItems.detail(target) });
    },
  });
};

export const useApplyPlan = (workItemId: Uuid | undefined) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (templateId: Uuid) => api.analysis.applyPlan(workItemId as Uuid, templateId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.workItems.children(workItemId ?? '') });
      void queryClient.invalidateQueries({ queryKey: queryKeys.workItems.detail(workItemId ?? '') });
    },
  });
};

/** Drives the "demo analysis" badge when the deterministic mock provider is active. */
export const useAiProvider = () =>
  useQuery({
    queryKey: queryKeys.analysis.provider,
    queryFn: ({ signal }) => api.analysis.provider(signal),
    staleTime: 5 * 60_000,
    retry: false,
  });

/**
 * POST /work-items/{id}/route runs the full deterministic pipeline and returns the
 * ranked candidates with every score component. It requires a prior analysis.
 */
export const useRouteWorkItem = (workItemId: Uuid | undefined) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.routing.route(workItemId as Uuid),
    onSuccess: (outcome) => {
      queryClient.setQueryData(queryKeys.routing.outcome(workItemId ?? ''), outcome);
      void queryClient.invalidateQueries({ queryKey: queryKeys.approvals.all });
      void queryClient.invalidateQueries({ queryKey: queryKeys.workItems.detail(workItemId ?? '') });
    },
  });
};
