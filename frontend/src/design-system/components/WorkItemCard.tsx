import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { CalendarClock, Clock, Eye } from 'lucide-react';
import type { WorkItemSummary } from '@/api/types';
import { paths } from '@/app/router/paths';
import { formatDate, formatHours } from '@/lib/format';
import { isDueToday, isOverdue } from '@/lib/workflow';
import { PriorityBadge, RiskBadge, StatusBadge } from './badges';
import { WorkItemTypeIcon } from './WorkItemTypeIcon';
import { ProgressIndicator } from './ProgressIndicator';
import { cn } from '@/lib/cn';

export interface WorkItemCardProps {
  item: WorkItemSummary;
  /** Board cards are denser than list cards. */
  density?: 'board' | 'list';
  actions?: ReactNode;
  showProgress?: boolean;
  className?: string;
}

export const WorkItemCard = ({
  item,
  density = 'list',
  actions,
  showProgress = false,
  className,
}: WorkItemCardProps) => {
  const overdue = isOverdue(item.dueDate);
  const dueToday = isDueToday(item.dueDate);

  return (
    <article
      className={cn(
        'rounded-md border bg-card transition-colors hover:border-border-structural hover:bg-field',
        item.blocked ? 'border-error/40' : 'border-border-card',
        density === 'board' ? 'p-3' : 'p-3.5',
        className,
      )}
      data-testid="work-item-card"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="flex min-w-0 items-center gap-2">
          <WorkItemTypeIcon type={item.workItemType} />
          <Link
            to={paths.workItem(item.id)}
            className="font-mono text-technical-label text-brand-indigo hover:underline"
          >
            {item.referenceKey}
          </Link>
        </div>
        <PriorityBadge priority={item.priority} />
      </div>

      <h3 className={cn('mt-2 text-body-sm font-medium text-on-surface', density === 'board' && 'line-clamp-3')}>
        <Link to={paths.workItem(item.id)} className="hover:underline">
          {item.summary}
        </Link>
      </h3>

      <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
        <StatusBadge status={item.status} statusCategory={item.statusCategory} />
        {item.riskLevel && item.riskLevel !== 'LOW' ? <RiskBadge risk={item.riskLevel} /> : null}
        {item.jiraIssueKey ? (
          <span className="font-technical-label text-technical-label text-on-surface-variant" title="Jira issue key (read-only until Phase 8)">
            {item.jiraIssueKey}
          </span>
        ) : null}
      </div>

      {showProgress && item.progressPercent !== null ? (
        <ProgressIndicator percent={item.progressPercent} size="sm" className="mt-3" />
      ) : null}

      <dl className="mt-2.5 flex flex-wrap items-center gap-x-4 gap-y-1">
        {item.dueDate ? (
          <div className="flex items-center gap-1.5">
            <dt className="sr-only">Due date</dt>
            <CalendarClock
              aria-hidden
              className={cn('size-3.5', overdue ? 'text-error' : dueToday ? 'text-warning-amber' : 'text-outline')}
            />
            <dd
              className={cn(
                'font-mono text-technical-label',
                overdue ? 'text-error' : dueToday ? 'text-warning-amber' : 'text-outline',
              )}
            >
              {overdue ? 'Overdue · ' : dueToday ? 'Due today · ' : ''}
              {formatDate(item.dueDate)}
            </dd>
          </div>
        ) : null}
        {item.estimatedHours !== null ? (
          <div className="flex items-center gap-1.5">
            <dt className="sr-only">Estimate</dt>
            <Clock aria-hidden className="size-3.5 text-outline" />
            <dd className="font-technical-label text-technical-label text-on-surface-variant">{formatHours(item.estimatedHours)}</dd>
          </div>
        ) : null}
        {item.storyPoints !== null ? (
          <div className="flex items-center gap-1.5">
            <dt className="sr-only">Story points</dt>
            <dd className="font-technical-label text-technical-label text-on-surface-variant">{item.storyPoints} pts</dd>
          </div>
        ) : null}
        {!item.assigneeId ? (
          <div className="flex items-center gap-1.5">
            <dt className="sr-only">Assignment</dt>
            <Eye aria-hidden className="size-3.5 text-outline" />
            <dd className="font-technical-label text-technical-label text-on-surface-variant">Unassigned</dd>
          </div>
        ) : null}
      </dl>

      {actions ? <div className="mt-3 flex flex-wrap items-center gap-2">{actions}</div> : null}
    </article>
  );
};
