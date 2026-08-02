import { CheckCircle2, GitCommitHorizontal, MessageSquare, Sparkles } from 'lucide-react';
import type { WorkItemRow } from '@/features/work-items/viewModel';
import { EmptyState } from '@/design-system/components/states';
import { formatRelative } from '@/lib/format';

/**
 * The RECENT ACTIVITY column from iris_employee_dashboard_default:
 *   a 32px circular icon chip at 10% tone, the sentence in body-sm, and a tracked-out
 *   uppercase 10px timestamp.
 *
 * There is no activity-feed endpoint. Rather than fabricate commits and pull requests
 * the way the mockup does, this renders the most recently updated work items the caller
 * can actually see - real data, same treatment.
 */
const iconFor = (row: WorkItemRow) => {
  if (row.blocked) return { Icon: MessageSquare, tone: 'bg-error/10 text-error' };
  if (row.statusCategory === 'DONE')
    return { Icon: CheckCircle2, tone: 'bg-success-teal/10 text-success-teal' };
  if (row.statusCategory === 'REVIEW')
    return { Icon: Sparkles, tone: 'bg-secondary/10 text-secondary' };
  return { Icon: GitCommitHorizontal, tone: 'bg-primary/10 text-primary' };
};

export interface ActivityPanelProps {
  rows: readonly WorkItemRow[];
  /** ISO instants, index-aligned with `rows`. */
  updatedAt: readonly string[];
}

export const ActivityPanel = ({ rows, updatedAt }: ActivityPanelProps) => {
  if (rows.length === 0) {
    return (
      <EmptyState title="No recent activity" description="Nothing you can see has changed yet." />
    );
  }

  return (
    <ol className="space-y-6">
      {rows.slice(0, 5).map((row, index) => {
        const { Icon, tone } = iconFor(row);
        return (
          <li key={row.id} className="flex gap-4">
            <span
              aria-hidden
              className={`flex size-8 shrink-0 items-center justify-center rounded-full ${tone}`}
            >
              <Icon className="size-4" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-sm text-on-surface">
                <span className="font-technical-label text-xs">{row.referenceKey}</span> is{' '}
                <span className="font-semibold">{row.status}</span>
              </p>
              <span className="mt-1 block text-[10px] uppercase tracking-wider text-on-surface-variant">
                {formatRelative(updatedAt[index])}
              </span>
            </div>
          </li>
        );
      })}
    </ol>
  );
};
