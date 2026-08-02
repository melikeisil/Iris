import { Brain } from 'lucide-react';
import { cn } from '@/lib/cn';

/**
 * The sticky right-hand summary from iris_create_ticket:
 *   bg-surface-variant border border-outline rounded-xl p-6 sticky top-24, with the
 *   violet `.ai-glow` inner shadow and a two-column label/value list in IBM Plex Mono.
 */
const Row = ({ label, value, tone }: { label: string; value: string; tone?: 'muted' }) => (
  <div className="flex justify-between gap-4">
    <dt className="font-technical-label text-technical-label uppercase text-on-surface-variant">
      {label}
    </dt>
    <dd
      className={cn(
        'max-w-[180px] text-right font-body-sm text-body-sm',
        tone === 'muted' ? 'text-on-surface-variant' : 'text-on-surface',
      )}
    >
      {value}
    </dd>
  </div>
);

export interface RequestSummaryPanelProps {
  title: string;
  ticketType: string;
  currentStep: string;
  savedState: string;
  analysisState: string;
  policyRoute: string;
}

export const RequestSummaryPanel = ({
  title,
  ticketType,
  currentStep,
  savedState,
  analysisState,
  policyRoute,
}: RequestSummaryPanelProps) => (
  <aside className="ai-glow sticky top-24 space-y-6 rounded-xl border border-outline-variant bg-surface-variant p-6">
    <div className="flex items-center justify-between gap-2">
      <h2 className="font-headline-md text-headline-md text-on-surface">Request summary</h2>
      <span className="rounded border border-primary/20 bg-primary/10 px-2 py-0.5 font-technical-label text-[10px] uppercase text-primary">
        Draft
      </span>
    </div>

    <dl className="space-y-3 pt-2">
      <Row label="Title" value={title} />
      <Row label="Type" value={ticketType} />
      <Row label="Current step" value={currentStep} />
      <Row label="Saved state" value={savedState} tone="muted" />
    </dl>

    <div className="space-y-3 border-t border-outline-variant pt-6">
      <div className="mb-2 flex items-center gap-2">
        <Brain aria-hidden className="size-4 text-ai-violet" />
        <span className="font-technical-label text-technical-label uppercase text-ai-violet">
          Iris AI status
        </span>
      </div>
      <dl className="space-y-3">
        <Row label="Analysis" value={analysisState} tone="muted" />
        <Row label="Policy route" value={policyRoute} tone="muted" />
      </dl>
    </div>

    <p className="border-t border-outline-variant/30 pt-4 text-[10px] italic text-on-surface-variant">
      AI analysis is advisory. Final routing is determined by backend policy.
    </p>
  </aside>
);
