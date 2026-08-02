import type { ReactNode } from 'react';
import {
  AlertOctagon,
  AlertTriangle,
  ArrowDown,
  ArrowUp,
  CheckCircle2,
  CircleDashed,
  CircleDot,
  Clock,
  Equal,
  Flame,
  Loader,
  Minus,
  ShieldAlert,
  Sparkles,
  Square,
  TestTube2,
} from 'lucide-react';
import type { Priority, RiskLevel, StateCategory } from '@/api/types';
import { humanizeEnum } from '@/lib/format';
import { cn } from '@/lib/cn';

/**
 * Every badge carries a glyph as well as a colour. Colour alone is not an accessible
 * indicator, and these are exactly the fields (priority, risk, status, AI confidence,
 * approval state) a colour-blind reviewer has to be able to read at a glance.
 */

const BASE = 'inline-flex items-center gap-1 rounded-sm px-1.5 py-0.5 font-mono text-technical-label uppercase tracking-[0.06em] border';

export interface BadgeProps {
  className?: string;
  /** Hide the icon in very dense rows. The text label still carries the meaning. */
  iconOnly?: boolean;
}

// ------------------------------------------------------------------ priority

const PRIORITY_STYLES: Record<Priority, { className: string; Icon: typeof ArrowUp }> = {
  CRITICAL: { className: 'border-error/40 bg-error/10 text-error', Icon: Flame },
  HIGH: { className: 'border-warning-amber/40 bg-warning-amber/10 text-warning-amber', Icon: ArrowUp },
  MEDIUM: { className: 'border-border-structural bg-surface-container-low text-on-surface-variant', Icon: Equal },
  LOW: { className: 'border-border-card bg-field text-outline', Icon: ArrowDown },
};

export const PriorityBadge = ({ priority, className }: BadgeProps & { priority: Priority }) => {
  const style = PRIORITY_STYLES[priority] ?? PRIORITY_STYLES.MEDIUM;
  const { Icon } = style;
  return (
    <span className={cn(BASE, style.className, className)} data-testid="priority-badge">
      <Icon aria-hidden className="size-3" />
      <span className="sr-only">Priority: </span>
      {humanizeEnum(priority)}
    </span>
  );
};

// ---------------------------------------------------------------------- risk

const RISK_STYLES: Record<RiskLevel, { className: string; Icon: typeof AlertTriangle }> = {
  CRITICAL: { className: 'border-error/50 bg-error/15 text-error', Icon: AlertOctagon },
  HIGH: { className: 'border-error/35 bg-error/10 text-error', Icon: ShieldAlert },
  MEDIUM: { className: 'border-warning-amber/40 bg-warning-amber/10 text-warning-amber', Icon: AlertTriangle },
  LOW: { className: 'border-border-card bg-field text-outline', Icon: Minus },
};

export const RiskBadge = ({ risk, className }: BadgeProps & { risk: RiskLevel | null }) => {
  if (!risk) return null;
  const style = RISK_STYLES[risk] ?? RISK_STYLES.LOW;
  const { Icon } = style;
  return (
    <span className={cn(BASE, style.className, className)} data-testid="risk-badge">
      <Icon aria-hidden className="size-3" />
      <span className="sr-only">Risk: </span>
      {humanizeEnum(risk)} risk
    </span>
  );
};

// -------------------------------------------------------------------- status

const CATEGORY_STYLES: Record<StateCategory, { className: string; Icon: typeof CircleDot }> = {
  TODO: { className: 'border-border-card bg-field text-outline', Icon: CircleDashed },
  IN_PROGRESS: { className: 'border-brand-indigo/40 bg-brand-indigo/10 text-brand-indigo', Icon: Loader },
  BLOCKED: { className: 'border-error/40 bg-error/10 text-error', Icon: Square },
  REVIEW: { className: 'border-warning-amber/40 bg-warning-amber/10 text-warning-amber', Icon: CircleDot },
  TESTING: { className: 'border-info/40 bg-info/10 text-info', Icon: TestTube2 },
  DONE: { className: 'border-success-teal/40 bg-success-teal/10 text-success-teal', Icon: CheckCircle2 },
};

export interface StatusBadgeProps extends BadgeProps {
  /** The real backend workflow status, e.g. CODE_REVIEW. Shown verbatim. */
  status: string;
  statusCategory: StateCategory;
}

export const StatusBadge = ({ status, statusCategory, className }: StatusBadgeProps) => {
  const style = CATEGORY_STYLES[statusCategory] ?? CATEGORY_STYLES.TODO;
  const { Icon } = style;
  return (
    <span
      className={cn(BASE, style.className, className)}
      data-testid="status-badge"
      title={`${humanizeEnum(status)} (${humanizeEnum(statusCategory)})`}
    >
      <Icon aria-hidden className="size-3" />
      <span className="sr-only">Status: </span>
      {humanizeEnum(status)}
    </span>
  );
};

// ------------------------------------------------------------------------ AI

export const AiBadge = ({
  label = 'AI generated',
  className,
}: BadgeProps & { label?: string }) => (
  <span className={cn(BASE, 'border-ai-violet/40 bg-ai-violet/10 text-ai-violet', className)} data-testid="ai-badge">
    <Sparkles aria-hidden className="size-3" />
    {label}
  </span>
);

/**
 * Confidence is rendered as a percentage, a filled bar and a worded band. The bar
 * alone would be a colour-only indicator.
 */
export const AiConfidenceIndicator = ({
  confidence,
  className,
}: BadgeProps & { confidence: number | null | undefined }) => {
  if (confidence === null || confidence === undefined) {
    return (
      <span className={cn('font-technical-label text-technical-label text-on-surface-variant', className)} data-testid="ai-confidence">
        Confidence unavailable
      </span>
    );
  }
  // The backend stores confidence as 0..1; tolerate a 0..100 payload too.
  const ratio = confidence > 1 ? confidence / 100 : confidence;
  const percent = Math.round(Math.min(1, Math.max(0, ratio)) * 100);
  const band = percent >= 80 ? 'High' : percent >= 55 ? 'Moderate' : 'Low';

  return (
    <span
      className={cn('inline-flex items-center gap-2', className)}
      data-testid="ai-confidence"
      role="img"
      aria-label={`AI confidence ${percent} percent, ${band}`}
    >
      <span aria-hidden className="h-1.5 w-16 overflow-hidden rounded-full bg-surface-container-low">
        <span className="block h-full rounded-full bg-ai-violet" style={{ width: `${percent}%` }} />
      </span>
      <span aria-hidden className="font-technical-label text-technical-label text-on-surface-variant text-ai-violet">
        {percent}% {band}
      </span>
    </span>
  );
};

// ------------------------------------------------------------- policy / approval

export type PolicyTone = 'auto' | 'review' | 'exception' | 'escalated' | 'decided';

const POLICY_STYLES: Record<PolicyTone, { className: string; Icon: typeof ShieldAlert }> = {
  auto: { className: 'border-success-teal/40 bg-success-teal/10 text-success-teal', Icon: CheckCircle2 },
  review: { className: 'border-brand-indigo/40 bg-brand-indigo/10 text-brand-indigo', Icon: Clock },
  exception: { className: 'border-editorial/40 bg-editorial/10 text-editorial', Icon: ShieldAlert },
  escalated: { className: 'border-error/40 bg-error/10 text-error', Icon: AlertTriangle },
  decided: { className: 'border-border-card bg-field text-outline', Icon: CheckCircle2 },
};

/** The route string comes from the backend policy engine and is never re-derived. */
export const toneForRoute = (route: string): PolicyTone => {
  if (route.includes('AUTO')) return 'auto';
  if (route.includes('MANAGER')) return 'escalated';
  if (route.includes('REVIEW') || route.includes('APPROVAL')) return 'review';
  return 'decided';
};

export const PolicyDecisionBadge = ({
  route,
  policyException = false,
  className,
}: BadgeProps & { route: string; policyException?: boolean }) => {
  const tone: PolicyTone = policyException ? 'exception' : toneForRoute(route);
  const style = POLICY_STYLES[tone];
  const { Icon } = style;
  return (
    <span className={cn(BASE, style.className, className)} data-testid="policy-badge">
      <Icon aria-hidden className="size-3" />
      <span className="sr-only">Policy decision: </span>
      {humanizeEnum(route)}
      {policyException ? ' · exception' : ''}
    </span>
  );
};

export const Chip = ({
  children,
  tone = 'neutral',
  className,
}: {
  children: ReactNode;
  tone?: 'neutral' | 'accent' | 'ai' | 'editorial';
  className?: string;
}) => (
  <span
    className={cn(
      'inline-flex items-center gap-1 rounded-sm border px-1.5 py-0.5 font-mono text-technical-sm uppercase tracking-[0.05em]',
      tone === 'neutral' && 'border-border-card bg-surface-container-low text-outline',
      tone === 'accent' && 'border-brand-indigo/40 bg-brand-indigo/10 text-brand-indigo',
      tone === 'ai' && 'border-ai-violet/40 bg-ai-violet/10 text-ai-violet',
      tone === 'editorial' && 'border-editorial/40 bg-editorial/10 text-editorial',
      className,
    )}
  >
    {children}
  </span>
);

export const SkillChip = ({ skill, className }: { skill: string; className?: string }) => (
  <Chip tone="neutral" className={className}>
    {skill}
  </Chip>
);

/** Marks any surface fed by a demo fixture instead of a real endpoint. */
export const DemoDataBadge = ({ className, reason }: { className?: string; reason?: string }) => (
  <span
    className={cn(
      'inline-flex items-center gap-1 rounded-sm border border-editorial/40 bg-editorial/10 px-1.5 py-0.5 font-mono text-technical-sm uppercase tracking-[0.05em] text-editorial',
      className,
    )}
    title={reason ?? 'This screen uses demo fixtures; no backend endpoint exists yet.'}
    data-testid="demo-data-badge"
  >
    <AlertTriangle aria-hidden className="size-3" />
    Demo data
  </span>
);
