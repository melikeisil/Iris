import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

/**
 * The work-item detail card, converted from iris_work_item_detail:
 *   p-6 bg-[#0c0f14] border border-[#171d29] rounded-xl
 * with a tracked-out IBM Plex Mono caption as its heading. `ai` adds the source's
 * `.intelligence-glow` (violet inner shadow plus a 3px violet left border).
 */
export const DetailCard = ({
  title,
  action,
  children,
  variant = 'default',
  className,
}: {
  title: string;
  action?: ReactNode;
  children: ReactNode;
  variant?: 'default' | 'ai';
  className?: string;
}) => (
  <section
    className={cn(
      'space-y-4 rounded-xl border border-border-card bg-card p-6',
      variant === 'ai' && 'intelligence-glow',
      className,
    )}
  >
    <div className="flex items-start justify-between gap-3">
      <h2
        className={cn(
          'font-technical-label text-xs uppercase tracking-widest',
          variant === 'ai' ? 'text-ai-violet' : 'text-on-surface-variant',
        )}
      >
        {title}
      </h2>
      {action}
    </div>
    {children}
  </section>
);

/** The label/value rows in the Metadata panel. */
export const MetaRow = ({ label, children }: { label: string; children: ReactNode }) => (
  <div className="flex items-center justify-between gap-4 text-sm">
    <span className="font-technical-label text-[10px] uppercase text-on-surface-variant">
      {label}
    </span>
    <span className="text-right">{children}</span>
  </div>
);
