import { useState } from 'react';
import { ChevronDown, Trophy } from 'lucide-react';
import type { AssignmentCandidate } from '@/api/types';
import { Avatar } from './Avatar';
import { Chip } from './badges';
import { WorkloadBar } from './WorkloadBar';
import { cn } from '@/lib/cn';

/**
 * The deterministic backend ranking, formatted.
 *
 * This component must never compute or adjust a score. Every number here comes
 * verbatim from RoutingController.CandidateResponse; the seven components are the
 * product's explainability story and re-deriving any of them client-side would make
 * the UI disagree with the audit trail.
 */
const COMPONENT_LABELS: Record<keyof AssignmentCandidate['scores'], string> = {
  skill: 'Skill match',
  capacity: 'Capacity',
  team: 'Team fit',
  experience: 'Experience',
  component: 'Component familiarity',
  deadline: 'Deadline fit',
  fairness: 'Fairness',
};

export interface AssignmentCandidateCardProps {
  candidate: AssignmentCandidate;
  /** The backend's top-ranked candidate, or the one the reviewer has chosen. */
  selected?: boolean;
  recommended?: boolean;
  onSelect?: (candidate: AssignmentCandidate) => void;
  className?: string;
}

export const AssignmentCandidateCard = ({
  candidate,
  selected = false,
  recommended = false,
  onSelect,
  className,
}: AssignmentCandidateCardProps) => {
  const [expanded, setExpanded] = useState(false);
  const entries = Object.entries(candidate.scores) as [keyof AssignmentCandidate['scores'], number][];
  const detailId = `candidate-${candidate.candidateUserId}-scores`;

  return (
    <div
      className={cn(
        'rounded-md border bg-card transition-colors',
        selected ? 'border-brand-indigo bg-brand-indigo/[0.07]' : 'border-border-card hover:border-border-structural',
        className,
      )}
      data-testid="assignment-candidate-card"
    >
      <div className="flex items-start gap-3 p-3">
        <Avatar name={candidate.fullName} size="sm" tone={selected ? 'accent' : 'default'} />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="truncate text-body-sm font-semibold text-on-surface">{candidate.fullName}</p>
            {recommended ? (
              <Chip tone="accent">
                <Trophy aria-hidden className="size-3" /> Backend top rank
              </Chip>
            ) : null}
          </div>
          <p className="font-technical-label text-technical-label text-on-surface-variant mt-0.5">
            Rank {candidate.rank} · Score {candidate.totalScore}
          </p>
          {candidate.explanation ? (
            <p className="mt-1.5 text-[0.8125rem] leading-snug text-outline">
              {candidate.explanation}
            </p>
          ) : null}
          <WorkloadBar ratio={candidate.workloadRatio} className="mt-2" compact />
        </div>
        {onSelect ? (
          <button
            type="button"
            onClick={() => onSelect(candidate)}
            aria-pressed={selected}
            className={cn(
              'shrink-0 rounded border px-2 py-1 font-mono text-technical-label uppercase transition-colors',
              selected
                ? 'border-brand-indigo bg-brand-indigo text-white'
                : 'border-border-card text-outline hover:border-brand-indigo hover:text-brand-indigo',
            )}
          >
            {selected ? 'Selected' : 'Select'}
          </button>
        ) : null}
      </div>

      <button
        type="button"
        onClick={() => setExpanded((value) => !value)}
        aria-expanded={expanded}
        aria-controls={detailId}
        className="flex w-full items-center justify-between gap-2 border-t border-border-card px-3 py-2 technical-label hover:text-on-surface"
      >
        Score breakdown
        <ChevronDown aria-hidden className={cn('size-3.5 transition-transform', expanded && 'rotate-180')} />
      </button>

      {expanded ? (
        <dl id={detailId} className="space-y-1 border-t border-border-card px-3 py-2">
          {entries.map(([key, value]) => (
            <div key={key} className="flex items-center justify-between gap-3">
              <dt className="technical-label">{COMPONENT_LABELS[key]}</dt>
              <dd className="flex items-center gap-2">
                <span aria-hidden className="h-1 w-20 overflow-hidden rounded-full bg-surface-container-low">
                  <span
                    className="block h-full rounded-full bg-brand-indigo"
                    style={{ width: `${Math.min(100, Math.max(0, value))}%` }}
                  />
                </span>
                <span className="font-technical-label text-technical-label text-on-surface-variant w-8 text-right">{value}</span>
              </dd>
            </div>
          ))}
        </dl>
      ) : null}
    </div>
  );
};
