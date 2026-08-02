import { useNavigate } from 'react-router-dom';
import { Modal } from '@/design-system/components/Modal';
import { Button } from '@/design-system/components/Button';
import { useAuth } from '@/app/providers/AuthProvider';
import { paths } from '@/app/router/paths';

/**
 * Shown when a refresh failed - including the refresh-token-reuse case, where the
 * backend revokes the whole token family on purpose. The user is told what happened
 * rather than being silently bounced to a login screen mid-task.
 */
export const SessionExpiredDialog = () => {
  const { sessionExpired, clearSessionExpired, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <Modal
      open={sessionExpired}
      onClose={clearSessionExpired}
      dismissible={false}
      size="sm"
      title="Your session has ended"
      description="For security, you need to sign in again. Anything you have already saved is safe."
      footer={
        <Button
          onClick={() => {
            clearSessionExpired();
            void logout().then(() => navigate(paths.login, { replace: true }));
          }}
        >
          Sign in again
        </Button>
      }
    >
      <p className="text-body-sm text-outline">
        Unsaved changes in an open form will be lost. If you were part-way through a form,
        copy anything you need before continuing.
      </p>
    </Modal>
  );
};
