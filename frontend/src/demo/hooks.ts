import { useQuery } from '@tanstack/react-query';
import { queryKeys } from '@/api/queryKeys';
import { environment } from '@/config/environment';
import { demoRepository } from './repository';

/**
 * Demo-mode query hooks. Each one mirrors the signature a real hook would have, so the
 * swap is a one-line change in ./repository.ts.
 *
 * Every screen that consumes one of these must render <DemoDataBadge />. That is
 * enforced by convention and checked in docs/demo-mode.md, not by types.
 */

const options = { staleTime: 60_000, retry: false } as const;

export const useDemoTeamWorkload = (teamId: string | null = null) =>
  useQuery({
    queryKey: queryKeys.demo.teamWorkload(teamId),
    queryFn: () => demoRepository.teamWorkload(),
    ...options,
  });

export const useDemoManagerAnalytics = () =>
  useQuery({
    queryKey: queryKeys.demo.managerAnalytics(),
    queryFn: () => demoRepository.managerAnalytics(),
    ...options,
  });

export const useDemoNotifications = () =>
  useQuery({
    queryKey: queryKeys.demo.notifications(),
    queryFn: () => demoRepository.notifications(),
    ...options,
  });

export const isDemoModeEnabled = (): boolean => environment.demoMode;
