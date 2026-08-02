import type { TokenResponse } from '../types';

/**
 * Session storage for the token pair.
 *
 * Tokens are held in module state and mirrored to localStorage so a reload keeps the
 * session. They are never written to the console or to any log: the only code that
 * touches the raw strings is this module and the fetch layer's Authorization header.
 */
const ACCESS_KEY = 'iris.access';
const REFRESH_KEY = 'iris.refresh';
const EXPIRY_KEY = 'iris.expires';

export interface StoredSession {
  accessToken: string;
  refreshToken: string;
  /** Epoch millis at which the access token stops being accepted. */
  expiresAt: number;
}

let session: StoredSession | null = null;
const listeners = new Set<(session: StoredSession | null) => void>();

const safeStorage = (): Storage | null => {
  try {
    return window.localStorage;
  } catch {
    // Private browsing or a locked-down origin. The session simply does not survive
    // a reload; that is preferable to crashing the app on boot.
    return null;
  }
};

const notify = () => {
  for (const listener of listeners) listener(session);
};

export const tokenStore = {
  load(): StoredSession | null {
    if (session) return session;
    const storage = safeStorage();
    if (!storage) return null;
    const accessToken = storage.getItem(ACCESS_KEY);
    const refreshToken = storage.getItem(REFRESH_KEY);
    const expiresAt = Number(storage.getItem(EXPIRY_KEY) ?? '0');
    if (!accessToken || !refreshToken) return null;
    session = { accessToken, refreshToken, expiresAt };
    return session;
  },

  set(tokens: Pick<TokenResponse, 'accessToken' | 'refreshToken' | 'expiresIn'>): StoredSession {
    session = {
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      expiresAt: Date.now() + tokens.expiresIn * 1000,
    };
    const storage = safeStorage();
    if (storage) {
      storage.setItem(ACCESS_KEY, session.accessToken);
      storage.setItem(REFRESH_KEY, session.refreshToken);
      storage.setItem(EXPIRY_KEY, String(session.expiresAt));
    }
    notify();
    return session;
  },

  clear(): void {
    session = null;
    const storage = safeStorage();
    if (storage) {
      storage.removeItem(ACCESS_KEY);
      storage.removeItem(REFRESH_KEY);
      storage.removeItem(EXPIRY_KEY);
    }
    notify();
  },

  current(): StoredSession | null {
    return session ?? tokenStore.load();
  },

  /** True when the access token is gone or within 15s of expiry. */
  isStale(): boolean {
    const current = tokenStore.current();
    if (!current) return true;
    return current.expiresAt - Date.now() < 15_000;
  },

  subscribe(listener: (session: StoredSession | null) => void): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};
