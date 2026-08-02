import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';
import { formatRelative } from '@/lib/format';

export interface ActivityEntry {
  id: string;
  icon?: ReactNode;
  actor?: string;
  title: ReactNode;
  detail?: ReactNode;
  timestamp: string;
  tone?: 'default' | 'ai' | 'warning';
}

export const ActivityTimeline = ({
  entries,
  className,
}: {
  entries: readonly ActivityEntry[];
  className?: string;
}) => (
  <ol className={cn('space-y-4', className)}>
    {entries.map((entry) => (
      <li key={entry.id} className="flex gap-3">
        <span
          aria-hidden
          className={cn(
            'mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full border',
            entry.tone === 'ai'
              ? 'border-ai-violet/40 bg-ai-violet/10 text-ai-violet'
              : entry.tone === 'warning'
                ? 'border-warning-amber/40 bg-warning-amber/10 text-warning-amber'
                : 'border-border-card bg-surface-container-low text-outline',
          )}
        >
          {entry.icon}
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-body-sm text-on-surface">
            {entry.actor ? <span className="font-semibold">{entry.actor} </span> : null}
            {entry.title}
          </p>
          {entry.detail ? (
            <div className="mt-1 rounded border border-border-card bg-field px-2.5 py-1.5 text-body-sm italic text-on-surface-variant">
              {entry.detail}
            </div>
          ) : null}
          <time className="font-technical-label text-technical-label text-on-surface-variant mt-1 block" dateTime={entry.timestamp}>
            {formatRelative(entry.timestamp)}
          </time>
        </div>
      </li>
    ))}
  </ol>
);
