import type { StateCategory, WorkItemSummary } from '@/api/types';
import { paths } from '@/app/router/paths';
import { isDueToday, isOverdue } from '@/lib/workflow';
import { formatDate, humanizeEnum } from '@/lib/format';

/**
 * The adapter between the API DTO and what the screens render.
 *
 * Pages never touch a `WorkItemSummary` directly. That keeps the Stitch-derived markup
 * free of backend vocabulary and, more importantly, means the row can carry derived
 * presentation values (the due label, the contextual action verb) without any component
 * re-deriving them differently.
 *
 * Nothing here invents a field. Every value is either copied from the DTO or computed
 * from DTO values only.
 */
export interface WorkItemRow {
  id: string;
  /** e.g. TASK-491. The backend's own reference key. */
  referenceKey: string;
  title: string;
  type: string;
  status: string;
  statusCategory: StateCategory;
  /** Where the item came from, e.g. "Ticket generated" or "Jira imported". */
  source: string;
  priority: WorkItemSummary['priority'];
  riskLevel: WorkItemSummary['riskLevel'];
  blocked: boolean;
  /** "Tomorrow", "3 Aug", "Overdue" or "-". */
  dueLabel: string;
  overdue: boolean;
  dueSoon: boolean;
  remainingLabel: string;
  progressLabel: string;
  progressPercent: number | null;
  /** Contextual verb, mirroring the per-row actions in the Stitch tables. */
  actionLabel: string;
  href: string;
}

/** Matches the workflow statuses that mean "we are waiting on a person to reply". */
export const NEEDS_INFO = /INFO|CLARIF|WAITING|PENDING_REQUESTER/i;

const dueLabelFor = (dueDate: string | null): string => {
  if (!dueDate) return '-';
  if (isOverdue(dueDate)) return 'Overdue';
  if (isDueToday(dueDate)) return 'Today';
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  if (dueDate === tomorrow.toISOString().slice(0, 10)) return 'Tomorrow';
  return formatDate(dueDate);
};

const actionLabelFor = (item: WorkItemSummary): string => {
  if (item.blocked) return 'Review blocker';
  if (NEEDS_INFO.test(item.status)) return 'Answer questions';
  switch (item.statusCategory) {
    case 'REVIEW':
      return 'Open review';
    case 'TESTING':
      return 'Verify solution';
    case 'IN_PROGRESS':
      return 'Continue work';
    case 'DONE':
      return 'View item';
    default:
      return 'Start work';
  }
};

export const toWorkItemRow = (item: WorkItemSummary): WorkItemRow => ({
  id: item.id,
  referenceKey: item.referenceKey,
  title: item.summary,
  type: humanizeEnum(item.workItemType),
  status: humanizeEnum(item.status),
  statusCategory: item.statusCategory,
  source: item.parentId ? 'Child item' : humanizeEnum(item.originType),
  priority: item.priority,
  riskLevel: item.riskLevel,
  blocked: item.blocked,
  dueLabel: dueLabelFor(item.dueDate),
  overdue: isOverdue(item.dueDate),
  dueSoon: isDueToday(item.dueDate),
  remainingLabel: item.estimatedHours === null ? '-' : `${item.estimatedHours}h`,
  progressLabel: item.progressPercent === null ? '-' : `${Math.round(item.progressPercent)}%`,
  progressPercent: item.progressPercent,
  actionLabel: actionLabelFor(item),
  href: paths.workItem(item.id),
});

export const toWorkItemRows = (items: readonly WorkItemSummary[]): WorkItemRow[] =>
  items.map(toWorkItemRow);
