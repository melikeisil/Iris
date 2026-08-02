import { useState } from 'react';
import { Info } from 'lucide-react';
import type { AvailableTransition } from '@/api/types';
import { Button } from './Button';
import { cn } from '@/lib/cn';

/**
 * Renders exactly the transitions the backend returned for this caller and this state.
 *
 * Transitions the backend marked `enabled: false` are shown disabled with their
 * `blockedReasons`, rather than hidden - "why can't I submit this?" is a question the
 * UI should answer, and the backend already answered it. Hiding a button is never
 * security: the backend re-checks on every POST.
 */
export interface WorkflowActionsProps {
  transitions: readonly AvailableTransition[];
  onTransition: (transition: AvailableTransition) => void;
  pendingCode?: string | null;
  disabled?: boolean;
  className?: string;
  /** Render the first enabled transition as the primary action. */
  emphasiseFirst?: boolean;
}

export const WorkflowActions = ({
  transitions,
  onTransition,
  pendingCode,
  disabled = false,
  className,
  emphasiseFirst = true,
}: WorkflowActionsProps) => {
  const [expandedCode, setExpandedCode] = useState<string | null>(null);

  if (transitions.length === 0) {
    return (
      <p className={cn('text-body-sm text-outline', className)} data-testid="no-transitions">
        No workflow actions are available to you in this state.
      </p>
    );
  }

  let primaryUsed = false;

  return (
    <div className={cn('flex flex-wrap items-center gap-2', className)} data-testid="workflow-actions">
      {transitions.map((transition) => {
        const isPrimary = emphasiseFirst && transition.enabled && !primaryUsed;
        if (isPrimary) primaryUsed = true;
        const reasons = transition.blockedReasons ?? [];

        return (
          <div key={transition.code} className="flex items-center gap-1">
            <Button
              variant={isPrimary ? 'primary' : 'secondary'}
              size="sm"
              disabled={disabled || !transition.enabled}
              loading={pendingCode === transition.code}
              onClick={() => onTransition(transition)}
              title={
                transition.enabled
                  ? `Moves this item to ${transition.toState}`
                  : reasons.join(' ') || 'Not available in this state'
              }
            >
              {transition.label}
            </Button>
            {!transition.enabled && reasons.length > 0 ? (
              <button
                type="button"
                aria-label={`Why is ${transition.label} unavailable?`}
                aria-expanded={expandedCode === transition.code}
                onClick={() =>
                  setExpandedCode((current) => (current === transition.code ? null : transition.code))
                }
                className="rounded p-1 text-outline hover:text-on-surface"
              >
                <Info aria-hidden className="size-3.5" />
              </button>
            ) : null}
            {expandedCode === transition.code ? (
              <ul className="rounded border border-border-card bg-field px-2 py-1 text-[0.75rem] text-outline">
                {reasons.map((reason) => (
                  <li key={reason}>{reason}</li>
                ))}
              </ul>
            ) : null}
          </div>
        );
      })}
    </div>
  );
};
