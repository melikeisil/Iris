import { BrowserRouter } from 'react-router-dom';
import { QueryProvider } from './providers/QueryProvider';
import { AuthProvider } from './providers/AuthProvider';
import { ToastProvider } from './providers/ToastProvider';
import { AppRoutes } from './router/AppRoutes';
import { ErrorBoundary } from './ErrorBoundary';

/**
 * Provider order matters: AuthProvider must sit inside QueryProvider because it is
 * itself a query, and inside BrowserRouter because sign-out navigates.
 */
export const App = () => (
  <ErrorBoundary>
    <QueryProvider>
      <BrowserRouter>
        <AuthProvider>
          <ToastProvider>
            <AppRoutes />
          </ToastProvider>
        </AuthProvider>
      </BrowserRouter>
    </QueryProvider>
  </ErrorBoundary>
);

export default App;
