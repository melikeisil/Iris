import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

/**
 * The dashboard / overview panel shell.
 *
 * Converted from the repeated wrapper in iris_employee_dashboard_default and
 * iris_manager_overview:
 *   bg-surface-container-low border border-[#2A3443] rounded-xl overflow-hidden
 * with an optional header separated by a 1px rule.
 */
export interface PanelCardProps {
  /** Rendered in Inter 20/600 - the source's `font-headline-md text-lg`. */
  title?: ReactNode;
  /** Rendered as a tracked-out IBM Plex Mono caption instead of a title. */
  technicalTitle?: string;
  /** Right-hand slot in the header, e.g. a count pill or an icon button. */
  headerAction?: ReactNode;
  children: ReactNode;
  /** Padding is off by default so tables can bleed to the card edge. */
  padded?: boolean;
  className?: string;
  bodyClassName?: string;
}

export const PanelCard = ({
  title,
  technicalTitle,
  headerAction,
  children,
  padded = false,
  className,
  bodyClassName,
}: PanelCardProps) => (
  <section
    className={cn(
      'flex flex-col overflow-hidden rounded-xl border border-border-structural bg-surface-container-low',
      className,
    )}
  >
    {title || technicalTitle ? (
      <div
        className={cn(
          'flex items-center justify-between gap-3',
          title ? 'border-b border-border-structural p-6' : 'px-6 pb-0 pt-6',
        )}
      >
        {title ? (
          <h2 className="flex items-center gap-2 font-headline-md text-lg text-on-surface">
            {title}
          </h2>
        ) : (
          <h2 className="font-technical-label text-[11px] uppercase tracking-widest text-on-surface-variant/60">
            {technicalTitle}
          </h2>
        )}
        {headerAction}
      </div>
    ) : null}
    <div className={cn('flex-1', padded && 'p-6', bodyClassName)}>{children}</div>
  </section>
);

/** The small count pill the source puts next to "Today's focus". */
export const CountPill = ({ children }: { children: ReactNode }) => (
  <span className="rounded bg-surface-variant/30 px-2 py-0.5 text-xs font-normal text-on-surface-variant">
    {children}
  </span>
);
