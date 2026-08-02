import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Sparkles, ShieldCheck, Hourglass } from 'lucide-react';
import type { ApprovalRequest } from '@/api/types';
import { Button } from '@/design-system/components/Button';
import { Modal } from '@/design-system/components/Modal';
import { TextArea } from '@/design-system/components/fields';
import { EmptyState, ErrorState, LoadingState } from '@/design-system/components/states';
import {
  AiConfidenceIndicator,
  DemoDataBadge,
  PolicyDecisionBadge,
  RiskBadge,
} from '@/design-system/components/badges';
import { AssignmentCandidateCard } from '@/design-system/components/AssignmentCandidateCard';
import {
  useApproveRecommendation,
  useEscalateRecommendation,
  useRejectRecommendation,
  useTeamReviewQueue,
} from './hooks/useReviewQueue';
import { useAssignmentCandidates } from './hooks/useAssignmentCandidates';
import { useWorkItem } from '@/features/work-items/hooks/useWorkItems';
import { useLatestAnalysis } from '@/features/work-items/hooks/useAnalysis';
import { useToast } from '@/app/providers/ToastProvider';
import { paths } from '@/app/router/paths';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { formatRelative, humanizeEnum } from '@/lib/format';
import { cn } from '@/lib/cn';

/**
 * The AI review queue, converted from iris_team_lead_review.
 *
 * Three-pane layout preserved from the source: a 320px queue on the left
 * (`w-80 border-r bg-surface-container-lowest`, cards at `p-4 rounded-xl`), the
 * selected item in the centre with the source's three-card "decision chain" (advisory
 * AI / backend policy / final decision), and the 340px backend-ranked candidate rail on
 * the right.
 *
 * Every decision is a real endpoint: GET /team-lead/review-queue,
 * POST /approvals/{id}/approve | reject | escalate.
 *
 * The source's editable "Decomposition Review" list is not implemented: the only way to
 * change a plan is POST /work-items/{id}/apply-plan with a template, which is a
 * different operation from editing line-item estimates, and there is no endpoint that
 * persists an edited decomposition.
 *
 * Below 1024px the three panes stack; the queue becomes a horizontal scroller.
 */
type Dialog = 'reject' | 'escalate' | null;

const DecisionCard = ({
  title,
  tone,
  value,
  caption,
  icon: Icon,
  children,
}: {
  title: string;
  tone: 'ai' | 'policy' | 'pending';
  value?: string;
  caption: string;
  icon: typeof Sparkles;
  children?: ReactNode;
}) => (
  <div
    className={cn(
      'rounded-xl border bg-card p-5',
      tone === 'ai' && 'intelligence-glow border-ai-violet/30',
      tone === 'policy' && 'border-tertiary/30',
      tone === 'pending' && 'border-outline-variant',
    )}
  >
    <div className="mb-3 flex items-center gap-2">
      <Icon
        aria-hidden
        className={cn(
          'size-5',
          tone === 'ai' && 'text-ai-violet',
          tone === 'policy' && 'text-tertiary',
          tone === 'pending' && 'text-outline',
        )}
      />
      <h3
        className={cn(
          'font-technical-label text-technical-label uppercase',
          tone === 'ai' && 'text-ai-violet',
          tone === 'policy' && 'text-tertiary',
          tone === 'pending' && 'text-outline',
        )}
      >
        {title}
      </h3>
    </div>
    {value ? <p className="py-2 font-technical-label text-body-sm text-on-surface">{value}</p> : null}
    {children}
    <p className="mt-2 font-technical-label text-[10px] uppercase tracking-wider text-outline">
      {caption}
    </p>
  </div>
);

const ApprovalQueueCard = ({
  approval,
  active,
  onClick,
}: {
  approval: ApprovalRequest;
  active: boolean;
  onClick: () => void;
}) => {
  const { data: item } = useWorkItem(approval.workItemId);

  return (
    <li className="w-64 shrink-0 laptop:w-auto">
      <button
        type="button"
        onClick={onClick}
        aria-current={active ? 'true' : undefined}
        className={cn(
          'w-full rounded-xl border p-4 text-left transition-all',
          active
            ? 'border-primary bg-primary/10'
            : 'border-outline-variant bg-card hover:bg-surface-variant/30',
        )}
      >
        <div className="mb-2 flex items-start justify-between gap-2">
          <div className="flex items-center gap-1.5 font-technical-label text-technical-label">
            {item?.referenceKey ? (
              <span className="font-bold text-primary">{item.referenceKey}</span>
            ) : null}
            <span className={active ? 'text-primary' : 'text-outline'}>
              {approval.route}
            </span>
          </div>
          <span className="font-technical-label text-[10px] text-on-surface-variant shrink-0">
            {formatRelative(approval.createdAt)}
          </span>
        </div>

        <p className="line-clamp-2 font-display-sm text-sm font-semibold italic text-on-surface">
          {item?.summary ?? approval.reasons[0]?.meaning ?? humanizeEnum(approval.status)}
        </p>

        {item?.summary && approval.reasons[0]?.meaning ? (
          <p className="mt-1 line-clamp-1 font-body-sm text-xs text-on-surface-variant/70">
            {approval.reasons[0].meaning}
          </p>
        ) : null}

        <div className="mt-3 flex flex-wrap gap-2">
          {approval.overdue ? (
            <span className="rounded border border-error/30 bg-error-container/20 px-2 py-0.5 font-technical-label text-[10px] uppercase text-error">
              Overdue
            </span>
          ) : null}
          {approval.policyException ? (
            <span className="rounded border border-editorial/30 bg-editorial/10 px-2 py-0.5 font-technical-label text-[10px] uppercase text-editorial">
              Policy exception
            </span>
          ) : null}
        </div>
      </button>
    </li>
  );
};

export const AiReviewPage = () => {
  const [searchParams] = useSearchParams();
  const { notify } = useToast();
  const queue = useTeamReviewQueue();
  const [selectedId, setSelectedId] = useState<string | null>(searchParams.get('approval'));
  const [dialog, setDialog] = useState<Dialog>(null);
  const [reason, setReason] = useState('');
  useDocumentTitle('AI review');

  const approvals = useMemo(() => queue.data ?? [], [queue.data]);

  useEffect(() => {
    if (approvals.length === 0) {
      if (selectedId !== null) setSelectedId(null);
      return;
    }
    if (selectedId === null || !approvals.some((approval) => approval.id === selectedId)) {
      setSelectedId(approvals[0]?.id ?? null);
    }
  }, [approvals, selectedId]);

  const selected: ApprovalRequest | null =
    approvals.find((approval) => approval.id === selectedId) ?? null;

  const workItem = useWorkItem(selected?.workItemId);
  const analysis = useLatestAnalysis(selected?.workItemId);
  const ranking = useAssignmentCandidates(selected?.workItemId ?? null);

  const approve = useApproveRecommendation(selected?.workItemId);
  const reject = useRejectRecommendation(selected?.workItemId);
  const escalate = useEscalateRecommendation(selected?.workItemId);

  if (queue.isLoading) return <LoadingState variant="page" label="Loading the review queue" />;
  if (queue.error) return <ErrorState error={queue.error} />;

  const closeDialog = () => {
    setDialog(null);
    setReason('');
  };

  return (
    <div className="flex min-h-[calc(100vh-4rem)] flex-col laptop:flex-row">
      {/* LEFT - the queue */}
      <section className="shrink-0 border-b border-outline-variant bg-surface-container-lowest laptop:w-80 laptop:border-b-0 laptop:border-r">
        <div className="border-b border-outline-variant p-6">
          <h1 className="font-display-sm text-display-sm italic text-on-surface">Review queue</h1>
          <p className="mt-1 font-body-sm text-body-sm text-on-surface-variant">
            Work requiring technical or policy validation.
          </p>
          <p className="mt-4 inline-flex items-center rounded border border-outline-variant bg-surface-variant px-2 py-1">
            <span className="font-technical-label text-technical-label text-primary">
              {approvals.length} item{approvals.length === 1 ? '' : 's'} require review
            </span>
          </p>
        </div>

        {approvals.length === 0 ? (
          <div className="p-6">
            <EmptyState
              title="The queue is clear"
              description="Nothing is waiting on a technical or policy decision from you."
            />
          </div>
        ) : (
          <ul className="flex gap-3 overflow-x-auto p-4 laptop:max-h-[calc(100vh-16rem)] laptop:flex-col laptop:overflow-y-auto">
            {approvals.map((approval) => (
              <ApprovalQueueCard
                key={approval.id}
                approval={approval}
                active={approval.id === selectedId}
                onClick={() => setSelectedId(approval.id)}
              />
            ))}
          </ul>
        )}
      </section>

      {/* CENTRE - the selected item */}
      <section className="flex-1 overflow-y-auto bg-background">
        {selected === null ? (
          <div className="p-8">
            <EmptyState title="Select an item" description="Pick a queue entry to review it." />
          </div>
        ) : (
          <div className="mx-auto max-w-4xl p-6 laptop:p-8">
            <div className="mb-10">
              <div className="mb-4 flex flex-wrap items-center gap-3">
                <span className="rounded border border-brand-indigo/30 bg-brand-indigo/20 px-3 py-1 font-technical-label text-technical-label text-brand-indigo">
                  {workItem.data?.referenceKey ?? 'Loading…'}
                </span>
                {workItem.data ? (
                  <span className="text-body-sm text-on-surface-variant">
                    • {humanizeEnum(workItem.data.workItemType)}
                  </span>
                ) : null}
              </div>
              <h2 className="mb-2 font-display-sm text-[40px] italic leading-tight text-on-surface">
                {workItem.data?.summary ?? 'Loading work item…'}
              </h2>
              {workItem.data?.description ? (
                <p className="max-w-2xl text-body-lg text-on-surface-variant">
                  {workItem.data.description}
                </p>
              ) : null}
            </div>

            {/* Decision chain */}
            <div className="mb-10 grid grid-cols-1 gap-4 md:grid-cols-3">
              <DecisionCard
                title="Advisory AI analysis"
                tone="ai"
                caption="AI recommendation only."
                icon={Sparkles}
              >
                <AiConfidenceIndicator confidence={analysis.view?.confidence ?? null} />
              </DecisionCard>
              <DecisionCard
                title="Backend policy decision"
                tone="policy"
                value={selected.route}
                caption="Deterministic backend decision."
                icon={ShieldCheck}
              />
              <DecisionCard
                title="Final decision"
                tone="pending"
                value={humanizeEnum(selected.status)}
                caption="Current status"
                icon={Hourglass}
              />
            </div>

            {/* Why the policy engine routed it here */}
            <div className="mb-8 rounded-xl border border-outline-variant bg-surface-container p-6">
              <h3 className="mb-4 font-technical-label text-technical-label uppercase text-outline">
                Triggered policy rules
              </h3>
              {selected.reasons.length === 0 ? (
                <p className="text-body-sm text-on-surface-variant">
                  The backend recorded no specific rule for this route.
                </p>
              ) : (
                <ul className="list-inside list-disc space-y-1 text-body-sm text-on-surface">
                  {selected.reasons.map((entry) => (
                    <li key={entry.code}>
                      {entry.meaning}{' '}
                      <span className="font-technical-label text-[10px] uppercase text-outline">
                        ({entry.code})
                      </span>
                    </li>
                  ))}
                </ul>
              )}
              <div className="mt-4 flex flex-wrap items-center gap-3">
                <PolicyDecisionBadge
                  route={selected.route}
                  policyException={selected.policyException}
                />
                {workItem.data?.riskLevel ? <RiskBadge risk={workItem.data.riskLevel} /> : null}
              </div>
            </div>

            {/* Actions */}
            <div className="flex flex-wrap gap-3 border-t border-outline-variant pt-6">
              <Button
                loading={approve.isPending}
                onClick={() =>
                  approve.mutate(
                    { id: selected.id },
                    {
                      onSuccess: () => notify({ tone: 'success', title: 'Approved' }),
                      onError: () =>
                        notify({ tone: 'error', title: 'The backend rejected that approval' }),
                    },
                  )
                }
              >
                Approve
              </Button>
              <Button variant="secondary" onClick={() => setDialog('reject')}>
                Request changes
              </Button>
              <Button variant="ghost" onClick={() => setDialog('escalate')}>
                Escalate
              </Button>
              {workItem.data ? (
                <Link to={paths.workItem(workItem.data.id)} className="ml-auto">
                  <Button variant="ghost">Open full work item</Button>
                </Link>
              ) : null}
            </div>
          </div>
        )}
      </section>

      {/* RIGHT - backend-ranked candidates */}
      <section className="shrink-0 overflow-y-auto border-t border-outline-variant bg-surface-container-lowest laptop:w-[340px] laptop:border-l laptop:border-t-0">
        <div className="border-b border-outline-variant p-6">
          <h2 className="font-headline-md text-headline-md text-on-surface">
            Backend-ranked assignment candidates
          </h2>
          <p className="mt-1 font-body-sm text-body-sm text-on-surface-variant">
            Candidate scores are specific to this work item and are not employee performance
            ratings.
          </p>
          {ranking.available && !ranking.isReal ? (
            <DemoDataBadge
              className="mt-3"
              reason="The ranking is only produced by the side-effecting POST /work-items/{id}/route. No cached routing outcome exists for this item."
            />
          ) : null}
        </div>

        <div className="space-y-4 p-4">
          {!ranking.available ? (
            <EmptyState
              title="Ranking unavailable"
              description="A ranking is only produced when the backend routes the item. Approving here uses the backend's own assignment."
              compact
            />
          ) : (
            ranking.candidates.map((candidate, index) => (
              <AssignmentCandidateCard
                key={candidate.candidateUserId}
                candidate={candidate}
                recommended={index === 0}
                /* No select control against fixture ids: approving would post a
                   fabricated user id to the backend. */
                {...(ranking.isReal
                  ? {
                      onSelect: () =>
                        approve.mutate(
                          { id: selected?.id ?? '', assigneeIdOverride: candidate.candidateUserId },
                          { onSuccess: () => notify({ tone: 'success', title: 'Approved' }) },
                        ),
                    }
                  : {})}
              />
            ))
          )}

          <p className="mt-4 rounded-lg bg-surface-variant/30 p-3 font-technical-label text-[10px] leading-tight text-outline">
            Capacity is based on open estimated work and remaining capacity, as computed by the
            backend.
          </p>
        </div>
      </section>

      <Modal
        open={dialog !== null}
        onClose={closeDialog}
        title={dialog === 'escalate' ? 'Escalate this decision' : 'Request changes'}
        description={
          dialog === 'escalate'
            ? 'The backend routes this to the next approval level and records your reason.'
            : 'The requester sees your reason. The item returns to them for revision.'
        }
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={closeDialog}>
              Cancel
            </Button>
            <Button
              loading={reject.isPending || escalate.isPending}
              disabled={reason.trim().length < 10}
              onClick={() => {
                if (!selected) return;
                const variables = { id: selected.id, reason: reason.trim() };
                const onSuccess = () => {
                  closeDialog();
                  notify({ tone: 'success', title: 'Decision recorded' });
                };
                if (dialog === 'escalate') escalate.mutate(variables, { onSuccess });
                else reject.mutate(variables, { onSuccess });
              }}
            >
              {dialog === 'escalate' ? 'Escalate' : 'Send back'}
            </Button>
          </>
        }
      >
        <TextArea
          label="Reason"
          required
          rows={4}
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          error={
            reason.trim().length > 0 && reason.trim().length < 10
              ? 'Give at least 10 characters of context.'
              : undefined
          }
        />
      </Modal>
    </div>
  );
};

export default AiReviewPage;
