import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../providers/AuthProvider';
import { LoadingState } from '@/design-system/components/states';
import { paths } from '../router/paths';

export const RequireAuth = () => {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background p-6">
        <LoadingState label="Restoring your session" variant="inline" />
      </div>
    );
  }

  if (status === 'anonymous') {
    // `from` lets the login page return the user to where they were headed.
    return <Navigate to={paths.login} replace state={{ from: location.pathname + location.search }} />;
  }

  return <Outlet />;
};
