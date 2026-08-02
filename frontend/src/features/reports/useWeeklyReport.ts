import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client/endpoints';
import { queryKeys } from '@/api/queryKeys';
import type { Uuid } from '@/api/types';

/**
 * POST /manager/reports/weekly/generate — computes the current week's report from
 * persisted data and stores it as a draft. `useMutation`'s own `isPending` is what the
 * "Generate Report" button uses to disable itself and show a loading state; React Query
 * also refuses to run a second mutation while one is in flight, which is the client-side
 * half of "prevent duplicate clicks" (the server-side half is `ReportService`'s
 * compare-and-swap on send, and the outbox's unique constraint on the email event).
 */
export const useGenerateWeeklyReport = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (departmentId?: Uuid | null) => api.weeklyReport.generate(departmentId),
    onSuccess: (report) => {
      queryClient.setQueryData(queryKeys.weeklyReport.preview(report.id), report);
      void queryClient.invalidateQueries({ queryKey: queryKeys.weeklyReport.all });
    },
  });
};

export const useWeeklyReportPreview = (id: Uuid | null) =>
  useQuery({
    queryKey: queryKeys.weeklyReport.preview(id ?? 'none'),
    queryFn: ({ signal }) => api.weeklyReport.preview(id as Uuid, signal),
    enabled: id != null,
    staleTime: 10_000,
  });

export const useSendWeeklyReport = (id: Uuid | null) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (recipients: string[]) => api.weeklyReport.send(id as Uuid, recipients),
    onSuccess: (report) => {
      queryClient.setQueryData(queryKeys.weeklyReport.preview(report.id), report);
      void queryClient.invalidateQueries({ queryKey: queryKeys.weeklyReport.all });
    },
  });
};

export const useWeeklyReportHistory = (page = 0) =>
  useQuery({
    queryKey: queryKeys.weeklyReport.history(page),
    queryFn: ({ signal }) => api.weeklyReport.history(page, 20, signal),
    staleTime: 15_000,
  });
