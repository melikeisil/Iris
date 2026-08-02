import type { ReactNode } from 'react';
import { Outlet } from 'react-router-dom';
import { useAuth } from '../providers/AuthProvider';
import { PermissionState } from '@/design-system/components/states';
import type { Capability } from '@/api/types';

/**
 * Route-level gate. Hiding a route is presentation, not security - the backend
 * authorises every request independently - but showing a user a screen they cannot use
 * is its own kind of failure.
 */
export const RequireCapability = ({
  anyOf,
  children,
}: {
  anyOf: readonly Capability[];
  children?: ReactNode;
}) => {
  const { can } = useAuth();

  if (!can(...anyOf)) {
    return (
      <PermissionState
        title="This area is not available to your role"
        description="Your account does not hold the capability this screen requires. If you believe that is wrong, ask an administrator to review your roles."
      />
    );
  }

  return <>{children ?? <Outlet />}</>;
};
