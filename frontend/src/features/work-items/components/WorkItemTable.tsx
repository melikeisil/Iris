import { Link } from 'react-router-dom';
import type { WorkItemRow } from '../viewModel';
import { EmptyState } from '@/design-system/components/states';
import { cn } from '@/lib/cn';

/**
 * The compact four-column table used by every dashboard panel.
 *
 * Converted from the `<table>` in iris_employee_dashboard_default:
 *   tbody  divide-y divide-[#2A3443]/30, rows hover:bg-white/5
 *   key    px-6 py-4 font-technical-label text-xs text-on-surface-variant/70
 *   title  px-6 py-4 text-on-surface text-sm font-medium
 *   tag    px-2 py-0.5 rounded text-[10px] font-bold uppercase
 *   action px-6 py-4 text-right, indigo/primary uppercase link
 *
 * Below 768px the rows become stacked cards, since a four-column table is unreadable
 * at that width.
 */
const PRIORITY_TAG: Record<string, string> = {
  CRITICAL: 'bg-error-container/30 text-error',
  HIGH: 'bg-tertiary-container/20 text-tertiary',
  MEDIUM: 'bg-surface-variant/20 text-on-surface-variant',
  LOW: 'bg-surface-variant/20 text-on-surface-variant',
};

const PriorityTag = ({ priority }: { priority: string }) => (
  <span
    className={cn(
      'rounded px-2 py-0.5 text-[10px] font-bold uppercase',
      PRIORITY_TAG[priority] ?? PRIORITY_TAG.MEDIUM,
    )}
  >
    <span className="sr-only">Priority </span>
    {priority.toLowerCase()}
  </span>
);

export interface WorkItemTableProps {
  rows: readonly WorkItemRow[];
  emptyTitle: string;
  emptyDescription?: string;
  /** Accessible caption; visually hidden because the panel already has a heading. */
  caption: string;
}

export const WorkItemTable = ({
  rows,
  emptyTitle,
  emptyDescription,
  caption,
}: WorkItemTableProps) => {
  if (rows.length === 0) {
    return (
      <div className="p-6">
        <EmptyState title={emptyTitle} description={emptyDescription} />
      </div>
    );
  }

  return (
    <>
      {/* Table from 768px up. */}
      <table className="hidden w-full text-left md:table">
        <caption className="sr-only">{caption}</caption>
        <tbody className="divide-y divide-border-structural/30">
          {rows.map((row) => (
            <tr key={row.id} className="hover:bg-white/5">
              <td className="px-6 py-4 font-technical-label text-xs text-on-surface-variant/70">
                {row.referenceKey}
              </td>
              <td className="px-6 py-4 text-sm font-medium text-on-surface">
                <Link to={row.href} className="hover:underline">
                  {row.title}
                </Link>
              </td>
              <td className="px-6 py-4">
                <PriorityTag priority={row.priority} />
              </td>
              <td className="px-6 py-4 text-right">
                <Link
                  to={row.href}
                  className="text-xs font-bold uppercase text-primary hover:underline"
                >
                  {row.actionLabel}
                  <span className="sr-only"> for {row.referenceKey}</span>
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* Stacked cards below 768px. */}
      <ul className="divide-y divide-border-structural/30 md:hidden">
        {rows.map((row) => (
          <li key={row.id} className="px-4 py-4">
            <Link to={row.href} className="block">
              <div className="mb-1 flex items-center justify-between gap-3">
                <span className="font-technical-label text-xs text-on-surface-variant/70">
                  {row.referenceKey}
                </span>
                <PriorityTag priority={row.priority} />
              </div>
              <p className="text-sm font-medium text-on-surface">{row.title}</p>
              <p className="mt-2 text-xs font-bold uppercase text-primary">{row.actionLabel}</p>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
};
