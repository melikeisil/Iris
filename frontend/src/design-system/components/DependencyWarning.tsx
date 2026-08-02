import { Link } from 'react-router-dom';
import { AlertTriangle, Link2Off } from 'lucide-react';
import type { LinkView } from '@/api/types';
import { paths } from '@/app/router/paths';
import { humanizeEnum } from '@/lib/format';
import { cn } from '@/lib/cn';

/** Shown when an item is blocked by, or blocks, other work. */
export const DependencyWarning = ({
  blockers,
  reason,
  className,
}: {
  blockers: readonly LinkView[];
  reason?: string | null;
  className?: string;
}) => {
  if (blockers.length === 0 && !reason) return null;

  return (
    <div
      className={cn('rounded border border-error/35 bg-error/[0.07] p-3', className)}
      role="status"
      data-testid="dependency-warning"
    >
      <p className="flex items-center gap-2 technical-label text-error">
        <AlertTriangle aria-hidden className="size-3.5" />
        Blocked
      </p>
      {reason ? <p className="mt-1.5 text-body-sm text-on-surface-variant">{reason}</p> : null}
      {blockers.length > 0 ? (
        <ul className="mt-2 space-y-1">
          {blockers.map((blocker) => (
            <li key={blocker.linkId} className="flex items-center gap-2 text-body-sm">
              <Link2Off aria-hidden className="size-3.5 shrink-0 text-outline" />
              <Link
                to={paths.workItem(blocker.workItemId)}
                className="font-mono text-technical-label text-brand-indigo hover:underline"
              >
                {blocker.referenceKey}
              </Link>
              <span className="min-w-0 truncate text-on-surface-variant">{blocker.summary}</span>
              <span className="font-technical-label text-technical-label text-on-surface-variant shrink-0">{humanizeEnum(blocker.status)}</span>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
};
