import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { AlertOctagon, CalendarX, FileWarning, Network, RefreshCcw } from 'lucide-react';
import { PageHeader } from '@/design-system/components/PageHeader';
import { MetricCard } from '@/design-system/components/MetricCard';
import { Button } from '@/design-system/components/Button';
import { EmptyState, ErrorState, LoadingState } from '@/design-system/components/states';
import { PolicyDecisionBadge } from '@/design-system/components/badges';
import { DecisionDrawer } from './components/DecisionDrawer';
import { CrossTeamDeliveryPanel, AiAnalyticsPanel } from './components/DeliveryPanels';
import {
  useApproveRecommendation,
  useManagerEscalations,
  useOverdueApprovals,
  usePendingApprovals,
  useRejectRecommendation,
} from '@/features/team-lead/hooks/useReviewQueue';
import { useWorkItem } from '@/features/work-items/hooks/useWorkItems';
import { useDashboard } from '@/features/dashboard/useDashboard';
import { useToast } from '@/app/providers/ToastProvider';
import { paths } from '@/app/router/paths';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { formatRelative, humanizeEnum } from '@/lib/format';
import { cn } from '@/lib/cn';

/**
 * Manager overview, converted from iris_manager_overview.
 *
 * Structure preserved: the mono eyebrow + serif page title with a right-hand
 * "last updated / refresh" cluster, a four-up summary grid, then the 8/4 content grid
 * whose left column is the decision table (`manager-selected-row` = indigo tint plus a
 * 2px indigo left rule) and whose right column stacks the delivery and trend panels.
 * Selecting a row opens the source's 440px glass decision drawer.
 *
 * The brief folds the former separate manager sub-pages into this one screen, which is
 * what the source shows anyway: decisions, cross-team delivery, risks and AI analytics
 * are sections here, not routes.
 *
 * Real: GET /manager/escalations, GET /approvals/overdue, GET /work-items counts,
 * POST /approvals/{id}/approve | reject. Demo: the two right-hand panels, badged.
 */
export const ManagerOverviewPage = () => {
  const [searchParams] = useSearchParams();
  const { notify } = useToast();
  const escalations = useManagerEscalations();
  const overdue = useOverdueApprovals();
  const pending = usePendingApprovals(0);
  const { manager } = useDashboard();
  const [selectedId, setSelectedId] = useState<string | null>(searchParams.get('approval'));
  useDocumentTitle('Manager overview');

  const decisions = useMemo(
    () => [...(pending.data?.content ?? []), ...(escalations.data ?? []), ...(overdue.data ?? [])].filter(
      (approval, index, all) => all.findIndex((other) => other.id === approval.id) === index,
    ),
    [pending.data, escalations.data, overdue.data],
  );

  const selected = decisions.find((approval) => approval.id === selectedId) ?? null;
  const selectedWorkItem = useWorkItem(selected?.workItemId);

  useEffect(() => {
    if (decisions.length === 0) {
      if (selectedId !== null) setSelectedId(null);
      return;
    }
    if (selectedId !== null && decisions.some((approval) => approval.id === selectedId)) return;
    if (searchParams.get('approval')) {
      setSelectedId(decisions[0]?.id ?? null);
    }
  }, [decisions, searchParams, selectedId]);

  const approve = useApproveRecommendation(selected?.workItemId);
  const reject = useRejectRecommendation(selected?.workItemId);

  if (escalations.isLoading) return <LoadingState variant="page" label="Loading the overview" />;
  if (escalations.error) return <ErrorState error={escalations.error} />;

  return (
    <div className="flex">
      <div className="min-w-0 flex-1 p-4 laptop:p-8">
        <div className="mx-auto w-full max-w-shell space-y-8">
          <PageHeader
            eyebrow="Manager workspace"
            title="Operational overview"
            size="sm"
            description="Review critical decisions, escalations and cross-team delivery risks."
            actions={
              <>
                <div className="text-right text-xs">
                  <p className="text-on-surface-variant">Last updated</p>
                  <p className="font-technical-sm">{formatRelative(new Date().toISOString())}</p>
                </div>
                <Button
                  variant="ghost"
                  leadingIcon={<RefreshCcw className="size-4" aria-hidden />}
                  onClick={() => {
                    void pending.refetch();
                    void escalations.refetch();
                    void overdue.refetch();
                  }}
                >
                  Refresh
                </Button>
              </>
            }
          />

          {/* Summary cards */}
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 laptop:grid-cols-4">
            <MetricCard
              label="Decisions required"
              value={decisions.length}
              caption="Escalated or overdue approvals"
              icon={FileWarning}
              tone="emphasis"
            />
            <MetricCard
              label="Overdue approvals"
              value={overdue.data?.length ?? 0}
              caption="Past their policy SLA"
              icon={CalendarX}
              tone="danger"
            />
            <MetricCard
              label="Blocked work"
              value={manager.blockedCount.data ?? '-'}
              caption="Dependencies holding work up"
              icon={Network}
              tone="warning"
            />
            <MetricCard
              label="Overdue work"
              value={manager.overdueCount.data ?? '-'}
              caption="Past their due date"
              icon={AlertOctagon}
            />
          </div>

          {/* Content grid */}
          <div className="grid grid-cols-1 gap-8 laptop:grid-cols-12">
            <div className="space-y-6 laptop:col-span-8">
              <section className="overflow-hidden rounded-xl border border-outline-variant bg-surface-container-low">
                <div className="flex items-center justify-between gap-3 border-b border-outline-variant bg-surface-container p-6">
                  <h2 className="font-headline-md text-headline-md">
                    Decisions requiring attention
                  </h2>
                  <span className="flex items-center gap-1 rounded-full bg-ai-violet/10 px-3 py-1 text-xs font-semibold text-ai-violet">
                    Policy-prioritised
                  </span>
                </div>

                {decisions.length === 0 ? (
                  <div className="p-6">
                    <EmptyState
                      title="No decisions are waiting"
                      description="Nothing has been escalated to you and no approval is past its SLA."
                    />
                  </div>
                ) : (
                  <>
                    <table className="hidden w-full border-collapse text-left md:table">
                      <caption className="sr-only">Approvals awaiting a management decision</caption>
                      <thead className="bg-surface-container-low">
                        <tr className="font-technical-label text-[10px] uppercase text-on-surface-variant">
                          {['Route', 'Reason', 'Waiting', 'Status', ''].map((header, index) => (
                            <th
                              key={header || `col-${index}`}
                              scope="col"
                              className="px-4 py-3 font-medium"
                            >
                              {header}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-outline-variant text-sm">
                        {decisions.map((approval) => (
                          <tr
                            key={approval.id}
                            className={cn(
                              'cursor-pointer transition-colors',
                              approval.id === selectedId
                                ? 'border-l-2 border-l-brand-indigo bg-brand-indigo/10'
                                : 'hover:bg-surface-variant/30',
                            )}
                            onClick={() => setSelectedId(approval.id)}
                          >
                            <td className="px-4 py-4">
                              <PolicyDecisionBadge
                                route={approval.route}
                                policyException={approval.policyException}
                              />
                            </td>
                            <td className="px-4 py-4">
                              <div className="line-clamp-1 font-semibold">
                                {approval.reasons[0]?.meaning ?? humanizeEnum(approval.status)}
                              </div>
                              {approval.escalationReason ? (
                                <div className="line-clamp-1 text-xs text-on-surface-variant">
                                  {approval.escalationReason}
                                </div>
                              ) : null}
                            </td>
                            <td
                              className={cn(
                                'px-4 py-4 text-xs',
                                approval.overdue && 'font-bold text-error',
                              )}
                            >
                              {formatRelative(approval.createdAt)}
                            </td>
                            <td className="px-4 py-4 text-xs">{humanizeEnum(approval.status)}</td>
                            <td className="px-4 py-4">
                              <button
                                type="button"
                                onClick={(event) => {
                                  event.stopPropagation();
                                  setSelectedId(approval.id);
                                }}
                                className="rounded bg-brand-indigo px-3 py-1 text-xs text-white hover:opacity-90"
                              >
                                Review
                                <span className="sr-only"> approval {approval.route}</span>
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>

                    <ul className="divide-y divide-outline-variant md:hidden">
                      {decisions.map((approval) => (
                        <li key={approval.id}>
                          <button
                            type="button"
                            onClick={() => setSelectedId(approval.id)}
                            className="w-full space-y-2 p-4 text-left"
                          >
                            <PolicyDecisionBadge
                              route={approval.route}
                              policyException={approval.policyException}
                            />
                            <p className="text-sm font-semibold">
                              {approval.reasons[0]?.meaning ?? humanizeEnum(approval.status)}
                            </p>
                            <p
                              className={cn(
                                'text-xs',
                                approval.overdue
                                  ? 'font-bold text-error'
                                  : 'text-on-surface-variant',
                              )}
                            >
                              Waiting {formatRelative(approval.createdAt)}
                            </p>
                          </button>
                        </li>
                      ))}
                    </ul>
                  </>
                )}
              </section>

              <p className="text-xs text-on-surface-variant">
                Need the full item?{' '}
                <Link to={paths.aiReview} className="text-brand-indigo hover:underline">
                  Open the review queue
                </Link>
                .
              </p>
            </div>

            <div className="space-y-6 laptop:col-span-4">
              <CrossTeamDeliveryPanel />
              <AiAnalyticsPanel />
            </div>
          </div>
        </div>
      </div>

      {selected ? (
        <DecisionDrawer
          approval={selected}
          workItem={selectedWorkItem.data}
          aiConfidence={selectedWorkItem.data?.aiConfidence ?? null}
          pending={approve.isPending || reject.isPending}
          onClose={() => setSelectedId(null)}
          onApprove={(note) =>
            approve.mutate(
              { id: selected.id, note },
              {
                onSuccess: () => {
                  setSelectedId(null);
                  notify({ tone: 'success', title: 'Decision committed' });
                },
                onError: () =>
                  notify({ tone: 'error', title: 'The backend rejected that decision' }),
              },
            )
          }
          onReject={(note) =>
            reject.mutate(
              { id: selected.id, reason: note },
              {
                onSuccess: () => {
                  setSelectedId(null);
                  notify({ tone: 'success', title: 'Decision recorded' });
                },
              },
            )
          }
        />
      ) : null}
    </div>
  );
};

export default ManagerOverviewPage;
