import type { ReactElement, ReactNode } from 'react';
import { render, type RenderOptions } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '@/app/providers/AuthProvider';
import { ToastProvider } from '@/app/providers/ToastProvider';
import { tokenStore } from '@/api/client/tokenStore';

/** A query client with retries off, so a failing test fails fast instead of hanging. */
export const createTestQueryClient = (): QueryClient =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
      mutations: { retry: false },
    },
  });

export interface RenderWithProvidersOptions extends Omit<RenderOptions, 'wrapper'> {
  route?: string;
  queryClient?: QueryClient;
  /** Seed a signed-in session so RequireAuth and useAuth behave. */
  authenticated?: boolean;
}

export const seedSession = (): void => {
  tokenStore.set({ accessToken: 'test-access', refreshToken: 'test-refresh', expiresIn: 900 });
};

export const renderWithProviders = (
  ui: ReactElement,
  { route = '/', queryClient = createTestQueryClient(), authenticated = true, ...options }: RenderWithProvidersOptions = {},
) => {
  if (authenticated) seedSession();
  else tokenStore.clear();

  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <AuthProvider>
          <ToastProvider>{children}</ToastProvider>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );

  return { queryClient, ...render(ui, { wrapper: Wrapper, ...options }) };
};

/** Minimal providers for a component that only needs routing (links, no data). */
export const renderWithRouter = (ui: ReactElement, route = '/') =>
  render(ui, {
    wrapper: ({ children }) => <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>,
  });
