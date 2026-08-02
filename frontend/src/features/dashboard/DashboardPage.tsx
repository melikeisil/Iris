import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Ban,
  CheckCircle2,
  Clock,
  ListChecks,
  MessageSquare,
  ShieldAlert,
  Sparkles,
  Star,
} from 'lucide-react';
import { PageHeader } from '@/design-system/components/PageHeader';
import { MetricCard } from '@/design-system/components/MetricCard';
import { Button } from '@/design-system/components/Button';
import { ErrorState, LoadingState } from '@/design-system/components/states';
import { PanelCard, CountPill } from '@/features/work-items/components/PanelCard';
import { WorkItemTable } from '@/features/work-items/components/WorkItemTable';
import { ClarificationsPanel } from './components/ClarificationsPanel';
import { ApprovalListPanel } from './components/ApprovalListPanel';
import { TeamWorkloadPanel } from './components/TeamWorkloadPanel';
import { ActivityPanel } from './components/ActivityPanel';
import { WeeklyReportModal } from '@/features/reports/WeeklyReportModal';
import { useDashboard } from './useDashboard';
import { useMyWorkItems } from '@/features/work-items/hooks/useWorkItems';
import { toWorkItemRows } from '@/features/work-items/viewModel';
import { paths } from '@/app/router/paths';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { cn } from '@/lib/cn';

/**
 * One dashboard for every role, converted from iris_employee_dashboard_default.
 *
 * The Stitch set ships one dashboard mockup; the brief calls for role-aware widgets.
 * Rather than three pages, the layout is fixed - a four-up metric row, then three
 * 8/4 content rows - and the role decides what fills each slot. That is exactly how
 * the source treats its own panels, which are interchangeable cards in a 12-column grid.
 *
 * The empty and loading states follow iris_employee_dashboard_empty and
 * iris_employee_dashboard_loading.
 */
const greeting = (): string => {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 18) return 'Good afternoon';
  return 'Good evening';
};

const firstName = (fullName: string): string => fullName.split(/\s+/)[0] ?? fullName;

type PreviewTab = 'assigned' | 'reported' | 'watching';

export const DashboardPage = () => {
  const { audience, user, personal, teamLead, manager, isLoading, error } = useDashboard();
  const assignedQuery = useMyWorkItems();
  const [tab, setTab] = useState<PreviewTab>('assigned');
  const [reportModalOpen, setReportModalOpen] = useState(false);
  useDocumentTitle('Dashboard');

  if (isLoading) return <LoadingState variant="page" label="Loading your dashboard" />;
  if (error) return <ErrorState error={error} />;

  const previewRows =
    tab === 'assigned'
      ? personal.assignedOpen
      : tab === 'reported'
        ? personal.reportedOpen
        : personal.watching;

  // Index-aligned: the rows and their timestamps must come from the same sorted array.
  const recent = [...(assignedQuery.data ?? [])]
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
    .slice(0, 5);

  return (
    <div className="mx-auto w-full max-w-shell p-4 laptop:p-8">
      <PageHeader
        title={`${greeting()}, ${user ? firstName(user.fullName) : 'there'}`}
        description="Here’s the work that needs your attention today."
        actions={
          <>
            {audience === 'MANAGER' ? (
              <Button variant="secondary" onClick={() => setReportModalOpen(true)}>
                Generate Report
              </Button>
            ) : null}
            <Link to={paths.myWork}>
              <Button variant="ghost">View My Work</Button>
            </Link>
          </>
        }
      />

      {audience === 'MANAGER' ? (
        <WeeklyReportModal open={reportModalOpen} onClose={() => setReportModalOpen(false)} />
      ) : null}

      {/* ROW 1 - summary cards. The set is role-dependent. */}
      <div className="mb-8 grid grid-cols-1 gap-6 sm:grid-cols-2 laptop:grid-cols-4">
        {audience === 'EMPLOYEE' ? (
          <>
            <MetricCard
              label="Assigned to me"
              value={personal.assignedOpen.length}
              caption="Open work items"
              icon={ListChecks}
              to={paths.myWork}
            />
            <MetricCard
              label="Due soon"
              value={personal.dueSoonCount}
              caption="Due today or overdue"
              icon={Clock}
              tone="warning"
              to={`${paths.myWork}?due=soon`}
            />
            <MetricCard
              label="Blocked"
              value={personal.blockedCount}
              caption="Active blockers"
              icon={Ban}
              tone="danger"
              to={`${paths.myWork}?blocked=true`}
            />
            <MetricCard
              label="Needs information"
              value={personal.needsInformationCount}
              caption="Pending replies"
              icon={MessageSquare}
              tone="emphasis"
            />
          </>
        ) : null}

        {audience === 'TEAM_LEAD' ? (
          <>
            <MetricCard
              label="Pending AI reviews"
              value={teamLead.reviewQueue.data?.length ?? 0}
              caption="Awaiting your decision"
              icon={Sparkles}
              tone="ai"
              to={paths.aiReview}
            />
            <MetricCard
              label="Pending approvals"
              value={teamLead.pendingApprovals.data?.page.totalElements ?? 0}
              caption="Assigned to you"
              icon={ShieldAlert}
              tone="emphasis"
            />
            <MetricCard
              label="Overdue approvals"
              value={teamLead.overdueApprovals.data?.length ?? 0}
              caption="Past their SLA"
              icon={Clock}
              tone="danger"
            />
            <MetricCard
              label="Blocked work"
              value={personal.blockedCount}
              caption="On your own items"
              icon={Ban}
              tone="danger"
              to={`${paths.myWork}?blocked=true`}
            />
          </>
        ) : null}

        {audience === 'MANAGER' ? (
          <>
            <MetricCard
              label="Open work"
              value={manager.split.data?.open ?? '-'}
              caption={
                manager.split.data?.truncated
                  ? `of ${manager.split.data.sampled} most recent`
                  : 'Not yet done'
              }
              icon={ListChecks}
            />
            <MetricCard
              label="Completed work"
              value={manager.split.data?.completed ?? '-'}
              caption={
                manager.split.data?.truncated
                  ? `of ${manager.split.data.sampled} most recent`
                  : 'Done'
              }
              icon={CheckCircle2}
            />
            <MetricCard
              label="Overdue work"
              value={manager.overdueCount.data ?? '-'}
              caption="Past their due date"
              icon={Clock}
              tone="warning"
            />
            <MetricCard
              label="Blocked work"
              value={manager.blockedCount.data ?? '-'}
              caption="Across your scope"
              icon={Ban}
              tone="danger"
            />
          </>
        ) : null}
      </div>

      {/* ROW 2 - today's focus, plus the role's attention panel. */}
      <div className="mb-8 grid grid-cols-1 gap-6 laptop:grid-cols-12">
        <PanelCard
          className="laptop:col-span-8"
          title={
            <>
              Today’s focus
              <CountPill>{personal.todaysFocus.length} items</CountPill>
            </>
          }
        >
          <WorkItemTable
            rows={personal.todaysFocus}
            caption="Work needing attention today"
            emptyTitle="Nothing urgent today"
            emptyDescription="No blocked, overdue or high-priority work is assigned to you."
          />
        </PanelCard>

        <div className="flex flex-col gap-6 laptop:col-span-4">
          {audience === 'EMPLOYEE' ? (
            <ClarificationsPanel
              rows={personal.reportedOpen.filter((row) => row.actionLabel === 'Answer questions')}
            />
          ) : (
            <PanelCard
              technicalTitle={audience === 'MANAGER' ? 'Escalations' : 'AI review queue'}
              className="flex-1"
            >
              <ApprovalListPanel
                approvals={
                  (audience === 'MANAGER'
                    ? manager.escalations.data
                    : teamLead.reviewQueue.data) ?? []
                }
                linkTo={(approval) =>
                  audience === 'MANAGER'
                    ? `${paths.manager}?approval=${approval.id}`
                    : `${paths.aiReview}?approval=${approval.id}`
                }
                emptyTitle={
                  audience === 'MANAGER' ? 'No open escalations' : 'The review queue is clear'
                }
                emptyDescription="Nothing is waiting on a decision from you right now."
              />
            </PanelCard>
          )}
        </div>
      </div>

      {/* ROW 3 - my work preview, plus workload. */}
      <div className="mb-8 grid grid-cols-1 gap-6 laptop:grid-cols-12">
        <PanelCard className="laptop:col-span-8" title="My work preview">
          <div className="flex gap-8 overflow-x-auto border-b border-border-structural/30 px-6">
            {(
              [
                ['assigned', 'Assigned to me'],
                ['reported', 'Raised by me'],
                ['watching', 'Watching'],
              ] as const
            ).map(([id, label]) => (
              <button
                key={id}
                type="button"
                onClick={() => setTab(id)}
                aria-pressed={tab === id}
                className={cn(
                  'whitespace-nowrap border-b-2 pb-3 text-sm font-medium transition-colors',
                  tab === id
                    ? 'border-primary text-on-surface'
                    : 'border-transparent text-on-surface-variant hover:text-on-surface',
                )}
              >
                {label}
              </button>
            ))}
          </div>
          <WorkItemTable
            rows={previewRows}
            caption="Your work items"
            emptyTitle="Nothing here yet"
            emptyDescription="Work you are assigned, raised or watching will appear here."
          />
        </PanelCard>

        <PanelCard className="laptop:col-span-4">
          {audience === 'EMPLOYEE' ? (
            <div className="space-y-4 p-6">
              <h2 className="font-technical-label text-[11px] uppercase tracking-widest text-on-surface-variant/60">
                Your queue
              </h2>
              <dl className="space-y-3 text-xs">
                {[
                  ['Open work items', personal.assignedOpen.length],
                  ['Due today or overdue', personal.dueSoonCount],
                  ['Blocked', personal.blockedCount],
                  ['Raised by me, still open', personal.reportedOpen.length],
                  ['Watching', personal.watching.length],
                ].map(([label, value]) => (
                  <div key={String(label)} className="flex justify-between gap-4">
                    <dt className="text-on-surface-variant">{label}</dt>
                    <dd className="font-technical-label font-bold text-on-surface">{value}</dd>
                  </div>
                ))}
              </dl>
              <p className="pt-2 text-[10px] italic text-on-surface-variant/60">
                Counts come from your own work items. Sprint capacity is not modelled by this
                backend.
              </p>
            </div>
          ) : (
            <TeamWorkloadPanel />
          )}
        </PanelCard>
      </div>

      {/* ROW 4 - raised by me, plus recent activity. */}
      <div className="grid grid-cols-1 gap-6 pb-8 laptop:grid-cols-12">
        <PanelCard className="laptop:col-span-8" title="Requests I raised">
          <WorkItemTable
            rows={personal.reportedOpen}
            caption="Requests you raised that are still open"
            emptyTitle="You have not raised anything"
            emptyDescription="Requests you create appear here until they are closed."
          />
        </PanelCard>

        <PanelCard
          className="laptop:col-span-4"
          technicalTitle="Recent activity"
          headerAction={<Star aria-hidden className="size-4 text-on-surface-variant" />}
          padded
        >
          <ActivityPanel
            rows={toWorkItemRows(recent)}
            updatedAt={recent.map((item) => item.updatedAt)}
          />
        </PanelCard>
      </div>
    </div>
  );
};

export default DashboardPage;
