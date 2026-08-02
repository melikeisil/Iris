import { Link } from 'react-router-dom';
import type { WorkItemRow } from '@/features/work-items/viewModel';
import { EmptyState } from '@/design-system/components/states';
import { cn } from '@/lib/cn';

/**
 * The dense My Work table, converted from iris_my_work:
 *   thead sticky top-0 bg-[#07090C], header cells font-technical-label text-[10px] uppercase
 *   rows  border-b border-[#171D29] hover:bg-surface-variant/20, cells py-4, body 13px
 *   key   font-technical-sm, indigo when the row is the active one
 *   action a bold indigo verb
 *
 * Below 768px the table becomes stacked cards, per the responsive brief.
 */
const STATUS_TONE = (row: WorkItemRow): string => {
  if (row.blocked) return 'text-error';
  switch (row.statusCategory) {
    case 'IN_PROGRESS':
      return 'text-brand-indigo';
    case 'REVIEW':
      return 'text-ai-violet';
    case 'DONE':
      return 'text-success-teal';
    case 'BLOCKED':
      return 'text-error';
    default:
      return 'text-on-surface-variant';
  }
};

const PRIORITY_TONE: Record<string, string> = {
  CRITICAL: 'text-error',
  HIGH: 'text-error',
  MEDIUM: 'text-warning-amber',
  LOW: 'text-on-surface-variant',
};

const HEADERS = [
  'Key',
  'Title',
  'Type',
  'Source',
  'Status',
  'Priority',
  'Due',
  'Est. effort',
  'Progress',
  'Action',
] as const;

export const MyWorkTable = ({ rows }: { rows: readonly WorkItemRow[] }) => {
  if (rows.length === 0) {
    return (
      <EmptyState
        title="Nothing matches these filters"
        description="Clear a filter, or switch tab to see work you raised or are watching."
      />
    );
  }

  return (
    <>
      <table className="hidden w-full border-collapse text-left md:table">
        <caption className="sr-only">Your work items</caption>
        <thead className="sticky top-0 z-10 bg-background">
          <tr className="border-b border-border-card">
            {HEADERS.map((header) => (
              <th
                key={header}
                scope="col"
                className="py-3 font-technical-label text-[10px] uppercase text-on-surface-variant"
              >
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="text-[13px]">
          {rows.map((row) => (
            <tr
              key={row.id}
              className="border-b border-border-card transition-colors hover:bg-surface-variant/20"
            >
              <td className="py-4 font-technical-sm text-brand-indigo">{row.referenceKey}</td>
              <td className="py-4 font-body-sm text-on-surface-bright">
                <Link to={row.href} className="hover:underline">
                  {row.title}
                </Link>
              </td>
              <td className="py-4 font-body-sm text-on-surface-variant">{row.type}</td>
              <td className="py-4 font-body-sm text-on-surface-variant">{row.source}</td>
              <td className={cn('py-4 font-body-sm', STATUS_TONE(row))}>
                {row.blocked ? '■ Blocked' : row.status}
              </td>
              <td
                className={cn(
                  'py-4 font-body-sm',
                  PRIORITY_TONE[row.priority] ?? 'text-on-surface-variant',
                )}
              >
                {row.priority.charAt(0) + row.priority.slice(1).toLowerCase()}
              </td>
              <td
                className={cn(
                  'py-4 font-body-sm',
                  row.overdue ? 'text-error' : 'text-on-surface-variant',
                )}
              >
                {row.dueLabel}
              </td>
              <td className="py-4 font-technical-sm text-on-surface-variant">
                {row.remainingLabel}
              </td>
              <td className="py-4 font-technical-sm text-on-surface-variant">
                {row.progressLabel}
              </td>
              <td className="py-4">
                <Link to={row.href} className="font-semibold text-brand-indigo hover:underline">
                  {row.actionLabel}
                  <span className="sr-only"> for {row.referenceKey}</span>
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <ul className="space-y-3 md:hidden">
        {rows.map((row) => (
          <li key={row.id}>
            <Link
              to={row.href}
              className="card-glow block rounded-xl border border-border-card bg-card p-4"
            >
              <div className="mb-2 flex items-center justify-between gap-2">
                <span className="font-technical-sm text-brand-indigo">{row.referenceKey}</span>
                <span className={cn('font-body-sm text-xs', STATUS_TONE(row))}>
                  {row.blocked ? '■ Blocked' : row.status}
                </span>
              </div>
              <p className="font-body-sm text-on-surface-bright">{row.title}</p>
              <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 font-technical-sm text-[10px] uppercase text-on-surface-variant">
                <span>{row.type}</span>
                <span>{row.source}</span>
                <span className={PRIORITY_TONE[row.priority]}>{row.priority}</span>
                <span className={row.overdue ? 'text-error' : undefined}>Due {row.dueLabel}</span>
                <span>{row.progressLabel}</span>
              </div>
              <p className="mt-3 text-xs font-semibold text-brand-indigo">{row.actionLabel}</p>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
};
