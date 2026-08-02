import { environment } from '@/config/environment';
import { ApiError, type ProblemDetail } from '../errors/ApiError';
import { tokenStore } from './tokenStore';

/**
 * The one HTTP client. Nothing else in the app calls fetch.
 *
 * Responsibilities, and deliberately nothing more:
 *  - base URL, JSON encoding, Authorization header
 *  - correlation ID propagation (X-Correlation-Id in, echoed back on errors)
 *  - problem+json to ApiError mapping
 *  - a single-flight refresh on 401 that matches the backend's rotating refresh
 *    token semantics: refresh tokens are single use, so two concurrent refreshes
 *    would make the second one look like reuse and revoke the whole family.
 *  - AbortSignal pass-through so TanStack Query can cancel
 */

/**
 * A JSON request payload.
 *
 * Deliberately `object` rather than `Record<string, unknown>`: a declared `interface`
 * has no implicit index signature, so DTO interfaces such as `CreateWorkItemRequest`
 * are not assignable to `Record<string, unknown>` even though they serialise fine.
 * Only inline object literals satisfy that constraint, which is why the DTO call sites
 * failed. `object` accepts both interfaces and arrays structurally, and still rejects
 * the primitives that would produce a malformed body. Adding index signatures to the
 * DTOs instead would weaken excess-property checking on every field.
 */
type JsonBody = object | null;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: JsonBody | undefined;
  query?: QueryParams | undefined;
  signal?: AbortSignal | undefined;
  /** Skip the Authorization header and the refresh dance (login, refresh itself). */
  anonymous?: boolean;
  /** Timeout in ms. 0 disables. */
  timeoutMs?: number;
}

export type QueryParams = Record<
  string,
  string | number | boolean | null | undefined | (string | number)[]
>;

const DEFAULT_TIMEOUT_MS = 30_000;

let sessionExpiredHandler: (() => void) | null = null;

/** The auth provider registers here so a dead session unwinds the app exactly once. */
export const onSessionExpired = (handler: () => void): void => {
  sessionExpiredHandler = handler;
};

const newCorrelationId = (): string => {
  const globalCrypto = globalThis.crypto as Crypto | undefined;
  if (globalCrypto && typeof globalCrypto.randomUUID === 'function') {
    return globalCrypto.randomUUID();
  }
  return `web-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
};

export const buildQuery = (params: QueryParams | undefined): string => {
  if (!params) return '';
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue;
    if (Array.isArray(value)) {
      for (const entry of value) search.append(key, String(entry));
    } else {
      search.append(key, String(value));
    }
  }
  const encoded = search.toString();
  return encoded ? `?${encoded}` : '';
};

const parseProblem = async (response: Response): Promise<ProblemDetail | null> => {
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) return null;
  try {
    return (await response.json()) as ProblemDetail;
  } catch {
    return null;
  }
};

const toApiError = (problem: ProblemDetail | null, response: Response, fallbackCorrelationId: string): ApiError =>
  new ApiError({
    message: problem?.title ?? response.statusText ?? 'Request failed',
    status: problem?.status ?? response.status,
    code: (problem?.code as ApiError['code']) ?? 'UNKNOWN',
    detail: problem?.detail ?? null,
    correlationId:
      problem?.correlationId ?? response.headers.get('x-correlation-id') ?? fallbackCorrelationId,
    fieldErrors: problem?.errors ?? [],
    problem,
  });

// ------------------------------------------------------------ refresh flow

let refreshInFlight: Promise<boolean> | null = null;

const performRefresh = async (): Promise<boolean> => {
  const current = tokenStore.current();
  if (!current) return false;

  const response = await fetch(`${environment.apiBaseUrl}/auth/refresh`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'x-correlation-id': newCorrelationId() },
    body: JSON.stringify({ refreshToken: current.refreshToken }),
  }).catch(() => null);

  if (!response || !response.ok) {
    tokenStore.clear();
    return false;
  }

  const tokens = (await response.json()) as {
    accessToken: string;
    refreshToken: string;
    expiresIn: number;
  };
  tokenStore.set(tokens);
  return true;
};

/** Single-flight: concurrent callers await the same rotation. */
const refreshOnce = (): Promise<boolean> => {
  if (!refreshInFlight) {
    refreshInFlight = performRefresh().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
};

// ---------------------------------------------------------------- request

const execute = async <T>(path: string, options: RequestOptions, retrying: boolean): Promise<T> => {
  const correlationId = newCorrelationId();
  const controller = new AbortController();
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;

  const timeout =
    timeoutMs > 0 ? setTimeout(() => controller.abort(new DOMException('timeout', 'TimeoutError')), timeoutMs) : null;

  const onExternalAbort = () => controller.abort(options.signal?.reason);
  options.signal?.addEventListener('abort', onExternalAbort);

  const headers: Record<string, string> = {
    accept: 'application/json',
    'x-correlation-id': correlationId,
  };
  if (options.body !== undefined) headers['content-type'] = 'application/json';

  if (!options.anonymous) {
    const session = tokenStore.current();
    if (session) headers.authorization = `Bearer ${session.accessToken}`;
  }

  let response: Response;
  try {
    response = await fetch(`${environment.apiBaseUrl}${path}${buildQuery(options.query)}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: controller.signal,
    });
  } catch (cause) {
    const aborted = options.signal?.aborted === true;
    const timedOut = cause instanceof DOMException && cause.name === 'TimeoutError';
    throw new ApiError({
      message: aborted ? 'Request cancelled' : timedOut ? 'Request timed out' : 'Network request failed',
      status: 0,
      code: aborted ? 'aborted' : timedOut ? 'timeout' : 'network',
      correlationId,
    });
  } finally {
    if (timeout) clearTimeout(timeout);
    options.signal?.removeEventListener('abort', onExternalAbort);
  }

  if (response.status === 401 && !options.anonymous && !retrying) {
    const refreshed = await refreshOnce();
    if (refreshed) return execute<T>(path, options, true);
    sessionExpiredHandler?.();
  }

  if (!response.ok) {
    throw toApiError(await parseProblem(response), response, correlationId);
  }

  if (response.status === 204) return undefined as T;

  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) return undefined as T;

  try {
    return (await response.json()) as T;
  } catch {
    throw new ApiError({
      message: 'The server returned a response that could not be read.',
      status: response.status,
      code: 'malformed-response',
      correlationId,
    });
  }
};

export const http = {
  get: <T>(path: string, options: Omit<RequestOptions, 'method' | 'body'> = {}) =>
    execute<T>(path, { ...options, method: 'GET' }, false),
  post: <T>(path: string, body?: JsonBody, options: Omit<RequestOptions, 'method' | 'body'> = {}) =>
    execute<T>(path, { ...options, method: 'POST', body: body ?? null }, false),
  put: <T>(path: string, body?: JsonBody, options: Omit<RequestOptions, 'method' | 'body'> = {}) =>
    execute<T>(path, { ...options, method: 'PUT', body: body ?? null }, false),
  delete: <T>(path: string, options: Omit<RequestOptions, 'method' | 'body'> = {}) =>
    execute<T>(path, { ...options, method: 'DELETE' }, false),
};
