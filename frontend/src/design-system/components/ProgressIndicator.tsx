import { cn } from '@/lib/cn';
import { formatPercent } from '@/lib/format';

export interface ProgressIndicatorProps {
  /** 0..100, as returned by the backend's ProgressCalculator. */
  percent: number | null | undefined;
  label?: string;
  strategy?: string | null;
  className?: string;
  size?: 'sm' | 'md';
}

export const ProgressIndicator = ({
  percent,
  label = 'Progress',
  strategy,
  className,
  size = 'md',
}: ProgressIndicatorProps) => {
  const value = percent === null || percent === undefined ? null : Math.round(Number(percent));

  return (
    <div className={cn('space-y-1', className)}>
      <div className="flex items-baseline justify-between gap-2">
        <span className="technical-label">{label}</span>
        <span className="font-technical-label text-technical-label text-on-surface-variant">{formatPercent(value)}</span>
      </div>
      <div
        className={cn('overflow-hidden rounded-full bg-surface-container-low', size === 'sm' ? 'h-1' : 'h-1.5')}
        role="progressbar"
        aria-valuenow={value ?? undefined}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={label}
      >
        <div className="h-full rounded-full bg-brand-indigo" style={{ width: `${value ?? 0}%` }} />
      </div>
      {strategy ? (
        <p className="text-[0.75rem] text-outline">
          Rolled up from child work ({strategy.toLowerCase().replaceAll('_', ' ')})
        </p>
      ) : null}
    </div>
  );
};
