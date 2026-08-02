import { useNavigate } from 'react-router-dom';
import { LogOut, UserRound } from 'lucide-react';
import { Menu } from '@/design-system/components/Menu';
import { Avatar } from '@/design-system/components/Avatar';
import { useAuth } from '@/app/providers/AuthProvider';
import { paths } from '@/app/router/paths';

/**
 * The avatar button in the top bar. The Stitch markup used a remote portrait from
 * lh3.googleusercontent.com; this renders initials instead - no remote image, and the
 * signed-in identity never leaves the browser.
 */
export const UserMenu = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  if (!user) return null;

  const roles = user.roles.join(' · ');

  return (
    <Menu
      label="Account menu"
      items={[
        {
          id: 'identity',
          label: (
            <span className="flex flex-col">
              <span className="text-body-sm text-on-surface">{user.fullName}</span>
              <span className="font-technical-label text-technical-sm uppercase text-on-surface-variant">
                {roles}
              </span>
            </span>
          ),
          icon: <UserRound className="size-4" aria-hidden />,
          disabled: true,
        },
        {
          id: 'sign-out',
          label: 'Sign out',
          icon: <LogOut className="size-4" aria-hidden />,
          destructive: true,
          sectionLabel: 'Session',
          onSelect: () => {
            void logout().then(() => navigate(paths.login, { replace: true }));
          },
        },
      ]}
      trigger={(props) => (
        <button
          {...props}
          type="button"
          className="ml-2 size-8 overflow-hidden rounded-full border border-border-structural transition-colors hover:border-primary"
        >
          <Avatar name={user.fullName} size="sm" className="size-full border-0 bg-transparent" />
          <span className="sr-only">Account menu for {user.fullName}</span>
        </button>
      )}
    />
  );
};
