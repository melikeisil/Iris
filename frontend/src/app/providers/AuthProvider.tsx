import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client/endpoints';
import { onSessionExpired } from '@/api/client/http';
import { tokenStore } from '@/api/client/tokenStore';
import { queryKeys } from '@/api/queryKeys';
import type { Capability, CurrentUser, SystemRole } from '@/api/types';

/**
 * Authentication and the caller's coarse authorisation surface.
 *
 * Two things this deliberately does not do:
 *  - it does not decide anything from `user.roles[0]`. Users are multi-role (the demo
 *    seed alone has a MANAGER who is also an EMPLOYEE), so every check is a set
 *    membership test.
 *  - it does not treat capabilities as security. They decide which navigation exists;
 *    the backend re-authorises every request regardless.
 */
export interface AuthContextValue {
  user: CurrentUser | null;
  status: 'loading' | 'authenticated' | 'anonymous';
  error: unknown;
  login: (email: string, password: string) => Promise<CurrentUser>;
  logout: () => Promise<void>;
  refetchUser: () => void;
  /** True when the user holds any of the given capabilities. Empty list means true. */
  can: (...capabilities: Capability[]) => boolean;
  hasRole: (...roles: SystemRole[]) => boolean;
  /** Session ended server-side; the shell shows a re-authenticate prompt. */
  sessionExpired: boolean;
  clearSessionExpired: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const queryClient = useQueryClient();
  const [hasSession, setHasSession] = useState<boolean>(() => tokenStore.current() !== null);
  const [sessionExpired, setSessionExpired] = useState(false);

  useEffect(() => tokenStore.subscribe((session) => setHasSession(session !== null)), []);

  useEffect(() => {
    onSessionExpired(() => {
      setSessionExpired(true);
      queryClient.clear();
    });
  }, [queryClient]);

  const query = useQuery({
    queryKey: queryKeys.currentUser,
    queryFn: ({ signal }) => api.auth.me(signal),
    enabled: hasSession,
    staleTime: 5 * 60_000,
    retry: false,
  });

  const login = useCallback(
    async (email: string, password: string) => {
      const response = await api.auth.login(email, password);
      tokenStore.set(response);
      setSessionExpired(false);
      queryClient.setQueryData(queryKeys.currentUser, response.user);
      return response.user;
    },
    [queryClient],
  );

  const logout = useCallback(async () => {
    const session = tokenStore.current();
    if (session) {
      // A failed logout must still clear the client session; the refresh family is
      // revoked server-side on next use anyway.
      await api.auth.logout(session.refreshToken).catch(() => undefined);
    }
    tokenStore.clear();
    queryClient.clear();
    setSessionExpired(false);
  }, [queryClient]);

  const user = query.data ?? null;

  const value = useMemo<AuthContextValue>(() => {
    const capabilities = new Set<string>(user?.capabilities ?? []);
    const roles = new Set<string>(user?.roles ?? []);

    return {
      user,
      status: !hasSession ? 'anonymous' : query.isLoading ? 'loading' : user ? 'authenticated' : 'anonymous',
      error: query.error,
      login,
      logout,
      refetchUser: () => void query.refetch(),
      can: (...required: Capability[]) =>
        required.length === 0 || required.some((capability) => capabilities.has(capability)),
      hasRole: (...required: SystemRole[]) => required.some((role) => roles.has(role)),
      sessionExpired,
      clearSessionExpired: () => setSessionExpired(false),
    };
  }, [user, hasSession, query, login, logout, sessionExpired]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = (): AuthContextValue => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>');
  return context;
};

/** Convenience for components that are only ever rendered behind RequireAuth. */
export const useCurrentUser = (): CurrentUser => {
  const { user } = useAuth();
  if (!user) throw new Error('useCurrentUser used outside an authenticated route');
  return user;
};
