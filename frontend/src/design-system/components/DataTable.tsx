import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';
import { EmptyState, ErrorState, LoadingState } from './states';

/**
 * A small, honest table. It does not sort, page or filter by itself - those are the
 * caller's concern, usually the backend's - it only renders rows and switches between
 * the loading, error and empty states.
 *
 * Below the `laptop` breakpoint it renders each row as a stacked card, which is what
 * keeps dense enterprise tables usable on a phone without a horizontal scrollbar.
 */
export interface Column<T> {
  id: string;
  header: ReactNode;
  cell: (row: T) => ReactNode;
  className?: string;
  headerClassName?: string;
  align?: 'left' | 'right';
}

export interface DataTableProps<T> {
  columns: readonly Column<T>[];
  rows: readonly T[];
  rowKey: (row: T) => string;
  caption?: string;
  isLoading?: boolean;
  error?: unknown;
  onRetry?: () => void;
  emptyTitle?: string;
  emptyDescription?: ReactNode;
  emptyAction?: ReactNode;
  filtered?: boolean;
  onRowClick?: (row: T) => void;
  rowClassName?: (row: T) => string | undefined;
  className?: string;
}

export function DataTable<T>({
  columns,
  rows,
  rowKey,
  caption,
  isLoading = false,
  error,
  onRetry,
  emptyTitle = 'Nothing here yet',
  emptyDescription,
  emptyAction,
  filtered = false,
  onRowClick,
  rowClassName,
  className,
}: DataTableProps<T>) {
  if (isLoading) return <LoadingState rows={5} />;
  if (error) return <ErrorState error={error} onRetry={onRetry} />;
  if (rows.length === 0) {
    return (
      <EmptyState
        title={emptyTitle}
        description={emptyDescription}
        action={emptyAction}
        filtered={filtered}
      />
    );
  }

  return (
    <>
      {/* Table for laptop and up. */}
      <div className={cn('hidden overflow-x-auto laptop:block', className)}>
        <table className="w-full border-collapse text-left">
          {caption ? <caption className="sr-only">{caption}</caption> : null}
          <thead>
            <tr className="border-b border-border-card">
              {columns.map((column) => (
                <th
                  key={column.id}
                  scope="col"
                  className={cn(
                    'whitespace-nowrap px-3 py-2 technical-label',
                    column.align === 'right' && 'text-right',
                    column.headerClassName,
                  )}
                >
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr
                key={rowKey(row)}
                className={cn(
                  'border-b border-border-card/60 transition-colors',
                  onRowClick && 'cursor-pointer hover:bg-field',
                  rowClassName?.(row),
                )}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
              >
                {columns.map((column) => (
                  <td
                    key={column.id}
                    className={cn(
                      'px-3 py-2.5 align-middle text-body-sm text-on-surface-variant',
                      column.align === 'right' && 'text-right',
                      column.className,
                    )}
                  >
                    {column.cell(row)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Stacked cards below laptop. */}
      <ul className={cn('space-y-2 laptop:hidden', className)}>
        {rows.map((row) => (
          <li key={rowKey(row)}>
            <div
              className={cn(
                'rounded-md border border-border-card bg-card p-3',
                onRowClick && 'cursor-pointer hover:border-border-structural',
                rowClassName?.(row),
              )}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              role={onRowClick ? 'button' : undefined}
              tabIndex={onRowClick ? 0 : undefined}
              onKeyDown={
                onRowClick
                  ? (event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        onRowClick(row);
                      }
                    }
                  : undefined
              }
            >
              <dl className="space-y-1.5">
                {columns.map((column) => (
                  <div key={column.id} className="flex items-start justify-between gap-3">
                    <dt className="technical-label shrink-0 pt-0.5">{column.header}</dt>
                    <dd className="min-w-0 text-right text-body-sm text-on-surface-variant">
                      {column.cell(row)}
                    </dd>
                  </div>
                ))}
              </dl>
            </div>
          </li>
        ))}
      </ul>
    </>
  );
}
