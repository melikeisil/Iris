import {
  Beaker,
  Bug,
  CheckSquare,
  FileText,
  FlaskConical,
  Hexagon,
  LifeBuoy,
  Layers,
  ListTodo,
  Siren,
  Wrench,
} from 'lucide-react';
import type { WorkItemType } from '@/api/types';
import { humanizeEnum } from '@/lib/format';
import { cn } from '@/lib/cn';

/**
 * A distinct glyph per type - shape carries the meaning, colour only reinforces it.
 * Types come straight from com.smartwork.workitem.domain.WorkItemType.
 */
const ICONS: Record<WorkItemType, { Icon: typeof Bug; className: string }> = {
  REQUEST: { Icon: LifeBuoy, className: 'text-info' },
  INCIDENT: { Icon: Siren, className: 'text-error' },
  EPIC: { Icon: Hexagon, className: 'text-ai-violet' },
  FEATURE: { Icon: Layers, className: 'text-brand-indigo' },
  STORY: { Icon: ListTodo, className: 'text-success-teal' },
  BUG: { Icon: Bug, className: 'text-error' },
  TASK: { Icon: CheckSquare, className: 'text-on-surface-variant' },
  TECHNICAL_DEBT: { Icon: Wrench, className: 'text-warning-amber' },
  RESEARCH: { Icon: FlaskConical, className: 'text-ai-violet' },
  TEST: { Icon: Beaker, className: 'text-info' },
  DOCUMENTATION: { Icon: FileText, className: 'text-outline' },
  SUBTASK: { Icon: CheckSquare, className: 'text-outline' },
};

export interface WorkItemTypeIconProps {
  type: WorkItemType;
  className?: string;
  /** Render the type name next to the glyph. */
  withLabel?: boolean;
}

export const WorkItemTypeIcon = ({ type, className, withLabel = false }: WorkItemTypeIconProps) => {
  const entry = ICONS[type] ?? ICONS.TASK;
  const { Icon } = entry;
  return (
    <span className={cn('inline-flex items-center gap-1.5', className)} data-testid="work-item-type-icon">
      <Icon aria-hidden className={cn('size-4 shrink-0', entry.className)} />
      {withLabel ? (
        <span className="font-technical-label text-technical-label text-on-surface-variant">{humanizeEnum(type)}</span>
      ) : (
        <span className="sr-only">{humanizeEnum(type)}</span>
      )}
    </span>
  );
};
