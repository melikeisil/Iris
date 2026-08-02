import { ApiError, isApiError } from './ApiError';

/**
 * One human sentence per backend error code. The backend's `detail` is preferred when
 * present - it is written for the caller - and this map is the fallback so the user
 * never sees a raw code or a JSON dump.
 */
const MESSAGES: Record<string, string> = {
  VALIDATION_FAILED: 'Some fields need attention before this can be saved.',
  MALFORMED_REQUEST: 'That request could not be understood. Please try again.',
  LINK_CYCLE_DETECTED: 'That link would create a circular dependency.',
  INVALID_LINK_TYPE: 'That relationship type is not allowed between these two items.',
  MALFORMED_FILTER: 'One of the filters is not valid.',
  UNAUTHENTICATED: 'Your session has ended. Please sign in again.',
  INVALID_CREDENTIALS: 'That email and password combination was not recognised.',
  TOKEN_EXPIRED: 'Your session has expired. Please sign in again.',
  REFRESH_TOKEN_REUSED:
    'This session was signed out for security reasons. Please sign in again.',
  ACCOUNT_DISABLED: 'This account is disabled. Contact your administrator.',
  FORBIDDEN: 'You do not have permission to do that.',
  TRANSITION_NOT_PERMITTED: 'You are not permitted to make this workflow transition.',
  ROUTE_NOT_PERMITTED: 'You do not have authority over this approval route.',
  LEVEL_RESTRICTION_VIOLATED: 'Your employee level does not permit this action.',
  NOT_FOUND: 'That item could not be found, or you cannot see it.',
  CONCURRENT_MODIFICATION:
    'Someone else changed this item while you were editing. Reload to see their changes.',
  DUPLICATE_RESOURCE: 'That already exists.',
  ALREADY_DECIDED: 'This request has already been decided by someone else.',
  INVALID_TRANSITION: 'That transition is not available from the current state.',
  TRANSITION_GUARD_FAILED: 'This transition is blocked by a workflow rule.',
  RECOMMENDATION_STALE:
    'The work item changed after this analysis ran. Re-run the analysis before deciding.',
  SPRINT_CAPACITY_EXCEEDED: 'That would exceed the sprint capacity.',
  TYPE_RULE_VIOLATED: 'That breaks a rule for this work item type.',
  RATE_LIMITED: 'Too many requests. Please wait a moment and try again.',
  INTERNAL_ERROR: 'Something went wrong on the server.',
  AI_SERVICE_UNAVAILABLE:
    'AI analysis is unavailable right now. Everything else still works.',
  JIRA_NOT_CONFIGURED: 'Jira integration is not configured yet.',
  JIRA_AUTHENTICATION_FAILED: 'Jira credentials were rejected.',
  JIRA_INVALID_CONFIGURATION: 'The Jira project or issue type configuration is invalid.',
  JIRA_UNAVAILABLE: 'Jira is unavailable right now.',
  network: 'Could not reach the server. Check your connection and try again.',
  timeout: 'The server took too long to respond.',
  aborted: 'That request was cancelled.',
  'malformed-response': 'The server returned an unexpected response.',
};

export const errorMessage = (error: unknown): string => {
  if (isApiError(error)) {
    if (error.detail && error.detail.trim() !== '') return error.detail;
    return MESSAGES[error.code] ?? error.message ?? 'Something went wrong.';
  }
  if (error instanceof Error && error.message) return error.message;
  return 'Something went wrong.';
};

export const errorTitle = (error: unknown): string => {
  if (!isApiError(error)) return 'Something went wrong';
  if (error.isNetwork) return 'Connection problem';
  if (error.isNotFound) return 'Not found';
  if (error.isAuthorization) return 'Not permitted';
  if (error.isConflict) return 'Conflict';
  if (error.isValidation) return 'Check these fields';
  if (error.status >= 500) return 'Server error';
  return 'Something went wrong';
};

export const fieldErrorMap = (error: unknown): Record<string, string> => {
  if (!isApiError(error)) return {};
  return Object.fromEntries(error.fieldErrors.map((f) => [f.field, f.message]));
};

export { ApiError, isApiError };
