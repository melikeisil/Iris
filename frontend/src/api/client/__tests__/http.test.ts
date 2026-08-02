import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { http } from '../http';
import { tokenStore } from '../tokenStore';
import { ApiError } from '../../errors/ApiError';

/**
 * The transport layer is the one place worth stubbing `fetch` itself: the contract
 * under test is the wire behaviour (headers, problem+json mapping, the 401 refresh
 * dance), not any component's use of it.
 */
const json = (body: unknown, status = 200, headers: Record<string, string> = {}): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json', ...headers },
  });

const problem = (status: number, code: string, extra: Record<string, unknown> = {}): Response =>
  new Response(
    JSON.stringify({
      type: 'about:blank',
      title: 'Concurrent modification',
      status,
      code,
      detail: 'Someone else changed this item.',
      instance: null,
      correlationId: 'corr-123',
      timestamp: '2026-01-05T10:00:00Z',
      errors: [{ field: 'summary', code: 'NotBlank', message: 'Summary is required.' }],
      ...extra,
    }),
    { status, headers: { 'content-type': 'application/problem+json' } },
  );

const fetchMock = vi.fn<(input: string | URL | Request, init?: RequestInit) => Promise<Response>>();

const lastInit = (index = 0): RequestInit => {
  const call = fetchMock.mock.calls[index];
  if (!call) throw new Error(`fetch was not called ${index + 1} time(s)`);
  return call[1] ?? {};
};

const headerOf = (init: RequestInit, name: string): string | undefined => {
  const headers = init.headers as Record<string, string> | undefined;
  return headers?.[name];
};

describe('http client', () => {
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    tokenStore.set({ accessToken: 'access-1', refreshToken: 'refresh-1', expiresIn: 900 });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    tokenStore.clear();
  });

  it('attaches the bearer token and a correlation id', async () => {
    fetchMock.mockResolvedValue(json({ ok: true }));

    await http.get('/work-items/wi-1');

    const init = lastInit();
    expect(headerOf(init, 'authorization')).toBe('Bearer access-1');
    expect(headerOf(init, 'x-correlation-id')).toBeTruthy();
  });

  it('omits the Authorization header on anonymous calls', async () => {
    fetchMock.mockResolvedValue(json({ ok: true }));

    await http.post('/auth/login', { email: 'a@b.c', password: 'secret12' }, { anonymous: true });

    expect(headerOf(lastInit(), 'authorization')).toBeUndefined();
    expect(lastInit().body).toBe(JSON.stringify({ email: 'a@b.c', password: 'secret12' }));
  });

  it('maps an RFC 7807 problem+json body onto ApiError', async () => {
    fetchMock.mockResolvedValue(problem(409, 'CONCURRENT_MODIFICATION'));

    const error = await http.get('/work-items/wi-1').catch((cause: unknown) => cause);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(409);
    expect(apiError.code).toBe('CONCURRENT_MODIFICATION');
    expect(apiError.detail).toBe('Someone else changed this item.');
    expect(apiError.correlationId).toBe('corr-123');
    expect(apiError.fieldErrors).toHaveLength(1);
    expect(apiError.isConcurrentModification).toBe(true);
  });

  it('refreshes once on 401 and replays the original request', async () => {
    fetchMock.mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/auth/refresh')) {
        return Promise.resolve(
          json({ accessToken: 'access-2', refreshToken: 'refresh-2', expiresIn: 900 }),
        );
      }
      const attempted = fetchMock.mock.calls.filter((call) =>
        String(call[0]).endsWith('/work-items/wi-1'),
      ).length;
      return Promise.resolve(attempted > 1 ? json({ id: 'wi-1' }) : problem(401, 'TOKEN_EXPIRED'));
    });

    const result = await http.get<{ id: string }>('/work-items/wi-1');

    expect(result).toEqual({ id: 'wi-1' });
    const urls = fetchMock.mock.calls.map((call) => String(call[0]));
    expect(urls.filter((url) => url.endsWith('/auth/refresh'))).toHaveLength(1);
    expect(tokenStore.current()?.accessToken).toBe('access-2');
    // The replay carries the rotated token, not the stale one.
    expect(headerOf(lastInit(2), 'authorization')).toBe('Bearer access-2');
  });

  it('clears the session when the refresh itself is refused', async () => {
    fetchMock.mockImplementation((input) =>
      Promise.resolve(
        String(input).endsWith('/auth/refresh')
          ? new Response(null, { status: 401 })
          : problem(401, 'TOKEN_EXPIRED'),
      ),
    );

    await expect(http.get('/work-items/wi-1')).rejects.toBeInstanceOf(ApiError);
    expect(tokenStore.current()).toBeNull();
  });

  it('turns a transport failure into a network ApiError rather than letting it escape', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    const error = await http.get('/work-items').catch((cause: unknown) => cause);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe('network');
    expect((error as ApiError).isNetwork).toBe(true);
  });

  it('returns undefined for a 204 rather than trying to parse a body', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(http.delete('/work-items/wi-1/watch')).resolves.toBeUndefined();
  });
});
