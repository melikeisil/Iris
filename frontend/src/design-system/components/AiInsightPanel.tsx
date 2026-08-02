import type { ReactNode } from 'react';
import { Sparkles } from 'lucide-react';
import { AiConfidenceIndicator, Chip } from './badges';
import { cn } from '@/lib/cn';

/**
 * "The Intelligence Feed": violet left accent, Cormorant for the summary sentence,
 * and an unmissable statement that the analysis is advisory. The backend policy engine
 * makes the actual decision - conflating the two is the single most dangerous thing
 * this UI could do.
 */
export interface AiInsightPanelProps {
  title?: string;
  summary?: ReactNode;
  confidence?: number | null;
  provider?: string | null;
  model?: string | null;
  isMock?: boolean;
  isDemoFallback?: boolean;
  children?: ReactNode;
  className?: string;
  /** Shown when the recommendation came back FAILED. */
  errorDetail?: string | null;
}

export const AiInsightPanel = ({
  title = 'Advisory AI analysis',
  summary,
  confidence,
  provider,
  model,
  isMock = false,
  isDemoFallback = false,
  children,
  className,
  errorDetail,
}: AiInsightPanelProps) => (
  <section
    className={cn(
      'rounded-md border border-ai-violet/25 bg-card ai-glow',
      'border-l-2 border-l-ai',
      className,
    )}
    aria-label={title}
  >
    <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border-card px-4 py-2.5">
      <h3 className="flex items-center gap-2 technical-label text-ai-violet">
        <Sparkles aria-hidden className="size-3.5" />
        {title}
      </h3>
      <div className="flex items-center gap-2">
        {isDemoFallback ? <Chip tone="editorial">Demo fallback</Chip> : null}
        {!isDemoFallback && isMock ? <Chip tone="editorial">Mock provider</Chip> : null}
        <AiConfidenceIndicator confidence={confidence ?? null} />
      </div>
    </div>

    <div className="space-y-3 p-4">
      {errorDetail ? (
        <p className="rounded border border-warning-amber/30 bg-warning-amber/[0.07] p-3 text-body-sm text-on-surface-variant">
          Analysis did not complete: {errorDetail}. The work item was still created and
          everything else works.
        </p>
      ) : null}

      {summary ? (
        <p className="font-display-sm font-medium tracking-tight text-[1.0625rem] italic leading-relaxed text-on-surface">
          {summary}
        </p>
      ) : null}

      {children}

      <p className="border-t border-border-card pt-3 text-[0.75rem] text-outline">
        AI recommendation only. Routing and assignment are decided by the deterministic
        backend policy engine.
        {provider ? (
          <span className="font-technical-label text-technical-label text-on-surface-variant ml-2">
            {provider}
            {model ? ` · ${model}` : ''}
          </span>
        ) : null}
      </p>
    </div>
  </section>
);

export const AiFactRow = ({ label, value }: { label: string; value: ReactNode }) => (
  <div className="flex items-baseline justify-between gap-4 border-b border-border-card/60 py-1.5 last:border-0">
    <dt className="technical-label">{label}</dt>
    <dd className="text-right text-body-sm text-on-surface">{value ?? '-'}</dd>
  </div>
);
