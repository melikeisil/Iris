import { AlertTriangle } from 'lucide-react';
import { EmptyState, LoadingState } from '@/design-system/components/states';
import { useAuth } from '@/app/providers/AuthProvider';
import { useTeamWorkloadReport } from '../hooks/useReports';
import { cn } from '@/lib/cn';

/**
 * Team workload and overloaded employees.
 *
 * Bar treatment converted from the "Cross-team delivery" block in
 * iris_manager_overview: a 1.5px track at bg-surface-container with an indigo fill.
 */
export const TeamWorkloadPanel = () => {
  const { user } = useAuth();
  const teamId = user?.teams[0]?.id ?? null;
  const { data, isLoading, isError } = useTeamWorkloadReport(teamId);

  if (!user) return <LoadingState label="Loading team workload" variant="inline" />;

  if (isError) {
    return (
      <div className="p-6">
        <EmptyState
          compact
          title="Team workload unavailable"
          description="The workload report could not be loaded for your current team."
        />
      </div>
    );
  }

  if (isLoading || !data) return <LoadingState label="Loading team workload" variant="inline" />;

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center justify-between gap-2">
        <h2 className="font-technical-label text-[11px] uppercase tracking-widest text-on-surface-variant/60">
          Team workload
        </h2>
        <span className="text-[10px] text-on-surface-variant">{data.teamName}</span>
      </div>

      <ul className="space-y-5">
        {data.members.map((member) => {
          const ratio = member.capacityHours > 0 ? member.assignedHours / member.capacityHours : 0;
          const percent = Math.min(150, Math.round(ratio * 100));
          const overloaded = ratio > 1;
          return (
            <li key={member.userId} className="space-y-2">
              <div className="flex items-center justify-between gap-2 text-xs">
                <span className="truncate font-bold text-on-surface">{member.fullName}</span>
                <span
                  className={cn(
                    'flex items-center gap-1 font-technical-label',
                    overloaded ? 'text-error' : 'text-on-surface-variant',
                  )}
                >
                  {overloaded ? <AlertTriangle aria-hidden className="size-3" /> : null}
                  {percent}%
                </span>
              </div>
              <div className="h-1.5 w-full overflow-hidden rounded-full bg-surface-container">
                <div
                  className={cn('h-full', overloaded ? 'bg-error' : 'bg-brand-indigo')}
                  /* A percentage width is genuinely dynamic; there is no class for it. */
                  style={{ width: `${Math.min(100, percent)}%` }}
                />
              </div>
              <div className="flex justify-between text-[10px] text-on-surface-variant">
                <span>
                  {member.assignedHours}h assigned / {member.capacityHours}h capacity
                </span>
                <span>
                  {member.openItems} open
                  {member.blockedItems > 0 ? ` · ${member.blockedItems} blocked` : ''}
                </span>
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
};
