/**
 * The backend's RFC 7807 problem+json body, extended with a stable `code` and a
 * correlation ID (com.smartwork.common.api.ApiErrorResponse).
 */
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  code: string;
  detail: string | null;
  instance: string | null;
  correlationId: string | null;
  timestamp: string;
  errors: FieldError[];
}

export interface FieldError {
  field: string;
  code: string;
  message: string;
}

/** Exactly the codes com.smartwork.common.error.ErrorCode can emit. */
export const ERROR_CODES = [
  'VALIDATION_FAILED',
  'MALFORMED_REQUEST',
  'LINK_CYCLE_DETECTED',
  'INVALID_LINK_TYPE',
  'MALFORMED_FILTER',
  'UNAUTHENTICATED',
  'INVALID_CREDENTIALS',
  'TOKEN_EXPIRED',
  'REFRESH_TOKEN_REUSED',
  'ACCOUNT_DISABLED',
  'FORBIDDEN',
  'TRANSITION_NOT_PERMITTED',
  'ROUTE_NOT_PERMITTED',
  'LEVEL_RESTRICTION_VIOLATED',
  'NOT_FOUND',
  'CONCURRENT_MODIFICATION',
  'DUPLICATE_RESOURCE',
  'ALREADY_DECIDED',
  'INVALID_TRANSITION',
  'TRANSITION_GUARD_FAILED',
  'RECOMMENDATION_STALE',
  'SPRINT_CAPACITY_EXCEEDED',
  'TYPE_RULE_VIOLATED',
  'RATE_LIMITED',
  'INTERNAL_ERROR',
  'AI_SERVICE_UNAVAILABLE',
  'JIRA_UNAVAILABLE',
] as const;

export type ErrorCode = (typeof ERROR_CODES)[number];

/** Transport-level failures that never reached the backend. */
export type ClientErrorKind = 'network' | 'timeout' | 'aborted' | 'malformed-response';

export class ApiError extends Error {
  readonly status: number;
  readonly code: ErrorCode | ClientErrorKind | 'UNKNOWN';
  readonly detail: string | null;
  readonly correlationId: string | null;
  readonly fieldErrors: FieldError[];
  readonly problem: ProblemDetail | null;

  constructor(init: {
    message: string;
    status: number;
    code: ApiError['code'];
    detail?: string | null;
    correlationId?: string | null;
    fieldErrors?: FieldError[];
    problem?: ProblemDetail | null;
  }) {
    super(init.message);
    this.name = 'ApiError';
    this.status = init.status;
    this.code = init.code;
    this.detail = init.detail ?? null;
    this.correlationId = init.correlationId ?? null;
    this.fieldErrors = init.fieldErrors ?? [];
    this.problem = init.problem ?? null;
  }

  get isAuthentication(): boolean {
    return (
      this.status === 401 ||
      this.code === 'UNAUTHENTICATED' ||
      this.code === 'TOKEN_EXPIRED' ||
      this.code === 'REFRESH_TOKEN_REUSED'
    );
  }

  get isAuthorization(): boolean {
    return this.status === 403;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }

  get isConflict(): boolean {
    return this.status === 409;
  }

  get isConcurrentModification(): boolean {
    return this.code === 'CONCURRENT_MODIFICATION';
  }

  get isValidation(): boolean {
    return this.code === 'VALIDATION_FAILED' || this.fieldErrors.length > 0;
  }

  get isAiUnavailable(): boolean {
    return this.code === 'AI_SERVICE_UNAVAILABLE';
  }

  get isNetwork(): boolean {
    return this.code === 'network' || this.code === 'timeout';
  }

  get isAborted(): boolean {
    return this.code === 'aborted';
  }
}

export const isApiError = (error: unknown): error is ApiError => error instanceof ApiError;
