import { cn } from '@/lib/cn';

/**
 * Capacity utilisation. The ratio comes from the backend's WorkloadCalculator; this
 * only formats it. Band words ("Balanced", "High") are rendered as text so the meaning
 * survives without colour.
 */
export interface WorkloadBarProps {
  /** 0..1 (or 0..100, tolerated). */
  ratio: number | null | undefined;
  label?: string;
  detail?: string;
  className?: string;
  compact?: boolean;
}

export const workloadBand = (percent: number): { label: string; tone: string; bar: string } => {
  if (percent >= 100) return { label: 'Over capacity', tone: 'text-error', bar: 'bg-error' };
  if (percent >= 85) return { label: 'High', tone: 'text-warning-amber', bar: 'bg-warning-amber' };
  if (percent >= 50) return { label: 'Balanced', tone: 'text-success-teal', bar: 'bg-success-teal' };
  return { label: 'Available', tone: 'text-info', bar: 'bg-info' };
};

export const WorkloadBar = ({ ratio, label, detail, className, compact = false }: WorkloadBarProps) => {
  if (ratio === null || ratio === undefined) {
    return <span className={cn('font-technical-label text-technical-label text-on-surface-variant', className)}>Workload unknown</span>;
  }
  const percent = Math.round((ratio > 1.5 ? ratio : ratio * 100));
  const band = workloadBand(percent);

  return (
    <div className={cn('space-y-1', className)} data-testid="workload-bar">
      <div className="flex items-baseline justify-between gap-2">
        <span className="technical-label">{label ?? 'Workload'}</span>
        <span className={cn('font-mono text-technical-label', band.tone)}>
          {percent}% · {band.label}
        </span>
      </div>
      <div
        className={cn('overflow-hidden rounded-full bg-surface-container-low', compact ? 'h-1' : 'h-1.5')}
        role="meter"
        aria-valuenow={percent}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={`${label ?? 'Workload'}: ${percent} percent, ${band.label}`}
      >
        <div
          className={cn('h-full rounded-full', band.bar)}
          style={{ width: `${Math.min(100, percent)}%` }}
        />
      </div>
      {detail ? <p className="text-[0.75rem] text-outline">{detail}</p> : null}
    </div>
  );
};
