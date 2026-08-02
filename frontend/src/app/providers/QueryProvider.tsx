import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { isApiError } from '@/api/errors/ApiError';

/**
 * Server state lives entirely in TanStack Query. There is no global store mirroring
 * it: a second copy of server data is a second source of truth.
 */
export const createQueryClient = (): QueryClient =>
  new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        gcTime: 5 * 60_000,
        refetchOnWindowFocus: false,
        retry: (failureCount, error) => {
          // Never retry a decision the backend already made: 4xx will not change.
          if (isApiError(error)) {
            if (error.isAborted) return false;
            if (error.status >= 400 && error.status < 500) return false;
          }
          return failureCount < 2;
        },
      },
      mutations: {
        retry: false,
      },
    },
  });

export const QueryProvider = ({ children, client }: { children: ReactNode; client?: QueryClient }) => {
  const [queryClient] = useState(() => client ?? createQueryClient());
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
};
