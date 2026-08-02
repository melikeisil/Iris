import { EmptyState, LoadingState } from '@/design-system/components/states';
import { useAuth } from '@/app/providers/AuthProvider';
import { useAiAnalyticsReport, useDepartmentCapacityReport } from '@/features/dashboard/hooks/useReports';
import { cn } from '@/lib/cn';

/**
 * The two right-hand panels from iris_manager_overview: "Cross-team delivery"
 * (a labelled 1.5px progress track per team) and "Risk and escalation trends"
 * (a row of 4px bars per band).
 *
 * Real reporting panels backed by Phase 9 endpoints.
 */
const RISK_LABEL: Record<string, string> = {
  high: 'HIGH RISK',
  medium: 'MED RISK',
  low: 'LOW RISK',
};

/** Shared "no endpoint, demo mode off" state, in the same card shell as the real panels. */
const PanelUnavailable = ({ title, description }: { title: string; description: string }) => (
  <section className="space-y-4 rounded-xl border border-outline-variant bg-surface-container-low p-6">
    <h2 className="font-headline-md text-sm font-bold uppercase tracking-wider">{title}</h2>
    <EmptyState compact title="Not available" description={description} />
  </section>
);

export const CrossTeamDeliveryPanel = () => {
  const { user } = useAuth();
  const { data, isLoading, isError } = useDepartmentCapacityReport(user?.department?.id ?? null);

  if (!user) return <LoadingState label="Loading delivery view" variant="inline" />;

  if (isError) {
    return (
      <PanelUnavailable
        title="Cross-team delivery"
        description="The department capacity report is unavailable right now."
      />
    );
  }

  if (isLoading || !data) return <LoadingState label="Loading delivery view" variant="inline" />;

  const teams = data.teams.map((team) => ({
    name: team.teamName,
    effort: team.assignedHours,
    capacity: team.capacityHours,
    blocked: team.blockedItems,
    ratio: team.utilizationRatio,
  }));

  return (
    <section className="space-y-6 rounded-xl border border-outline-variant bg-surface-container-low p-6">
      <div className="flex items-center justify-between gap-2">
        <h2 className="font-headline-md text-sm font-bold uppercase tracking-wider">
          Cross-team delivery
        </h2>
        <span className="text-[10px] text-on-surface-variant">{data.departmentName}</span>
      </div>

      <ul className="space-y-6">
        {teams.map((team) => {
          const band = team.ratio > 0.95 ? 'high' : team.ratio > 0.75 ? 'medium' : 'low';
          const percent = Math.min(100, Math.round(team.ratio * 100));
          return (
            <li key={team.name} className="space-y-2">
              <div className="mb-1 flex justify-between text-xs">
                <span className="font-bold">{team.name}</span>
                <span
                  className={cn(
                    'font-bold',
                    band === 'high' && 'text-error',
                    band === 'medium' && 'text-tertiary',
                    band === 'low' && 'text-on-surface-variant',
                  )}
                >
                  {RISK_LABEL[band]}
                </span>
              </div>
              <div className="h-1.5 w-full overflow-hidden rounded-full bg-surface-container">
                <div
                  className={cn('h-full', band === 'high' ? 'bg-error' : 'bg-brand-indigo')}
                  style={{ width: `${percent}%` }}
                />
              </div>
              <div className="flex justify-between text-[10px] text-on-surface-variant">
                <span>
                  Effort: {team.effort}h / Cap: {team.capacity}h
                </span>
                <span>{team.blocked} blocked</span>
              </div>
            </li>
          );
        })}
      </ul>
    </section>
  );
};

export const AiAnalyticsPanel = () => {
  const { data, isLoading, isError } = useAiAnalyticsReport();

  if (isError) {
    return (
      <PanelUnavailable
        title="AI and delivery analytics"
        description="The AI analytics report is unavailable right now."
      />
    );
  }

  if (isLoading || !data) return <LoadingState label="Loading AI analytics" variant="inline" />;

  const bands: readonly { label: string; value: number; tone: string }[] = [
    { label: 'AI acceptance', value: Math.round(data.aiAcceptanceRate * 100), tone: 'bg-ai-violet' },
    { label: 'AI override', value: Math.round(data.aiOverrideRate * 100), tone: 'bg-tertiary' },
    {
      label: 'Auto-assigned',
      value: Math.round(data.autoAssignedShare * 100),
      tone: 'bg-brand-indigo',
    },
    {
      label: 'Planned work',
      value: Math.round(
        (data.plannedVsUnplanned.planned /
          Math.max(1, data.plannedVsUnplanned.planned + data.plannedVsUnplanned.unplanned)) *
          100,
      ),
      tone: 'bg-outline',
    },
  ];

  return (
    <section className="rounded-xl border border-outline-variant bg-surface-container-low p-6">
      <div className="mb-6 flex items-center justify-between gap-2">
        <h2 className="font-headline-md text-sm font-bold uppercase tracking-wider">
          AI and delivery analytics
        </h2>
        <span className="text-[10px] text-on-surface-variant">Last 30 days</span>
      </div>

      <ul className="space-y-4">
        {bands.map((band) => (
          <li key={band.label} className="flex items-center gap-4">
            <span className="w-28 font-technical-label text-xs">{band.label}</span>
            <span className="flex h-4 flex-1 items-end gap-1">
              <span className="h-full flex-1 overflow-hidden rounded-sm bg-surface-container">
                <span
                  className={cn('block h-full rounded-sm', band.tone)}
                  style={{ width: `${band.value}%` }}
                />
              </span>
              <span className="ml-2 text-xs font-bold">{band.value}%</span>
            </span>
          </li>
        ))}
      </ul>

      <p className="mt-4 text-[10px] italic text-on-surface-variant">
        Average decision latency {data.averageDecisionLatencyMinutes} minutes ·{' '}
        {data.policyExceptionsLast30Days} policy exceptions in the last 30 days.
      </p>
    </section>
  );
};
