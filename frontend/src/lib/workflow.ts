import type { AvailableTransition, StateCategory, WorkItemSummary } from '@/api/types';

/**
 * Presentation mapping over the backend's real status values.
 *
 * The backend already tells us which board column a state belongs to through
 * `statusCategory` (com.smartwork.workflow.domain.StateCategory), which is precisely
 * what lets six workflow profiles share one board. The frontend never invents a status
 * and never sends one: transitions always go through the named transitions the backend
 * returned in `availableTransitions`.
 */

export interface BoardColumn {
  readonly id: StateCategory;
  readonly label: string;
  /** Non-colour indicator, so the column is legible without hue. */
  readonly marker: string;
  readonly tone: 'neutral' | 'progress' | 'blocked' | 'review' | 'testing' | 'done';
}

export const BOARD_COLUMNS: readonly BoardColumn[] = [
  { id: 'TODO', label: 'To do', marker: '○', tone: 'neutral' },
  { id: 'IN_PROGRESS', label: 'In progress', marker: '◐', tone: 'progress' },
  { id: 'BLOCKED', label: 'Blocked', marker: '■', tone: 'blocked' },
  { id: 'REVIEW', label: 'Review', marker: '◆', tone: 'review' },
  { id: 'TESTING', label: 'Testing', marker: '△', tone: 'testing' },
  { id: 'DONE', label: 'Done', marker: '●', tone: 'done' },
];

export const groupByColumn = <T extends { statusCategory: StateCategory; blocked?: boolean }>(
  items: readonly T[],
): Record<StateCategory, T[]> => {
  const grouped: Record<StateCategory, T[]> = {
    TODO: [],
    IN_PROGRESS: [],
    BLOCKED: [],
    REVIEW: [],
    TESTING: [],
    DONE: [],
  };
  for (const item of items) {
    // A blocked item belongs in the Blocked column whatever its workflow state says,
    // because that is the column a person goes looking in.
    const column: StateCategory = item.blocked ? 'BLOCKED' : item.statusCategory;
    (grouped[column] ?? grouped.TODO).push(item);
  }
  return grouped;
};

/** The transitions a user can actually execute right now. */
export const enabledTransitions = (transitions: readonly AvailableTransition[]): AvailableTransition[] =>
  transitions.filter((transition) => transition.enabled);

export const blockedTransitions = (transitions: readonly AvailableTransition[]): AvailableTransition[] =>
  transitions.filter((transition) => !transition.enabled);

/**
 * The order a person should work through their queue: overdue first, then priority,
 * then nearest due date. Pure presentation - the backend already returns `/work-items/my`
 * in its own order and this only applies to client-side re-sorts the user asked for.
 */
const PRIORITY_RANK: Record<string, number> = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };

export const byUrgency = (a: WorkItemSummary, b: WorkItemSummary): number => {
  const priority = (PRIORITY_RANK[a.priority] ?? 9) - (PRIORITY_RANK[b.priority] ?? 9);
  if (priority !== 0) return priority;
  if (a.dueDate && b.dueDate) return a.dueDate.localeCompare(b.dueDate);
  if (a.dueDate) return -1;
  if (b.dueDate) return 1;
  return b.updatedAt.localeCompare(a.updatedAt);
};

export const isOverdue = (dueDate: string | null, today = new Date()): boolean => {
  if (!dueDate) return false;
  const iso = today.toISOString().slice(0, 10);
  return dueDate < iso;
};

export const isDueToday = (dueDate: string | null, today = new Date()): boolean =>
  dueDate === today.toISOString().slice(0, 10);
