import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client/endpoints';
import { queryKeys } from '@/api/queryKeys';

const options = { staleTime: 60_000, retry: false } as const;

export const useTeamWorkloadReport = (teamId: string | null) =>
  useQuery({
    queryKey: queryKeys.reports.teamWorkload(teamId),
    queryFn: ({ signal }) => api.reports.teamWorkload(teamId, signal),
    enabled: teamId !== null,
    ...options,
  });

export const useDepartmentCapacityReport = (departmentId: string | null) =>
  useQuery({
    queryKey: queryKeys.reports.departmentCapacity(departmentId),
    queryFn: ({ signal }) => api.reports.departmentCapacity(departmentId, signal),
    enabled: departmentId !== null,
    ...options,
  });

export const useAiAnalyticsReport = () =>
  useQuery({
    queryKey: queryKeys.reports.aiAnalytics(),
    queryFn: ({ signal }) => api.reports.aiAnalytics(signal),
    ...options,
  });
