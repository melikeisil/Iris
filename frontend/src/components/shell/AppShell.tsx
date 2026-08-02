import { useEffect, useState, type MouseEvent } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { X } from 'lucide-react';
import { Sidebar } from './Sidebar';
import { TopHeader } from './TopHeader';
import { SessionExpiredDialog } from './SessionExpiredDialog';
import { IconButton } from '@/design-system/components/IconButton';
import { useIsDesktop, useIsMobile } from '@/hooks/useMediaQuery';
import { useTeamReviewQueue } from '@/features/team-lead/hooks/useReviewQueue';

const focusMainContent = () => {
  const main = document.getElementById('main-content');
  if (!(main instanceof HTMLElement)) return;
  main.focus();
};

const handleSkipLink = (event: MouseEvent<HTMLAnchorElement>) => {
  event.preventDefault();
  window.location.hash = 'main-content';
  focusMainContent();
};

/**
 * One shell for every role. Roles change the navigation data, never the layout - which
 * is why there is no ManagerShell or AdminShell in this codebase, even though the
 * Stitch sources ship separate employee / multi-role mockups.
 *
 * Responsive behaviour, matching iris_employee_dashboard_1024px_preview:
 *   >= 1440  expanded 248px sidebar
 *   1024-1439 collapsed 80px icon rail
 *   768-1023  collapsed rail
 *   < 768     no sidebar; an overlay drawer opened from the top bar
 */
export const AppShell = () => {
  const isMobile = useIsMobile();
  const isDesktop = useIsDesktop();
  const location = useLocation();

  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const reviewQueue = useTeamReviewQueue();

  useEffect(() => setMobileNavOpen(false), [location.pathname]);

  useEffect(() => {
    if (!mobileNavOpen) return undefined;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMobileNavOpen(false);
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [mobileNavOpen]);

  const badges = { 'ai-review': reviewQueue.data?.length ?? 0 };

  return (
    <div className="flex min-h-screen bg-background text-on-background">
      <a
        href="#main-content"
        onClick={handleSkipLink}
        className="sr-only absolute left-3 top-3 z-50 rounded-lg bg-brand-indigo px-3 py-2 text-body-sm text-white focus:not-sr-only"
      >
        Skip to main content
      </a>

      {!isMobile ? (
        <div className="sticky top-0 hidden h-screen shrink-0 self-start md:block">
          <Sidebar collapsed={!isDesktop} badges={badges} pathname={location.pathname} />
        </div>
      ) : null}

      {mobileNavOpen ? (
        <div className="fixed inset-0 z-50 md:hidden">
          <div
            aria-hidden
            className="absolute inset-0 bg-black/70"
            onClick={() => setMobileNavOpen(false)}
          />
          <div
            role="dialog"
            aria-modal="true"
            aria-label="Navigation"
            className="absolute inset-y-0 left-0 w-[248px] animate-fade-in"
          >
            <div className="absolute right-2 top-3 z-10">
              <IconButton
                label="Close navigation"
                icon={<X className="size-4" />}
                onClick={() => setMobileNavOpen(false)}
              />
            </div>
            <Sidebar
              badges={badges}
              pathname={location.pathname}
              onNavigate={() => setMobileNavOpen(false)}
            />
          </div>
        </div>
      ) : null}

      <div className="flex min-w-0 flex-1 flex-col">
        <TopHeader
          pathname={location.pathname}
          onOpenMobileNav={() => setMobileNavOpen(true)}
          showMobileNavButton={isMobile}
        />
        <main id="main-content" tabIndex={-1} className="min-w-0 flex-1">
          <Outlet />
        </main>
      </div>

      <SessionExpiredDialog />
    </div>
  );
};
