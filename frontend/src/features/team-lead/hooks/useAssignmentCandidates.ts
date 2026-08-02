import { useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '@/api/queryKeys';
import { environment } from '@/config/environment';
import { demoAssignmentCandidates, demoExcludedCandidates } from '@/demo/fixtures';
import type { AssignmentCandidate, ExcludedCandidate, RoutingOutcome, Uuid } from '@/api/types';

/**
 * The reviewer's candidate ranking.
 *
 * Real source: the response of POST /work-items/{id}/route, which this app caches under
 * queryKeys.routing.outcome. That call has side effects (it can auto-assign or open an
 * approval), so the review screen never triggers it just to look at a ranking.
 *
 * When no cached outcome exists there is no read endpoint to fall back on, which is a
 * documented gap. In demo mode a fixture ranking is returned and flagged; with demo
 * mode off the screen says plainly that the ranking is unavailable.
 */
export interface CandidateRanking {
  candidates: readonly AssignmentCandidate[];
  excluded: readonly ExcludedCandidate[];
  /** True when the numbers came from the backend rather than a fixture. */
  isReal: boolean;
  available: boolean;
  poolTier: string | null;
}

export const useAssignmentCandidates = (workItemId: Uuid | null): CandidateRanking => {
  const queryClient = useQueryClient();

  const cached = workItemId
    ? queryClient.getQueryData<RoutingOutcome>(queryKeys.routing.outcome(workItemId))
    : undefined;

  if (cached) {
    return {
      candidates: cached.candidates,
      excluded: cached.excludedCandidates,
      isReal: true,
      available: true,
      poolTier: cached.candidatePoolTier,
    };
  }

  if (environment.demoMode) {
    return {
      candidates: demoAssignmentCandidates as readonly AssignmentCandidate[],
      excluded: demoExcludedCandidates as readonly ExcludedCandidate[],
      isReal: false,
      available: true,
      poolTier: 'TEAM',
    };
  }

  return { candidates: [], excluded: [], isReal: false, available: false, poolTier: null };
};
