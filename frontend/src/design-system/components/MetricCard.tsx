import type { LucideIcon } from 'lucide-react';
import { Link } from 'react-router-dom';
import { cn } from '@/lib/cn';

/**
 * A dashboard summary card.
 *
 * Converted verbatim from the ROW 1 cards in iris_employee_dashboard_default:
 *   bg-surface-container-low border border-[#2A3443] p-5 rounded-xl
 *   label  font-technical-label text-[10px] tracking-widest uppercase (60% alpha)
 *   value  text-3xl font-semibold
 *   caption text-xs
 * The "needs information" card in the source carries `border-l-4 border-l-primary`;
 * that is the `emphasis` tone here.
 */
export type MetricTone = 'default' | 'emphasis' | 'warning' | 'danger' | 'ai';

const TONES: Record<MetricTone, { icon: string; card: string }> = {
  default: { icon: 'text-on-surface-variant', card: '' },
  emphasis: { icon: 'text-primary', card: 'border-l-4 border-l-primary' },
  warning: { icon: 'text-tertiary', card: '' },
  danger: { icon: 'text-error', card: '' },
  ai: { icon: 'text-ai-violet', card: 'border-l-4 border-l-ai-violet' },
};

export interface MetricCardProps {
  label: string;
  value: number | string;
  /** Short noun phrase under the number, e.g. "Work items". */
  caption?: string;
  icon?: LucideIcon;
  tone?: MetricTone;
  /** Makes the whole card a link to a filtered view. */
  to?: string;
  className?: string;
}

export const MetricCard = ({
  label,
  value,
  caption,
  icon: Icon,
  tone = 'default',
  to,
  className,
}: MetricCardProps) => {
  const body = (
    <>
      <div className="mb-4 flex items-center justify-between gap-2">
        <span className="font-technical-label text-[10px] uppercase tracking-widest text-on-surface-variant/60">
          {label}
        </span>
        {Icon ? <Icon aria-hidden className={cn('size-[18px]', TONES[tone].icon)} /> : null}
      </div>
      <div className="flex items-baseline gap-2">
        <span className="text-3xl font-semibold text-on-surface">{value}</span>
        {caption ? <span className="text-xs text-on-surface-variant">{caption}</span> : null}
      </div>
    </>
  );

  const classes = cn(
    'rounded-xl border border-border-structural bg-surface-container-low p-5 transition-colors',
    TONES[tone].card,
    to && 'hover:border-brand-indigo/50',
    className,
  );

  return to ? (
    <Link to={to} className={cn(classes, 'block')}>
      {body}
    </Link>
  ) : (
    <div className={classes}>{body}</div>
  );
};
