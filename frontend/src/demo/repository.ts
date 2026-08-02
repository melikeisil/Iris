import { environment } from '@/config/environment';
import { demoManagerAnalytics, demoNotifications, demoTeamWorkload } from './fixtures';
import type { DemoManagerAnalytics, DemoNotification, DemoTeamWorkload } from './types';

/**
 * The adapter seam.
 *
 * Presentation code calls the hooks in ./hooks.ts, which call these functions. When a
 * real endpoint ships, the body of one function here changes from `fixture(...)` to
 * `api.something(...)` and nothing else in the app moves. That is the whole point of
 * the indirection: there are no `if (demoMode)` branches inside components.
 */

export class DemoUnavailableError extends Error {
  constructor(what: string) {
    super(
      `${what} has no backend endpoint yet and demo mode is off. Set VITE_DEMO_MODE=true to preview it, or wait for the endpoint (see docs/phase7-api-gaps.md).`,
    );
    this.name = 'DemoUnavailableError';
  }
}

const fixture = async <T>(what: string, value: T): Promise<T> => {
  if (!environment.demoMode) throw new DemoUnavailableError(what);
  // A small delay keeps the loading states honest and demonstrable.
  await new Promise((resolve) => setTimeout(resolve, environment.isTest ? 0 : 180));
  return value;
};

export const demoRepository = {
  /** GAP: no /team-lead/workload or capacity endpoint. */
  teamWorkload: (): Promise<DemoTeamWorkload> => fixture('Team workload', demoTeamWorkload),

  /** GAP: no reporting or AI-analytics endpoints (Phase 9). */
  managerAnalytics: (): Promise<DemoManagerAnalytics> =>
    fixture('Manager AI analytics', demoManagerAnalytics),

  /** GAP: no notification module - no endpoint, no table. */
  notifications: (): Promise<DemoNotification[]> => fixture('Notifications', demoNotifications),
} as const;
