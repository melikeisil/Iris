import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { CircleDashed, CheckCircle2, Eye, EyeOff, ExternalLink, RefreshCcw } from 'lucide-react';
import { Button } from '@/design-system/components/Button';
import { errorMessage } from '@/api/errors/messages';
import { ErrorState, LoadingState, EmptyState } from '@/design-system/components/states';
import {
  AiConfidenceIndicator,
  Chip,
  PriorityBadge,
  RiskBadge,
  StatusBadge,
} from '@/design-system/components/badges';
import { WorkflowActions } from '@/design-system/components/WorkflowActions';
import { ActivityTimeline } from '@/design-system/components/ActivityTimeline';
import { DetailCard, MetaRow } from './components/DetailCard';
import { BlockWorkItemDialog } from './components/BlockWorkItemDialog';
import { CommentComposer } from './components/CommentComposer';
import { ClarificationQuestionsForm } from './components/ClarificationQuestionsForm';
import {
  useAddComment,
  useBlockWorkItem,
  useJiraStatus,
  useToggleWatch,
  useTransitionWorkItem,
  useWorkItem,
  useWorkItemChildren,
  useWorkItemComments,
  useCreateJiraIssue,
  useWorkItemLinks,
} from './hooks/useWorkItems';
import {
  useLatestAnalysis,
  useAnalyzeWorkItem,
  useRouteWorkItem,
  useSubmitAdditionalInformation,
} from './hooks/useAnalysis';
import { toWorkItemRows } from './viewModel';
import { WorkItemTable } from './components/WorkItemTable';
import { useToast } from '@/app/providers/ToastProvider';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { formatDate, formatHours, humanizeEnum } from '@/lib/format';
import { cn } from '@/lib/cn';
import { useAuth } from '@/app/providers/AuthProvider';

/**
 * Work item detail, converted from iris_work_item_detail.
 *
 * Structure preserved from the source: the header block (type chip, sync state, 48px
 * serif title, status and priority row, Edit plus one indigo primary action), the
 * five-tab rule, and the 8/4 grid whose left column stacks Description + Advisory AI
 * side by side, then Acceptance Criteria, Work Context and Activity, with Metadata and
 * its two stacked action buttons on the right.
 *
 * Everything is real: GET /work-items/{id}, /children, /links, /comments,
 * /recommendations, POST /transition, /block, /watch. The source's "Tree Complexity"
 * sparkline is dropped - there is no metric behind it and drawing a chart from nothing
 * would be a fabrication.
 */
type Tab = 'overview' | 'ai' | 'children' | 'dependencies' | 'activity';

const TABS: readonly { id: Tab; label: string; ai?: boolean }[] = [
  { id: 'overview', label: 'Overview' },
  { id: 'ai', label: 'AI & Policy', ai: true },
  { id: 'children', label: 'Child work' },
  { id: 'dependencies', label: 'Dependencies' },
  { id: 'activity', label: 'Activity' },
];

const formatEffort = (value: number | null | undefined): string =>
  value == null ? '—' : formatHours(value);

export const WorkItemDetailPage = () => {
  const { workItemId } = useParams<{ workItemId: string }>();
  const { notify } = useToast();
  const { hasRole } = useAuth();
  const [tab, setTab] = useState<Tab>('overview');
  const [blockOpen, setBlockOpen] = useState(false);

  const item = useWorkItem(workItemId);
  const jiraStatus = useJiraStatus();
  const children = useWorkItemChildren(workItemId, tab === 'children');
  const links = useWorkItemLinks(workItemId, tab === 'dependencies');
  const comments = useWorkItemComments(workItemId, tab === 'activity');
  const analysis = useLatestAnalysis(workItemId, tab === 'ai');

  const transition = useTransitionWorkItem(workItemId ?? '');
  const block = useBlockWorkItem(workItemId ?? '');
  const addComment = useAddComment(workItemId ?? '');
  const toggleWatch = useToggleWatch(workItemId ?? '');
  const analyze = useAnalyzeWorkItem(workItemId);
  const routeWorkItem = useRouteWorkItem(workItemId);
  const createJiraIssue = useCreateJiraIssue(workItemId ?? '');
  const submitAdditionalInformation = useSubmitAdditionalInformation(workItemId);

  useDocumentTitle(item.data ? `${item.data.referenceKey} · ${item.data.summary}` : 'Work item');

  if (item.isLoading) return <LoadingState variant="page" label="Loading work item" />;
  if (item.error) return <ErrorState error={item.error} />;
  if (!item.data) return <ErrorState error={new Error('Work item not found')} />;

  const detail = item.data;
  const jira = createJiraIssue.data
    ? {
        issueKey: createJiraIssue.data.issueKey,
        issueUrl: createJiraIssue.data.issueUrl,
        createdAt: createJiraIssue.data.createdAt,
      }
    : detail.jira;
  const watching = detail.relationships.includes('WATCHER');
  const canBlock =
    detail.permissions.canEdit &&
    detail.availableTransitions.some((transitionDef) => transitionDef.code === 'BLOCK' && transitionDef.enabled);
  const workflowActions = detail.availableTransitions.filter((transitionDef) => transitionDef.enabled);
  const hasJiraRole = hasRole('TEAM_LEAD', 'MANAGER');
  const jiraConfigured = jiraStatus.data?.configured ?? false;
  const canCreateJira = hasJiraRole && detail.permissions.canAssign && jiraConfigured;
  const jiraLinked = Boolean(jira?.issueKey);
  const jiraOpenable = Boolean(jira?.issueKey && jira.issueUrl);
  const jiraErrorMessage = createJiraIssue.error ? errorMessage(createJiraIssue.error) : null;
  const jiraStatusText = jiraLinked ? 'Linked to Jira' : 'Not linked to Jira';
  const jiraScopeBlocked = hasJiraRole && !detail.permissions.canAssign;
  const canRouteFromAnalysis = analysis.view !== null && !analysis.view.failed;
  const isRequester = detail.relationships.includes('REQUESTER');
  const needsClarification =
    analysis.view !== null && !analysis.view.failed && analysis.view.requiresMoreInformation;

  return (
    <div className="mx-auto w-full max-w-shell p-4 laptop:p-8">
      {/* Header */}
      <div className="mb-10 flex flex-col justify-between gap-6 md:flex-row md:items-end">
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-3">
            <Chip>{humanizeEnum(detail.workItemType)}</Chip>
            <span className="font-technical-label text-[10px] uppercase text-on-surface-variant">
              {detail.referenceKey}
            </span>
            {jira?.issueKey ? (
              <span className="flex items-center gap-1.5 font-technical-label text-[10px] uppercase text-on-surface-variant">
                <RefreshCcw aria-hidden className="size-3.5" />
                Jira {jira.issueKey}
              </span>
            ) : null}
          </div>

          <h1 className="max-w-3xl font-display-sm text-4xl italic tracking-tight text-on-surface laptop:text-5xl">
            {detail.summary}
          </h1>

          <div className="flex flex-wrap items-center gap-4">
            <StatusBadge status={detail.status} statusCategory={detail.statusCategory} />
            <PriorityBadge priority={detail.priority} />
            <RiskBadge risk={detail.riskLevel} />
            {detail.blocked ? (
              <span className="rounded border border-error/40 bg-error/10 px-2 py-0.5 font-technical-label text-[10px] uppercase text-error">
                Blocked · {detail.blocked.reason}
              </span>
            ) : null}
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <Button
            variant="ghost"
            leadingIcon={watching ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
            loading={toggleWatch.isPending}
            onClick={() => toggleWatch.mutate(watching)}
          >
            {watching ? 'Unwatch' : 'Watch'}
          </Button>
          {workflowActions.length > 0 ? (
            <WorkflowActions
              transitions={detail.availableTransitions}
              pendingCode={transition.isPending ? transition.variables?.transition : null}
              onTransition={(action) =>
                transition.mutate(
                  { transition: action.code, version: detail.version },
                  {
                    onSuccess: (result) =>
                      notify({ tone: 'success', title: `Moved to ${result.toState}` }),
                    onError: () =>
                      notify({ tone: 'error', title: 'That transition was rejected by the backend' }),
                  },
                )
              }
            />
          ) : null}
        </div>
      </div>

      {/* Tabs */}
      <div className="mb-8 flex items-center gap-8 overflow-x-auto border-b border-outline-variant" role="tablist">
        {TABS.map((definition) => (
          <button
            key={definition.id}
            type="button"
            role="tab"
            aria-selected={tab === definition.id}
            onClick={() => setTab(definition.id)}
            className={cn(
              'flex items-center gap-2 whitespace-nowrap pb-4 text-sm font-medium transition-colors',
              tab === definition.id
                ? 'border-b-2 border-primary text-primary'
                : 'text-on-surface-variant hover:text-on-surface',
            )}
          >
            {definition.label}
            {definition.ai ? (
              <span aria-hidden className="size-1.5 rounded-full bg-ai-violet" />
            ) : null}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-8 laptop:grid-cols-12">
        <div className="space-y-8 laptop:col-span-8">
          {tab === 'overview' ? (
            <>
              <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
                <DetailCard title="Description">
                  <p className="whitespace-pre-line text-body-sm leading-relaxed text-on-surface-variant">
                    {detail.description ?? 'No description was provided.'}
                  </p>
                </DetailCard>

                <DetailCard
                  title="Advisory AI analysis"
                  variant="ai"
                  action={<AiConfidenceIndicator confidence={detail.aiConfidence} />}
                >
                  {detail.aiConfidence === null ? (
                    <EmptyState
                      title="Not analysed yet"
                      description="Run an analysis from the AI & Policy tab."
                      compact
                    />
                  ) : (
                    <p className="font-display-sm text-lg italic leading-tight text-on-surface">
                      Analysis is recorded for this item. Open the AI &amp; Policy tab for the full
                      recommendation.
                    </p>
                  )}
                  <p className="text-[10px] italic text-on-surface-variant">
                    AI output is advisory. Routing decisions are made by the backend policy engine.
                  </p>
                </DetailCard>
              </div>

              <DetailCard title="Acceptance criteria">
                {detail.acceptanceCriteria ? (
                  <ul className="space-y-3">
                    {detail.acceptanceCriteria
                      .split('\n')
                      .map((line) => line.trim())
                      .filter((line) => line !== '')
                      .map((line) => (
                        <li
                          key={line}
                          className="flex items-start gap-3 text-body-sm text-on-surface-variant"
                        >
                          <CircleDashed
                            aria-hidden
                            className="mt-0.5 size-4 shrink-0 text-on-surface-variant/30"
                          />
                          {line}
                        </li>
                      ))}
                  </ul>
                ) : (
                  <EmptyState title="No acceptance criteria" compact />
                )}
              </DetailCard>

              <DetailCard title="Work context">
                <div className="grid grid-cols-1 gap-8 sm:grid-cols-2">
                  <div>
                    <span className="mb-2 block font-technical-label text-[10px] uppercase text-on-surface-variant/60">
                      Parent
                    </span>
                    {detail.parent ? (
                      <Link
                        to={`/work-items/${detail.parent.id}`}
                        className="text-sm font-medium hover:underline"
                      >
                        {detail.parent.referenceKey}: {detail.parent.summary}
                      </Link>
                    ) : (
                      <span className="text-sm text-on-surface-variant">Independent item</span>
                    )}
                  </div>
                  <div>
                    <span className="mb-2 block font-technical-label text-[10px] uppercase text-on-surface-variant/60">
                      Progress
                    </span>
                    <span className="text-sm font-medium">
                      {detail.progressPercent === null
                        ? 'Not tracked'
                        : `${Math.round(detail.progressPercent)}%`}
                      {detail.childrenSummary
                        ? ` · ${detail.childrenSummary.done}/${detail.childrenSummary.total} children done`
                        : ''}
                    </span>
                  </div>
                </div>
              </DetailCard>
            </>
          ) : null}

          {tab === 'ai' && needsClarification ? (
            <DetailCard title="Clarification needed" variant="ai">
              {isRequester ? (
                <>
                  <p className="mb-4 text-body-sm text-on-surface-variant">
                    The AI could not complete its analysis without more detail. Answer the
                    questions below to send this item back for reanalysis.
                  </p>
                  <ClarificationQuestionsForm
                    questions={analysis.view?.clarificationQuestions ?? []}
                    pending={submitAdditionalInformation.isPending}
                    error={submitAdditionalInformation.error}
                    submitted={submitAdditionalInformation.isSuccess}
                    onSubmit={(answers) =>
                      submitAdditionalInformation.mutate(answers, {
                        onSuccess: () =>
                          notify({
                            tone: 'success',
                            title: 'Answers submitted',
                            description: 'This item is back in AI analysis.',
                          }),
                      })
                    }
                  />
                </>
              ) : (
                <p className="text-body-sm text-on-surface-variant">
                  This item is waiting on the requester to answer clarification questions
                  before it can be reanalyzed.
                </p>
              )}
            </DetailCard>
          ) : null}

          {tab === 'ai' ? (
            <DetailCard
              title="Advisory AI analysis"
              variant="ai"
              action={
                <div className="flex flex-wrap items-center justify-end gap-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    loading={routeWorkItem.isPending}
                    disabled={!canRouteFromAnalysis}
                    onClick={() =>
                      routeWorkItem.mutate(undefined, {
                        onSuccess: (outcome) =>
                          notify({
                            tone: 'success',
                            title: `Routed to ${humanizeEnum(outcome.decision.route)}`,
                            description: outcome.approvalRequest
                              ? 'A review request is now waiting in the correct queue.'
                              : 'The backend completed routing without opening a human review queue.',
                          }),
                        onError: () =>
                          notify({
                            tone: 'error',
                            title: 'Routing could not be completed',
                            description: 'Check that analysis exists and the backend accepted the work item state.',
                          }),
                      })
                    }
                  >
                    Run routing
                  </Button>
                  <Button
                    variant="ai"
                    size="sm"
                    loading={analyze.isPending}
                    onClick={() => analyze.mutate(workItemId)}
                  >
                    Re-run analysis
                  </Button>
                </div>
              }
            >
              {analysis.isLoading ? (
                <LoadingState label="Loading analysis" variant="inline" />
              ) : analysis.view === null ? (
                <EmptyState
                  title="No analysis yet"
                  description="Run an analysis to get an advisory classification, priority and effort estimate."
                  compact
                />
              ) : analysis.view.failed ? (
                <p className="text-body-sm text-error">
                  The last analysis failed: {analysis.view.errorDetail ?? 'no detail was recorded'}.
                  The work item is unaffected.
                </p>
              ) : (
                <div className="space-y-4">
                  <AiConfidenceIndicator confidence={analysis.view.confidence} />
                  {analysis.view.summary ? (
                    <p className="font-display-sm text-lg italic leading-tight text-on-surface">
                      “{analysis.view.summary}”
                    </p>
                  ) : null}
                  <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    {(
                      [
                        ['Classification', analysis.view.classification],
                        ['Recommended priority', analysis.view.recommendedPriority],
                        ['Recommended risk', analysis.view.recommendedRisk],
                        ['Recommended team', analysis.view.recommendedTeam],
                        [
                          'Estimated effort',
                          analysis.view.estimatedHours == null
                            ? null
                            : formatEffort(analysis.view.estimatedHours),
                        ],
                      ] as const
                    )
                      .filter(([, value]) => value !== null && value !== '')
                      .map(([label, value]) => (
                        <div key={label} className="flex justify-between gap-4 text-sm">
                          <dt className="font-technical-label text-[10px] uppercase text-on-surface-variant">
                            {label}
                          </dt>
                          <dd className="text-on-surface">{value}</dd>
                        </div>
                      ))}
                  </dl>
                  {analysis.view.requiredSkills.length > 0 ? (
                    <div className="flex flex-wrap gap-2">
                      {analysis.view.requiredSkills.map((skill) => (
                        <Chip key={skill}>{skill}</Chip>
                      ))}
                    </div>
                  ) : null}
                  {analysis.view.isDemoFallback ? (
                    <p className="text-[10px] italic text-editorial">
                      Demo fallback used after the real Gemini provider was rate-limited.
                    </p>
                  ) : analysis.view.isMock ? (
                    <p className="text-[10px] italic text-editorial">
                      Produced by the deterministic mock AI provider.
                    </p>
                  ) : null}
                  <p className="text-[10px] italic text-on-surface-variant">
                    Analysis alone does not open a manager or team-lead queue. Use Run routing to
                    send the item through backend policy.
                  </p>
                </div>
              )}
            </DetailCard>
          ) : null}

          {tab === 'children' ? (
            <DetailCard title="Child work">
              {children.isLoading ? (
                <LoadingState label="Loading child items" variant="inline" />
              ) : (children.data?.children.length ?? 0) === 0 ? (
                <EmptyState title="No child work items" compact />
              ) : (
                <WorkItemTable
                  rows={toWorkItemRows(children.data?.children ?? [])}
                  caption="Child work items"
                  emptyTitle="No child work items"
                />
              )}
            </DetailCard>
          ) : null}

          {tab === 'dependencies' ? (
            <DetailCard title="Dependencies">
              {links.isLoading ? (
                <LoadingState label="Loading links" variant="inline" />
              ) : (links.data?.outgoing.length ?? 0) + (links.data?.incoming.length ?? 0) === 0 ? (
                <EmptyState title="No links" description="This item has no dependencies." compact />
              ) : (
                <ul className="space-y-3">
                  {[...(links.data?.outgoing ?? []), ...(links.data?.incoming ?? [])].map((link) => (
                    <li
                      key={link.linkId}
                      className="flex items-center gap-4 rounded-lg border border-outline-variant bg-card p-4"
                    >
                      <Chip tone={link.blocking ? 'accent' : 'neutral'}>
                        {humanizeEnum(link.linkType)}
                      </Chip>
                      <Link to={`/work-items/${link.workItemId}`} className="flex-1 hover:underline">
                        <span className="font-technical-label text-xs text-on-surface-variant">
                          {link.referenceKey}
                        </span>{' '}
                        <span className="text-body-sm text-on-surface">{link.summary}</span>
                      </Link>
                      <span className="font-technical-label text-[10px] uppercase text-on-surface-variant">
                        {humanizeEnum(link.status)}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </DetailCard>
          ) : null}

          {tab === 'activity' ? (
            <DetailCard title="Activity">
              {comments.isLoading ? (
                <LoadingState label="Loading comments" variant="inline" />
              ) : (
                <div className="space-y-6">
                  <ActivityTimeline
                    entries={(comments.data ?? []).map((comment) => ({
                      id: comment.id,
                      title: comment.internal ? 'Internal comment' : 'Comment',
                      detail: comment.body,
                      timestamp: comment.createdAt,
                      tone: comment.internal ? 'ai' : 'default',
                    }))}
                  />
                  {detail.permissions.canComment ? (
                    <CommentComposer
                      canPostInternal={detail.permissions.canSeeInternalComments}
                      pending={addComment.isPending}
                      error={addComment.error}
                      onSubmit={(input) => addComment.mutate(input)}
                    />
                  ) : null}
                </div>
              )}
            </DetailCard>
          ) : null}
        </div>

        {/* Right column */}
        <div className="space-y-6 laptop:col-span-4">
          <DetailCard title="Metadata">
            <div className="space-y-4">
              <MetaRow label="Status">
                <StatusBadge status={detail.status} statusCategory={detail.statusCategory} />
              </MetaRow>
              <MetaRow label="Risk">
                {detail.riskLevel ? (
                  <RiskBadge risk={detail.riskLevel} />
                ) : (
                  <span className="text-xs italic text-on-surface-variant">Not assessed</span>
                )}
              </MetaRow>
              <MetaRow label="Assignee">
                {detail.assigneeId ? (
                  <span className="font-technical-label text-xs text-on-surface">
                    {detail.assigneeId.slice(0, 8)}
                  </span>
                ) : (
                  <span className="text-xs italic text-on-surface-variant">Pending assignment</span>
                )}
              </MetaRow>
              <MetaRow label="Due date">
                <span className="font-medium text-on-surface">{formatDate(detail.dueDate)}</span>
              </MetaRow>
              <MetaRow label="Estimated">
                <span className="font-medium text-on-surface">{formatEffort(detail.estimatedHours)}</span>
              </MetaRow>
              <MetaRow label="Remaining">
                <span className="font-medium text-on-surface">{formatEffort(detail.remainingHours)}</span>
              </MetaRow>
            </div>

            <div className="space-y-3 border-t border-outline-variant/30 pt-4">
              {detail.blocked ? (
                <p className="flex items-start gap-2 text-xs text-error">
                  <CheckCircle2 aria-hidden className="mt-0.5 size-3.5 shrink-0" />
                  Blocked since {formatDate(detail.blocked.since)}: {detail.blocked.reason}
                </p>
              ) : canBlock ? (
                <Button
                  variant="danger"
                  fullWidth
                  size="sm"
                  onClick={() => setBlockOpen(true)}
                >
                  Block this item
                </Button>
              ) : null}
            </div>
          </DetailCard>

          <DetailCard title="Jira Integration">
            <div className="space-y-4">
              <div>
                <p className="text-sm font-medium text-on-surface">{jiraStatusText}</p>
                {!jiraLinked ? (
                  <p className="mt-1 text-xs text-on-surface-variant">
                    {jiraStatus.isLoading
                      ? 'Checking Jira configuration for this environment...'
                      : !jiraConfigured
                        ? 'Jira integration is not configured for this environment yet.'
                        : jiraScopeBlocked
                          ? 'You can create Jira issues only for work items inside your team or manager scope.'
                          : canCreateJira
                            ? 'Create one Jira issue for this work item and keep the returned key here.'
                            : 'Only team leads and managers can create a Jira issue from Iris.'}
                  </p>
                ) : null}
              </div>

              {jiraStatus.data ? (
                <div className="rounded-lg border border-outline-variant bg-surface-2 px-3 py-2 text-xs text-on-surface-variant">
                  <span className="font-medium text-on-surface">
                    {jiraConfigured ? 'Connected' : 'Not configured'}
                  </span>
                  {jiraStatus.data.projectKey ? ` · Project ${jiraStatus.data.projectKey}` : ''}
                  {jiraStatus.data.issueType ? ` · Type ${jiraStatus.data.issueType}` : ''}
                </div>
              ) : null}

              {jiraLinked ? (
                <div className="space-y-3">
                  <div className="flex items-center gap-2 text-sm font-semibold text-brand-indigo">
                    <CheckCircle2 aria-hidden className="size-4" />
                    <span>{jira?.issueKey}</span>
                  </div>
                  {jiraOpenable ? (
                    <a
                      href={jira?.issueUrl ?? undefined}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1.5 text-sm font-medium text-brand-indigo hover:underline"
                    >
                      Open in Jira
                      <ExternalLink aria-hidden className="size-3.5" />
                    </a>
                  ) : (
                    <p className="text-xs text-on-surface-variant">Jira link is not available.</p>
                  )}
                </div>
              ) : canCreateJira ? (
                <Button
                  variant="secondary"
                  size="sm"
                  loading={createJiraIssue.isPending}
                  onClick={() =>
                    createJiraIssue.mutate(undefined, {
                      onSuccess: (result) =>
                        notify({
                          tone: 'success',
                          title: `Jira issue ${result.issueKey} created`,
                        }),
                    })
                  }
                >
                  {createJiraIssue.isPending ? 'Creating Jira issue...' : 'Create in Jira'}
                </Button>
              ) : null}

              {jiraErrorMessage ? (
                <p role="alert" className="text-xs text-error">
                  {jiraErrorMessage}
                </p>
              ) : null}
            </div>
          </DetailCard>
        </div>
      </div>

      <BlockWorkItemDialog
        open={blockOpen}
        onClose={() => setBlockOpen(false)}
        pending={block.isPending}
        error={block.error}
        onConfirm={(input) =>
          block.mutate(
            {
              reason: input.reason,
              blockedByWorkItemId: input.blockedByWorkItemId,
              version: detail.version,
            },
            {
              onSuccess: () => {
                setBlockOpen(false);
                notify({ tone: 'success', title: 'Work item blocked' });
              },
            },
          )
        }
      />
    </div>
  );
};

export default WorkItemDetailPage;
