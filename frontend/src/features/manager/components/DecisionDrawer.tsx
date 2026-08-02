import { useState } from 'react';
import { Sparkles, ShieldCheck, X } from 'lucide-react';
import type { ApprovalRequest, WorkItemDetail } from '@/api/types';
import { Button } from '@/design-system/components/Button';
import { TextArea } from '@/design-system/components/fields';
import { CheckboxField } from '@/design-system/components/fields';
import { AiConfidenceIndicator } from '@/design-system/components/badges';
import { IconButton } from '@/design-system/components/IconButton';
import { humanizeEnum } from '@/lib/format';

/**
 * The right-hand decision drawer from iris_manager_overview:
 *   fixed right-0 w-[440px] .glass-panel border-l, a violet "advisory AI" block with
 *   the source's `.iris-ai-border` 3px left rule, an amber "backend policy decision"
 *   block listing the triggered rules, and a footer with a required decision note.
 *
 * The mandatory-controls checklist in the mockup is rendered from the approval's own
 * `reasons`, which is the only list the backend actually produces. The acknowledgement
 * checkbox is a local confirmation gate, not a persisted field - it is labelled as such.
 */
export interface DecisionDrawerProps {
  approval: ApprovalRequest;
  workItem: WorkItemDetail | undefined;
  aiConfidence: number | null;
  onClose: () => void;
  onApprove: (note: string) => void;
  onReject: (note: string) => void;
  pending: boolean;
}

export const DecisionDrawer = ({
  approval,
  workItem,
  aiConfidence,
  onClose,
  onApprove,
  onReject,
  pending,
}: DecisionDrawerProps) => {
  const [note, setNote] = useState('');
  const [acknowledged, setAcknowledged] = useState(false);
  const noteTooShort = note.trim().length < 10;

  return (
    <aside
      role="complementary"
      aria-label="Decision detail"
      className="glass-panel flex w-full shrink-0 flex-col border-l border-outline-variant laptop:w-[440px]"
    >
      <div className="flex items-start justify-between gap-3 border-b border-outline-variant p-6">
        <div>
          <div className="mb-1 flex items-center gap-2">
            <span className="font-technical-label text-technical-label text-brand-indigo">
              {workItem?.referenceKey ?? '—'}
            </span>
            <span className="text-xs text-on-surface-variant">/</span>
            <span className="text-xs text-on-surface-variant">{humanizeEnum(approval.route)}</span>
          </div>
          <h2 className="font-display-sm text-2xl italic">
            {workItem?.summary ?? 'Loading work item…'}
          </h2>
        </div>
        <IconButton label="Close decision detail" icon={<X className="size-4" />} onClick={onClose} />
      </div>

      <div className="flex-1 space-y-8 overflow-y-auto p-6">
        <dl className="grid grid-cols-2 gap-4">
          {(
            [
              ['Priority', workItem ? humanizeEnum(workItem.priority) : '—'],
              ['Risk', workItem?.riskLevel ? humanizeEnum(workItem.riskLevel) : 'Not assessed'],
              ['Status', workItem ? humanizeEnum(workItem.status) : '—'],
              ['Waiting since', new Date(approval.createdAt).toLocaleDateString()],
            ] as const
          ).map(([label, value]) => (
            <div key={label} className="space-y-1">
              <dt className="font-technical-label text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">
                {label}
              </dt>
              <dd className="text-sm font-semibold text-on-surface">{value}</dd>
            </div>
          ))}
        </dl>

        <section className="space-y-3 rounded-xl border border-ai-violet/20 border-l-[3px] border-l-ai-violet bg-ai-violet/5 p-4">
          <div className="flex items-center justify-between gap-2">
            <span className="flex items-center gap-2 font-technical-label text-[10px] font-bold uppercase tracking-widest text-ai-violet">
              <Sparkles aria-hidden className="size-3.5" />
              Advisory AI analysis
            </span>
          </div>
          <AiConfidenceIndicator confidence={aiConfidence} />
          <p className="text-xs text-on-surface-variant">
            AI recommendation only. It does not decide the route.
          </p>
        </section>

        <section className="space-y-2 rounded-xl border border-tertiary/20 border-l-[3px] border-l-tertiary bg-tertiary/5 p-4">
          <span className="flex items-center gap-2 font-technical-label text-[10px] font-bold uppercase tracking-widest text-tertiary">
            <ShieldCheck aria-hidden className="size-3.5" />
            Backend policy decision
          </span>
          <p className="text-sm font-bold text-on-surface">{approval.route}</p>
          <p className="text-[10px] italic text-on-surface-variant">
            Deterministic backend policy decision.
          </p>
          {approval.reasons.length > 0 ? (
            <div className="space-y-1 pt-1">
              <p className="font-technical-label text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">
                Triggered rules
              </p>
              <ul className="list-inside list-disc space-y-0.5 text-[11px] text-on-surface">
                {approval.reasons.map((reason) => (
                  <li key={reason.code}>{reason.meaning}</li>
                ))}
              </ul>
            </div>
          ) : null}
        </section>
      </div>

      <div className="space-y-4 border-t border-outline-variant bg-surface-container-low p-6">
        <TextArea
          label={
            <span className="flex w-full items-center justify-between gap-2">
              <span>Decision note</span>
              <span className="text-[10px] font-bold uppercase tracking-widest text-error">
                Required
              </span>
            </span>
          }
          rows={3}
          required
          value={note}
          onChange={(event) => setNote(event.target.value)}
          placeholder="Explain the reason and controls for this decision."
          error={
            note.length > 0 && noteTooShort ? 'Give at least 10 characters of context.' : undefined
          }
        />

        <CheckboxField
          label="I have reviewed the risks and controls for this decision."
          hint="A local confirmation gate. The backend records only your decision note."
          checked={acknowledged}
          onChange={(event) => setAcknowledged(event.target.checked)}
        />

        <div className="grid grid-cols-2 gap-3">
          <Button
            size="lg"
            loading={pending}
            disabled={noteTooShort || !acknowledged}
            onClick={() => onApprove(note.trim())}
          >
            Approve decision
          </Button>
          <Button
            variant="secondary"
            size="lg"
            className="border-error/40 text-error hover:bg-error/10 hover:text-error"
            disabled={noteTooShort || pending}
            onClick={() => onReject(note.trim())}
          >
            Reject decision
          </Button>
        </div>
        <p className="text-[10px] text-on-surface-variant">
          The backend validates and commits this decision before the workflow continues.
        </p>
      </div>
    </aside>
  );
};
