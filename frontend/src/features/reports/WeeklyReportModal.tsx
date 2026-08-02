import { useMemo, useState } from 'react';
import { Sparkles } from 'lucide-react';
import { Modal } from '@/design-system/components/Modal';
import { Button } from '@/design-system/components/Button';
import { Chip } from '@/design-system/components/badges';
import { LoadingState, ErrorState } from '@/design-system/components/states';
import { isApiError } from '@/api/errors/ApiError';
import { errorMessage } from '@/api/errors/messages';
import type { WeeklyReport } from '@/api/types';
import {
  useGenerateWeeklyReport,
  useSendWeeklyReport,
  useWeeklyReportPreview,
} from './useWeeklyReport';

export interface WeeklyReportModalProps {
  open: boolean;
  onClose: () => void;
}

const parseRecipients = (raw: string): string[] =>
  raw
    .split(/[,\n]/)
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0);

/**
 * The whole Manager-triggered weekly report flow in one modal: generate → preview →
 * edit recipients → send. Deliberately one component rather than a page + a route,
 * because every step depends on the report just generated - there is nothing else to
 * navigate to in between.
 */
export const WeeklyReportModal = ({ open, onClose }: WeeklyReportModalProps) => {
  const [reportId, setReportId] = useState<string | null>(null);
  const [recipientsText, setRecipientsText] = useState('');
  const [recipientsTouched, setRecipientsTouched] = useState(false);

  const generate = useGenerateWeeklyReport();
  const preview = useWeeklyReportPreview(reportId);
  const send = useSendWeeklyReport(reportId);

  const report: WeeklyReport | undefined = send.data ?? preview.data ?? generate.data;

  const handleClose = () => {
    if (send.isPending) return; // never close out from under an in-flight send
    setReportId(null);
    setRecipientsText('');
    setRecipientsTouched(false);
    generate.reset();
    send.reset();
    onClose();
  };

  const handleGenerate = () => {
    if (generate.isPending) return; // belt-and-suspenders alongside the disabled button
    generate.mutate(undefined, {
      onSuccess: (created) => {
        setReportId(created.id);
        if (!recipientsTouched) {
          setRecipientsText(created.defaultRecipients.join('\n'));
        }
      },
    });
  };

  const recipients = useMemo(() => parseRecipients(recipientsText), [recipientsText]);

  const handleSend = () => {
    // Disabled state already prevents this, but a second guard here is what actually
    // stops a duplicate network call if the button's disabled attribute is bypassed
    // (a fast double Enter-key submit, for instance) before React re-renders.
    if (send.isPending || report?.status === 'SENDING' || report?.status === 'SENT') return;
    send.mutate(recipients);
  };

  const alreadySent = report?.status === 'SENT';
  const sendDisabled =
    send.isPending || report?.status === 'SENDING' || alreadySent || recipients.length === 0;

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title="Weekly Report"
      description="Generated from this week's persisted work item data."
      size="lg"
      dismissible={!send.isPending}
      footer={
        <>
          <Button variant="ghost" onClick={handleClose} disabled={send.isPending}>
            Close
          </Button>
          {report ? (
            <Button
              variant="primary"
              onClick={handleSend}
              disabled={sendDisabled}
              loading={send.isPending}
            >
              {alreadySent ? 'Sent' : 'Send Report'}
            </Button>
          ) : (
            <Button variant="primary" onClick={handleGenerate} loading={generate.isPending}>
              Generate Report
            </Button>
          )}
        </>
      }
    >
      {!report && !generate.isPending && !generate.isError ? (
        <div className="flex flex-col items-center gap-3 py-10 text-center">
          <p className="text-body-sm text-on-surface-variant">
            Compute this week&apos;s status, completions, blockers, overdue and SLA-risk
            work, department workload and reassignments, then preview it before sending.
            Click &ldquo;Generate Report&rdquo; below to start.
          </p>
        </div>
      ) : null}

      {generate.isPending ? <LoadingState variant="page" label="Computing this week's report" /> : null}

      {generate.isError ? (
        <ErrorState error={generate.error} onRetry={handleGenerate} />
      ) : null}

      {report && preview.isLoading ? <LoadingState variant="page" label="Loading report" /> : null}
      {preview.isError ? <ErrorState error={preview.error} /> : null}

      {report ? (
        <div className="space-y-6">
          <div className="flex flex-wrap items-center gap-2">
            <Chip tone="editorial">
              {report.periodStart} to {report.periodEnd}
            </Chip>
            <Chip tone={report.status === 'SENT' ? 'accent' : 'editorial'}>{report.status}</Chip>
            {report.aiProvider === 'deterministic-fallback' ? (
              <Chip tone="editorial">AI summary unavailable — showing metrics-only summary</Chip>
            ) : null}
          </div>

          {report.aiSummary ? (
            <section className="rounded-md border border-ai-violet/30 bg-ai-violet/[0.06] p-4">
              <h3 className="mb-2 flex items-center gap-1.5 text-body-sm font-semibold text-on-surface">
                <Sparkles aria-hidden className="size-4 text-ai-violet" />
                Management summary
              </h3>
              <p className="text-body-sm text-on-surface-variant">{report.aiSummary}</p>
              {report.aiRecommendedActions?.length ? (
                <ul className="mt-2 list-disc space-y-1 pl-5 text-body-sm text-on-surface-variant">
                  {report.aiRecommendedActions.map((action) => (
                    <li key={action}>{action}</li>
                  ))}
                </ul>
              ) : null}
            </section>
          ) : null}

          <section>
            <h3 className="mb-2 text-body-sm font-semibold text-on-surface">Status counts</h3>
            <div className="flex flex-wrap gap-2">
              {Object.entries(report.metrics.statusCounts).map(([status, count]) => (
                <Chip key={status} tone="editorial">
                  {status}: {count}
                </Chip>
              ))}
            </div>
          </section>

          <section className="grid grid-cols-2 gap-4 laptop:grid-cols-4">
            <Metric label="Completed this week" value={report.metrics.completedThisWeek.length} />
            <Metric label="Blocked" value={report.metrics.blockedTasks.length} tone="danger" />
            <Metric label="Overdue" value={report.metrics.overdueTasks.length} tone="warning" />
            <Metric label="SLA risk" value={report.metrics.slaRiskTasks.length} tone="warning" />
            <Metric label="Reassignments" value={report.metrics.reassignments.length} />
            <Metric label="Approval changes" value={report.metrics.approvalChanges.length} />
            <Metric label="Active employees" value={report.metrics.activeByEmployee.length} />
            <Metric label="Parent tickets" value={report.metrics.parentTicketProgress.length} />
          </section>

          {report.metrics.completedThisWeek.length > 0 ? (
            <section>
              <h3 className="mb-2 text-body-sm font-semibold text-on-surface">Completed this week</h3>
              <ul className="space-y-1 text-body-sm text-on-surface-variant">
                {report.metrics.completedThisWeek.map((task) => (
                  <li key={task.referenceKey}>
                    <span className="font-technical-label">{task.referenceKey}</span> — {task.summary}
                    {' · completed by '}
                    {task.completedByName ?? 'Unassigned'}
                  </li>
                ))}
              </ul>
            </section>
          ) : null}

          {report.metrics.blockedTasks.length > 0 ? (
            <section>
              <h3 className="mb-2 text-body-sm font-semibold text-on-surface">Blocked</h3>
              <ul className="space-y-1 text-body-sm text-on-surface-variant">
                {report.metrics.blockedTasks.map((task) => (
                  <li key={task.referenceKey}>
                    <span className="font-technical-label">{task.referenceKey}</span> — {task.blockReason}
                  </li>
                ))}
              </ul>
            </section>
          ) : null}

          <section>
            <h3 className="mb-2 text-body-sm font-semibold text-on-surface">Department workload</h3>
            <ul className="space-y-1 text-body-sm text-on-surface-variant">
              {report.metrics.departmentWorkload.map((department) => (
                <li key={department.departmentId}>
                  {department.departmentName}: {department.assignedHours} / {department.totalCapacityHours} hrs
                  {' '}(remaining {department.remainingCapacityHours} hrs)
                </li>
              ))}
            </ul>
          </section>

          <section className="space-y-2">
            <label htmlFor="report-recipients" className="text-body-sm font-semibold text-on-surface">
              Recipients (one per line or comma-separated)
            </label>
            <textarea
              id="report-recipients"
              className="w-full rounded-md border border-border-card bg-field p-2 text-body-sm text-on-surface"
              rows={3}
              value={recipientsText}
              disabled={alreadySent || send.isPending}
              onChange={(event) => {
                setRecipientsTouched(true);
                setRecipientsText(event.target.value);
              }}
            />
            {recipients.length === 0 ? (
              <p className="text-body-sm text-warning-amber">At least one recipient is required.</p>
            ) : null}
          </section>

          {send.isError ? (
            <ErrorState
              error={send.error}
              compact
              onRetry={
                isApiError(send.error) && send.error.isConflict ? undefined : handleSend
              }
            />
          ) : null}

          {send.isSuccess && report.status === 'SENT' ? (
            <p className="rounded-md border border-success-teal/30 bg-success-teal/[0.08] p-3 text-body-sm text-on-surface">
              Report sent to {report.recipients?.join(', ') ?? recipients.join(', ')}.
            </p>
          ) : null}

          {report.status === 'SEND_FAILED' ? (
            <p className="rounded-md border border-error/30 bg-error/[0.06] p-3 text-body-sm text-on-surface">
              Delivery failed: {report.emailFailureReason ?? errorMessage(send.error)}. The
              outbox will keep retrying automatically; you can also click Send Report again.
            </p>
          ) : null}
        </div>
      ) : null}
    </Modal>
  );
};

const Metric = ({
  label,
  value,
  tone = 'default',
}: {
  label: string;
  value: number;
  tone?: 'default' | 'warning' | 'danger';
}) => (
  <div className="rounded-md border border-border-card bg-card p-3">
    <p className="text-technical-label uppercase tracking-widest text-on-surface-variant/60">{label}</p>
    <p
      className={
        tone === 'danger'
          ? 'text-headline-md text-error'
          : tone === 'warning'
            ? 'text-headline-md text-warning-amber'
            : 'text-headline-md text-on-surface'
      }
    >
      {value}
    </p>
  </div>
);

export default WeeklyReportModal;
