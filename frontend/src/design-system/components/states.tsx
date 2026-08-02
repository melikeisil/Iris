import { useState, type ReactNode } from 'react';
import {
  AlertTriangle,
  Inbox,
  Lock,
  RefreshCw,
  SearchX,
  ServerCrash,
  Sparkles,
  WifiOff,
} from 'lucide-react';
import { ApiError, isApiError } from '@/api/errors/ApiError';
import { errorMessage, errorTitle } from '@/api/errors/messages';
import { Button } from './Button';
import { cn } from '@/lib/cn';

/**
 * The five screen states every data surface needs. A blank page or a raw JSON dump is
 * never acceptable, so these are deliberately the only way a feature renders a
 * non-happy path.
 */

// ------------------------------------------------------------------- loading

export const Skeleton = ({ className }: { className?: string }) => (
  <div className={cn('skeleton', className)} aria-hidden />
);

export interface LoadingStateProps {
  label?: string;
  /** `rows` for a list, `cards` for a metric grid, `inline` for a small region. */
  variant?: 'rows' | 'cards' | 'inline' | 'page';
  rows?: number;
  className?: string;
}

export const LoadingState = ({
  label = 'Loading',
  variant = 'rows',
  rows = 4,
  className,
}: LoadingStateProps) => (
  <div className={cn('animate-fade-in', className)} role="status" aria-live="polite" aria-busy="true">
    <span className="sr-only">{label}</span>
    {variant === 'inline' ? (
      <Skeleton className="h-5 w-40" />
    ) : variant === 'cards' ? (
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 laptop:grid-cols-4">
        {Array.from({ length: rows }).map((_, index) => (
          <Skeleton key={index} className="h-24" />
        ))}
      </div>
    ) : variant === 'page' ? (
      <div className="space-y-4">
        <Skeleton className="h-9 w-64" />
        <Skeleton className="h-4 w-96" />
        <div className="grid grid-cols-1 gap-4 laptop:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <Skeleton key={index} className="h-24" />
          ))}
        </div>
        <Skeleton className="h-72" />
      </div>
    ) : (
      <div className="space-y-2">
        {Array.from({ length: rows }).map((_, index) => (
          <Skeleton key={index} className="h-12" />
        ))}
      </div>
    )}
  </div>
);

/** A quiet strip shown while a background refetch runs over already-visible data. */
export const RefreshingBar = ({ visible }: { visible: boolean }) =>
  visible ? (
    <div
      className="flex items-center gap-2 border-b border-border-card bg-field px-4 py-1.5 font-technical-label text-technical-label text-on-surface-variant"
      role="status"
      aria-live="polite"
    >
      <RefreshCw aria-hidden className="size-3 animate-spin" />
      Refreshing
    </div>
  ) : null;

// --------------------------------------------------------------------- empty

export interface EmptyStateProps {
  title: string;
  description?: ReactNode;
  icon?: ReactNode;
  action?: ReactNode;
  className?: string;
  /** Use when a filter produced no rows, as opposed to there being no data at all. */
  filtered?: boolean;
  /** Tighter padding and no border, for an empty region inside an existing card. */
  compact?: boolean;
}

export const EmptyState = ({
  title,
  description,
  icon,
  action,
  className,
  filtered = false,
  compact = false,
}: EmptyStateProps) => (
  <div
    className={cn(
      'flex flex-col items-center justify-center gap-3 text-center',
      compact
        ? 'px-2 py-6'
        : 'rounded-lg border border-dashed border-border-card bg-card px-6 py-12',
      className,
    )}
    data-testid="empty-state"
  >
    <span aria-hidden className="text-outline">
      {icon ?? (filtered ? <SearchX className="size-7" /> : <Inbox className="size-7" />)}
    </span>
    <h3 className={cn(compact ? 'text-body-sm font-semibold text-on-surface' : 'text-headline-md text-on-surface')}>
      {title}
    </h3>
    {description ? (
      <p className="max-w-md text-body-sm text-outline">{description}</p>
    ) : null}
    {action}
  </div>
);

// ---------------------------------------------------------------- permission

export const PermissionState = ({
  title = 'You do not have access to this',
  description,
  className,
}: {
  title?: string;
  description?: ReactNode;
  className?: string;
}) => (
  <div
    className={cn(
      'flex flex-col items-center justify-center gap-3 rounded-md border border-border-card bg-card px-6 py-12 text-center',
      className,
    )}
    data-testid="permission-state"
    role="region"
    aria-label="Access restricted"
  >
    <Lock aria-hidden className="size-7 text-warning-amber" />
    <h3 className="text-headline-md text-on-surface">{title}</h3>
    <p className="max-w-md text-body-sm text-outline">
      {description ??
        'Your role or your relationship to this item does not include this action. Ask the owning team lead if you need it.'}
    </p>
  </div>
);

// --------------------------------------------------------------------- error

const iconFor = (error: unknown) => {
  if (!isApiError(error)) return <AlertTriangle className="size-7 text-error" />;
  if (error.isNetwork) return <WifiOff className="size-7 text-warning-amber" />;
  if (error.isAuthorization) return <Lock className="size-7 text-warning-amber" />;
  if (error.isAiUnavailable) return <Sparkles className="size-7 text-ai-violet" />;
  if (error.status >= 500) return <ServerCrash className="size-7 text-error" />;
  return <AlertTriangle className="size-7 text-error" />;
};

export interface ErrorStateProps {
  error: unknown;
  onRetry?: (() => void) | undefined;
  /** Compact form for inline panels rather than a whole page. */
  compact?: boolean;
  className?: string;
}

export const ErrorState = ({ error, onRetry, compact = false, className }: ErrorStateProps) => {
  const [showDetails, setShowDetails] = useState(false);
  const apiError = isApiError(error) ? error : null;

  return (
    <div
      className={cn(
        'rounded-md border border-error/30 bg-error/[0.06]',
        compact ? 'p-3' : 'flex flex-col items-center gap-3 p-8 text-center',
        className,
      )}
      role="alert"
      data-testid="error-state"
    >
      {!compact ? <span aria-hidden>{iconFor(error)}</span> : null}
      <div className={cn(compact && 'flex items-start gap-2')}>
        {compact ? <AlertTriangle aria-hidden className="mt-0.5 size-4 shrink-0 text-error" /> : null}
        <div className={cn(!compact && 'space-y-1')}>
          <h3 className={cn(compact ? 'text-body-sm font-semibold text-on-surface' : 'text-headline-md text-on-surface')}>
            {errorTitle(error)}
          </h3>
          <p className="max-w-lg text-body-sm text-on-surface-variant">{errorMessage(error)}</p>
        </div>
      </div>

      {apiError?.fieldErrors.length ? (
        <ul className="w-full max-w-lg space-y-1 text-left text-body-sm text-error">
          {apiError.fieldErrors.map((fieldError) => (
            <li key={`${fieldError.field}-${fieldError.code}`}>
              <span className="font-technical-label text-technical-label text-on-surface-variant text-outline">{fieldError.field}</span>{' '}
              {fieldError.message}
            </li>
          ))}
        </ul>
      ) : null}

      <div className={cn('flex items-center gap-2', compact && 'mt-2')}>
        {onRetry ? (
          <Button size="sm" variant="secondary" leadingIcon={<RefreshCw className="size-3.5" />} onClick={onRetry}>
            Try again
          </Button>
        ) : null}
        {apiError ? (
          <Button size="sm" variant="ghost" onClick={() => setShowDetails((value) => !value)}>
            {showDetails ? 'Hide technical details' : 'Technical details'}
          </Button>
        ) : null}
      </div>

      {showDetails && apiError ? (
        <dl className="w-full max-w-lg space-y-1 rounded border border-border-card bg-field p-3 text-left">
          <div className="flex justify-between gap-4">
            <dt className="technical-label">Code</dt>
            <dd className="font-technical-label text-technical-label text-on-surface-variant">{apiError.code}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="technical-label">HTTP</dt>
            <dd className="font-technical-label text-technical-label text-on-surface-variant">{apiError.status || 'n/a'}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="technical-label">Correlation ID</dt>
            <dd className="font-technical-label text-technical-label text-on-surface-variant break-all">{apiError.correlationId ?? 'none'}</dd>
          </div>
        </dl>
      ) : null}
    </div>
  );
};

/**
 * Partial failure: some panels loaded, one did not. The page keeps working and only
 * the failed region degrades.
 */
export const PartialFailureNotice = ({
  label,
  error,
  onRetry,
}: {
  label: string;
  error: unknown;
  onRetry?: () => void;
}) => (
  <div
    className="flex items-center justify-between gap-3 rounded border border-warning-amber/30 bg-warning-amber/[0.07] px-3 py-2"
    role="status"
  >
    <p className="text-body-sm text-on-surface-variant">
      <AlertTriangle aria-hidden className="mr-1.5 inline size-3.5 text-warning-amber" />
      {label} could not be loaded. {errorMessage(error)}
    </p>
    {onRetry ? (
      <Button size="sm" variant="ghost" onClick={onRetry}>
        Retry
      </Button>
    ) : null}
  </div>
);

export { ApiError };
