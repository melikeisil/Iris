import { Bell } from 'lucide-react';
import { DemoDataBadge } from '@/design-system/components/badges';
import { Menu, type MenuItemDefinition } from '@/design-system/components/Menu';
import { useDemoNotifications } from '@/demo/hooks';
import { formatRelative } from '@/lib/format';

/**
 * The bell in the top bar.
 *
 * There is no notification module in this backend at all - no endpoint, no table - so
 * this reads a fixture and says so. It is never presented as live: the menu header
 * carries the same <DemoDataBadge /> every other fixture-backed surface uses, and with
 * demo mode off the menu states that notifications are unavailable rather than showing
 * an empty list that would read as "nothing new". See docs/demo-mode.md.
 */
const demoHeader = (
  <span className="flex items-center gap-2">
    <DemoDataBadge reason="No notification endpoint or table exists in this backend." />
  </span>
);

export const NotificationMenu = () => {
  const { data, isError } = useDemoNotifications();
  const notifications = isError ? [] : (data ?? []);
  const unread = notifications.filter((notification) => !notification.read).length;

  const items: MenuItemDefinition[] = isError
    ? [
        {
          id: 'unavailable',
          disabled: true,
          sectionLabel: demoHeader,
          label: 'Notifications are not available - this backend has no notification endpoint.',
        },
      ]
    : notifications.length === 0
      ? [{ id: 'empty', disabled: true, sectionLabel: demoHeader, label: 'Nothing new' }]
      : notifications.slice(0, 6).map((notification, index) => ({
          id: notification.id,
          disabled: true,
          ...(index === 0 ? { sectionLabel: demoHeader } : {}),
          label: (
            <span className="flex flex-col gap-0.5">
              <span className="text-body-sm text-on-surface">{notification.title}</span>
              <span className="text-technical-sm uppercase tracking-wider text-on-surface-variant">
                {formatRelative(notification.createdAt)}
              </span>
            </span>
          ),
        }));

  return (
    <Menu
      label="Notifications"
      items={items}
      trigger={(props) => (
        <button
          {...props}
          type="button"
          className="group relative rounded-full p-2 transition-colors hover:bg-white/5"
        >
          <Bell
            aria-hidden
            className="size-5 text-on-surface-variant group-hover:text-on-surface"
            strokeWidth={1.75}
          />
          {unread > 0 ? (
            <span className="absolute right-1 top-1 flex size-4 items-center justify-center rounded-full border-2 border-background bg-error text-[9px] font-bold text-on-error">
              {unread}
            </span>
          ) : null}
          <span className="sr-only">Notifications, {unread} unread (demo data)</span>
        </button>
      )}
    />
  );
};
