/**
 * Typed access to build-time configuration. Read once, never scattered through
 * components as `import.meta.env` lookups.
 */
const bool = (value: string | undefined, fallback: boolean): boolean => {
  if (value === undefined || value === '') return fallback;
  return value === 'true' || value === '1';
};

export const environment = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  /**
   * Demo mode only affects capabilities with no backend endpoint (see
   * docs/phase7-api-gaps.md). Real endpoints are always called for real.
   */
  demoMode: bool(import.meta.env.VITE_DEMO_MODE, true),
  isTest: import.meta.env.MODE === 'test',
  isProduction: import.meta.env.PROD,
} as const;

export type Environment = typeof environment;
