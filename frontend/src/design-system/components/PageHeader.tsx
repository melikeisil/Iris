import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

/**
 * The page introduction block.
 *
 * Converted from the two header treatments in the Stitch sources:
 *   iris_employee_dashboard_default - display serif title, body-lg subtitle, one
 *     outlined action on the right (`flex justify-between items-end mb-10`)
 *   iris_manager_overview - a tracked-out mono eyebrow above the title
 *
 * `italic` on the title is deliberate: Cormorant Garamond italic is the editorial
 * signature of this design, used on every page title in the source.
 */
export interface PageHeaderProps {
  /** Small mono label above the title, e.g. "MANAGER WORKSPACE". */
  eyebrow?: string;
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  /** `lg` is the dashboard's 48px serif; `sm` is the 32px used elsewhere. */
  size?: 'lg' | 'sm';
  className?: string;
}

export const PageHeader = ({
  eyebrow,
  title,
  description,
  actions,
  size = 'lg',
  className,
}: PageHeaderProps) => (
  <header
    className={cn(
      'mb-10 flex flex-col gap-4 md:flex-row md:items-end md:justify-between',
      className,
    )}
  >
    <div className="max-w-2xl">
      {eyebrow ? (
        <span className="mb-2 block font-technical-label text-technical-label uppercase tracking-[0.25em] text-brand-indigo">
          {eyebrow}
        </span>
      ) : null}
      <h1
        className={cn(
          'italic text-on-surface',
          size === 'lg'
            ? 'font-display-lg text-4xl laptop:text-display-lg'
            : 'font-display-sm text-display-sm',
        )}
      >
        {title}
      </h1>
      {description ? (
        <p className="mt-2 font-body-lg text-body-lg leading-relaxed text-on-surface-variant opacity-80">
          {description}
        </p>
      ) : null}
    </div>
    {actions ? <div className="flex flex-wrap items-center gap-3">{actions}</div> : null}
  </header>
);
