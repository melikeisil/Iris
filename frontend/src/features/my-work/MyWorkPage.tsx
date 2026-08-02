import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PRIORITIES, WORK_ITEM_TYPES } from '@/api/types';
import { ErrorState, LoadingState } from '@/design-system/components/states';
import { MyWorkTable } from './components/MyWorkTable';
import { FilterBar, type FilterDefinition } from './components/FilterBar';
import {
  useCreatedTickets,
  useMyWorkItems,
  useWatchedWorkItems,
} from '@/features/work-items/hooks/useWorkItems';
import { toWorkItemRows, type WorkItemRow } from '@/features/work-items/viewModel';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { humanizeEnum } from '@/lib/format';
import { cn } from '@/lib/cn';

/**
 * My Work, converted from iris_my_work.
 *
 * The source's layout is preserved: a breadcrumb + serif page title, a six-up metric
 * strip in `grid-cols-6`, a tab row, the filter pills, and the dense scrolling table.
 * The mockup's right-hand 320px notifications/activity rail is dropped - it duplicated
 * the top bar's notification menu and there is no activity endpoint to fill it.
 *
 * The separate "My Tickets", "Watching" and "Reviews" routes from the earlier pass are
 * folded in here as tabs, which is what the source shows.
 *
 * All four tabs are real endpoints: GET /work-items/my, /created-by-me and /watching.
 * "Reviews" filters the assigned list to the REVIEW status category, which is the
 * signal the backend actually exposes; there is no reviewer-assignment endpoint.
 */
type Tab = 'assigned' | 'reviews' | 'reported' | 'watching';

const TABS: readonly { id: Tab; label: string }[] = [
  { id: 'assigned', label: 'Assigned to me' },
  { id: 'reviews', label: 'Reviews' },
  { id: 'reported', label: 'Raised by me' },
  { id: 'watching', label: 'Watching' },
];

const Metric = ({
  label,
  value,
  tone,
  caption,
}: {
  label: string;
  value: string | number;
  tone?: 'danger' | 'ai';
  caption?: string;
}) => (
  <div
    className={cn(
      'relative flex flex-col gap-1 overflow-hidden rounded-xl border bg-card p-3',
      tone === 'danger' && 'border-error/30',
      tone === 'ai' && 'border-ai-violet/30',
      !tone && 'border-border-card',
    )}
  >
    {tone ? (
      <span
        aria-hidden
        className={cn(
          'absolute inset-0',
          tone === 'danger' ? 'bg-error/5' : 'bg-ai-violet/5',
        )}
      />
    ) : null}
    <span
      className={cn(
        'relative z-10 font-technical-label text-[10px] uppercase',
        tone === 'danger' && 'text-error',
        tone === 'ai' && 'text-ai-violet',
        !tone && 'text-on-surface-variant',
      )}
    >
      {label}
    </span>
    <span className="relative z-10 flex items-baseline gap-2">
      <span className="font-headline-md text-headline-md text-on-surface-bright">{value}</span>
      {caption ? (
        <span className="text-[9px] leading-tight text-on-surface-variant">{caption}</span>
      ) : null}
    </span>
  </div>
);

export const MyWorkPage = () => {
  const [searchParams] = useSearchParams();
  const [tab, setTab] = useState<Tab>('assigned');
  const [query, setQuery] = useState(searchParams.get('q') ?? '');
  const [type, setType] = useState('');
  const [priority, setPriority] = useState('');
  const [status, setStatus] = useState(searchParams.get('blocked') === 'true' ? 'BLOCKED' : '');
  useDocumentTitle('My Work');

  const assigned = useMyWorkItems();
  const reported = useCreatedTickets(0);
  const watching = useWatchedWorkItems(0);

  const assignedItems = useMemo(() => assigned.data ?? [], [assigned.data]);

  const source = useMemo(() => {
    switch (tab) {
      case 'reported':
        return reported.data?.content ?? [];
      case 'watching':
        return watching.data?.content ?? [];
      case 'reviews':
        return assignedItems.filter((item) => item.statusCategory === 'REVIEW');
      default:
        return assignedItems;
    }
  }, [tab, assignedItems, reported.data, watching.data]);

  const rows: WorkItemRow[] = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return toWorkItemRows(
      source.filter((item) => {
        if (type !== '' && item.workItemType !== type) return false;
        if (priority !== '' && item.priority !== priority) return false;
        if (status === 'BLOCKED' && !item.blocked) return false;
        if (status !== '' && status !== 'BLOCKED' && item.statusCategory !== status) return false;
        if (
          needle !== '' &&
          !item.summary.toLowerCase().includes(needle) &&
          !item.referenceKey.toLowerCase().includes(needle)
        ) {
          return false;
        }
        return true;
      }),
    );
  }, [source, type, priority, status, query]);

  const open = assignedItems.filter((item) => item.statusCategory !== 'DONE');

  const filters: FilterDefinition[] = [
    {
      id: 'type',
      label: 'Type',
      value: type,
      onChange: setType,
      options: WORK_ITEM_TYPES.map((value) => ({ value, label: humanizeEnum(value) })),
    },
    {
      id: 'priority',
      label: 'Priority',
      value: priority,
      onChange: setPriority,
      options: PRIORITIES.map((value) => ({ value, label: humanizeEnum(value) })),
    },
    {
      id: 'status',
      label: 'Status',
      value: status,
      onChange: setStatus,
      options: [
        { value: 'TODO', label: 'To do' },
        { value: 'IN_PROGRESS', label: 'In progress' },
        { value: 'BLOCKED', label: 'Blocked' },
        { value: 'REVIEW', label: 'Review' },
        { value: 'TESTING', label: 'Testing' },
        { value: 'DONE', label: 'Done' },
      ],
    },
  ];

  if (assigned.isLoading) return <LoadingState variant="page" label="Loading your work" />;
  if (assigned.error) return <ErrorState error={assigned.error} />;

  return (
    <div className="flex h-full flex-col">
      <div className="flex flex-col gap-6 px-4 pb-4 pt-8 laptop:px-8">
        <div>
          <h1 className="font-display-lg text-4xl italic text-on-surface-bright laptop:text-display-lg">
            My Work
          </h1>
        </div>

        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 laptop:grid-cols-6">
          <Metric label="Assigned to me" value={open.length} />
          <Metric
            label="In progress"
            value={open.filter((item) => item.statusCategory === 'IN_PROGRESS').length}
          />
          <Metric
            label="Due today"
            value={
              open.filter((item) => item.dueDate === new Date().toISOString().slice(0, 10)).length
            }
          />
          <Metric
            label="Blocked"
            value={open.filter((item) => item.blocked).length}
            tone="danger"
          />
          <Metric
            label="In review"
            value={open.filter((item) => item.statusCategory === 'REVIEW').length}
            tone="ai"
          />
          <Metric label="Raised by me" value={reported.data?.page.totalElements ?? 0} />
        </div>

        <div className="flex flex-col gap-4 border-b border-border-card">
          <div className="flex items-center gap-8 overflow-x-auto" role="tablist">
            {TABS.map((definition) => (
              <button
                key={definition.id}
                type="button"
                role="tab"
                aria-selected={tab === definition.id}
                onClick={() => setTab(definition.id)}
                className={cn(
                  'whitespace-nowrap pb-3 font-body-sm text-body-sm transition-colors',
                  tab === definition.id
                    ? 'border-b-2 border-brand-indigo font-semibold text-on-surface-bright'
                    : 'text-on-surface-variant hover:text-on-surface',
                )}
              >
                {definition.label}
              </button>
            ))}
          </div>
        </div>

        <FilterBar filters={filters} query={query} onQueryChange={setQuery} />
      </div>

      <div className="flex-1 overflow-y-auto px-4 pb-8 laptop:px-8">
        <MyWorkTable rows={rows} />
      </div>
    </div>
  );
};

export default MyWorkPage;
