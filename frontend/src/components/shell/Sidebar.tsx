import { Link } from 'react-router-dom';
import { HelpCircle, Settings } from 'lucide-react';
import { IrisMark } from './IrisMark';
import { Avatar } from '@/design-system/components/Avatar';
import { useAuth } from '@/app/providers/AuthProvider';
import { visibleSections, isNavItemActive, type NavItem } from '@/config/navigation';
import { brand } from '@/config/brand';
import { cn } from '@/lib/cn';

/**
 * The application sidebar, converted from
 * iris_application_shell_employee_expanded/code.html and
 * iris_employee_dashboard_default/code.html.
 *
 * Structural values are taken verbatim from that markup:
 *   aside  w-[248px] bg-surface-container-lowest border-r border-[#2A3443] h-screen sticky
 *   item   flex items-center gap-3 px-3 py-2 rounded-lg
 *   active bg-secondary-container text-on-secondary-container + .active-glow
 *   header font-technical-label text-[10px] text-on-surface-variant/50 tracking-[0.2em]
 *
 * The collapsed variant is the 80px icon rail from
 * iris_employee_dashboard_1024px_preview/code.html.
 */
export interface SidebarProps {
  collapsed?: boolean;
  /** Per-item counters, e.g. the AI review queue depth. Keyed by NavItem.id. */
  badges?: Readonly<Record<string, number>>;
  onNavigate?: () => void;
  className?: string;
  /** The current pathname; passed in so the mobile drawer highlights correctly. */
  pathname: string;
}

const SidebarLink = ({
  item,
  collapsed,
  badge,
  active,
  onNavigate,
}: {
  item: NavItem;
  collapsed: boolean;
  badge: number;
  active: boolean;
  onNavigate?: () => void;
}) => {
  const Icon = item.icon;
  return (
    <Link
      to={item.to}
      onClick={onNavigate}
      aria-current={active ? 'page' : undefined}
      title={collapsed ? item.label : undefined}
      className={cn(
        'group flex items-center gap-3 rounded-lg px-3 py-2 transition-all',
        collapsed && 'justify-center px-0 py-3',
        active
          ? 'active-glow bg-secondary-container text-on-secondary-container'
          : 'text-on-surface-variant hover:bg-surface-variant/30 hover:text-on-surface',
      )}
    >
      <Icon aria-hidden className="size-5 shrink-0" strokeWidth={1.75} />
      {!collapsed ? (
        <>
          <span className="font-body-sm text-body-sm flex-1 truncate">{item.label}</span>
          {badge > 0 ? (
            <span className="rounded bg-primary/20 px-1.5 py-0.5 font-technical-label text-technical-sm font-bold text-primary">
              {badge}
            </span>
          ) : null}
        </>
      ) : badge > 0 ? (
        <span className="sr-only">{badge} items need attention</span>
      ) : null}
    </Link>
  );
};

export const Sidebar = ({
  collapsed = false,
  badges = {},
  onNavigate,
  className,
  pathname,
}: SidebarProps) => {
  const { can, hasRole, user } = useAuth();
  const sections = visibleSections(can, hasRole);

  return (
    <nav
      aria-label="Main navigation"
      className={cn(
        'flex h-screen flex-col overflow-y-auto border-r border-border-structural bg-surface-container-lowest',
        collapsed ? 'w-20 items-center px-2 py-6' : 'w-[248px]',
        className,
      )}
    >
      {/* Logo and organisation */}
      <div className={cn('flex flex-col gap-1', collapsed ? 'mb-8 items-center' : 'px-6 py-8')}>
        <div className="flex items-center gap-2">
          <IrisMark />
          {!collapsed ? (
            <span className="font-display-sm text-display-sm tracking-tight text-on-surface">
              {brand.productName}
            </span>
          ) : null}
        </div>
        {!collapsed ? (
          <span className="font-technical-label text-technical-label uppercase text-on-surface-variant/60">
            {brand.organizationName}
          </span>
        ) : null}
      </div>

      <div className={cn('flex flex-1 flex-col gap-6', collapsed ? 'w-full' : 'px-3')}>
        {sections.map((section) => (
          <section key={section.id}>
            {!collapsed && section.label ? (
              <h2 className="mb-2 px-3 font-technical-label text-[10px] uppercase tracking-[0.2em] text-on-surface-variant/50">
                {section.label}
              </h2>
            ) : null}
            <div className="flex flex-col gap-0.5">
              {section.items.map((item) => (
                <SidebarLink
                  key={item.id}
                  item={item}
                  collapsed={collapsed}
                  badge={badges[item.id] ?? 0}
                  active={isNavItemActive(item, pathname)}
                  onNavigate={onNavigate}
                />
              ))}
            </div>
          </section>
        ))}
      </div>

      {/* Footer: help, settings, signed-in user */}
      <div
        className={cn(
          'mt-auto flex flex-col gap-4 border-t border-border-structural/50 p-4',
          collapsed && 'w-full items-center',
        )}
      >
        <div className={cn('flex flex-col gap-1', collapsed && 'items-center')}>
          <Link
            to={brand.supportUrl}
            className={cn(
              'flex items-center gap-3 rounded-lg px-3 py-1.5 text-xs font-medium text-on-surface-variant transition-all hover:text-on-surface',
              collapsed && 'justify-center px-0',
            )}
          >
            <HelpCircle aria-hidden className="size-[18px]" strokeWidth={1.75} />
            {!collapsed ? 'Help Center' : <span className="sr-only">Help Center</span>}
          </Link>
          <Link
            to="/settings"
            className={cn(
              'flex items-center gap-3 rounded-lg px-3 py-1.5 text-xs font-medium text-on-surface-variant transition-all hover:text-on-surface',
              collapsed && 'justify-center px-0',
            )}
          >
            <Settings aria-hidden className="size-[18px]" strokeWidth={1.75} />
            {!collapsed ? 'Settings' : <span className="sr-only">Settings</span>}
          </Link>
        </div>

        {user ? (
          <div
            className={cn(
              'flex items-center gap-3 rounded-xl border border-white/5 bg-surface-variant/20 p-2',
              collapsed && 'justify-center border-0 bg-transparent p-0',
            )}
          >
            <div className="relative">
              <Avatar name={user.fullName} size="md" className="rounded-lg" />
              <span
                aria-hidden
                className="absolute -bottom-1 -right-1 size-3 rounded-full border-2 border-surface-container-lowest bg-success-teal"
              />
            </div>
            {!collapsed ? (
              <div className="flex min-w-0 flex-col overflow-hidden">
                <span className="truncate font-medium leading-none text-on-surface">
                  {user.fullName}
                </span>
                <span className="mt-1 font-technical-label text-[10px] uppercase tracking-wider text-on-surface-variant/70">
                  {user.employeeLevel}
                </span>
              </div>
            ) : null}
          </div>
        ) : null}
      </div>
    </nav>
  );
};
