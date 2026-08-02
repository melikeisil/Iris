import { Link, useNavigate } from 'react-router-dom';
import { ChevronRight, HelpCircle, Menu as MenuIcon, Plus, Search } from 'lucide-react';
import { NotificationMenu } from './NotificationMenu';
import { UserMenu } from './UserMenu';
import { IconButton } from '@/design-system/components/IconButton';
import { breadcrumbFor } from '@/config/navigation';
import { paths } from '@/app/router/paths';
import { brand } from '@/config/brand';

/**
 * The sticky top bar, converted from the `<header>` in
 * iris_application_shell_employee_expanded/code.html:
 *
 *   h-16 bg-[#07090C] border-b border-[#2A3443] flex items-center justify-between px-8
 *
 * Three zones: mono breadcrumb, centred search with a Cmd-K hint, and the actions
 * cluster (indigo Create, notifications, help, avatar).
 *
 * The search input is presentational in this MVP: there is no search endpoint. It is
 * wired to filter My Work rather than pretending to be global search.
 */
export interface TopHeaderProps {
  pathname: string;
  onOpenMobileNav: () => void;
  /** Shown only below the `md` breakpoint, where the sidebar becomes a drawer. */
  showMobileNavButton: boolean;
}

export const TopHeader = ({ pathname, onOpenMobileNav, showMobileNavButton }: TopHeaderProps) => {
  const navigate = useNavigate();
  const { section, page } = breadcrumbFor(pathname);

  return (
    <header className="sticky top-0 z-40 flex h-16 items-center justify-between gap-4 border-b border-border-structural bg-background px-4 laptop:px-8">
      <div className="flex min-w-0 items-center gap-3">
        {showMobileNavButton ? (
          <IconButton
            label="Open navigation"
            icon={<MenuIcon className="size-5" />}
            onClick={onOpenMobileNav}
            className="md:hidden"
          />
        ) : null}
        <nav
          aria-label="Breadcrumb"
          className="flex min-w-0 items-center gap-2 font-technical-label text-[11px] tracking-wide text-on-surface-variant/70"
        >
          <span className="hidden uppercase sm:inline">{section}</span>
          <ChevronRight aria-hidden className="hidden size-3 sm:block" />
          <span className="truncate uppercase text-on-surface">{page}</span>
        </nav>
      </div>

      <div className="mx-4 hidden max-w-xl flex-1 laptop:mx-12 laptop:block">
        <form
          role="search"
          onSubmit={(event) => {
            event.preventDefault();
            const value = new FormData(event.currentTarget).get('q');
            navigate(`${paths.myWork}?q=${encodeURIComponent(String(value ?? ''))}`);
          }}
          className="group relative"
        >
          <Search
            aria-hidden
            className="pointer-events-none absolute left-4 top-1/2 size-5 -translate-y-1/2 text-on-surface-variant transition-colors group-focus-within:text-primary"
          />
          <label className="sr-only" htmlFor="global-search">
            Search your work
          </label>
          <input
            id="global-search"
            name="q"
            type="search"
            placeholder="Search your work items"
            className="h-10 w-full rounded-lg border border-border-field bg-field pl-12 pr-16 text-sm transition-all placeholder:text-on-surface-variant/40 focus:border-primary focus:ring-0"
          />
          <span
            aria-hidden
            className="absolute right-4 top-1/2 flex -translate-y-1/2 items-center gap-1 opacity-40"
          >
            <kbd className="rounded border border-on-surface-variant px-1.5 py-0.5 font-technical-label text-[10px]">
              /
            </kbd>
          </span>
        </form>
      </div>

      <div className="flex items-center gap-3 laptop:gap-5">
        <button
          type="button"
          onClick={() => navigate(paths.newRequest)}
          className="flex items-center gap-2 rounded-lg bg-brand-indigo px-4 py-2 text-sm font-semibold text-white shadow-lg shadow-brand-indigo/10 transition-all hover:bg-brand-indigo-hover active:scale-95 laptop:px-5"
        >
          <Plus aria-hidden className="size-[18px]" />
          <span className="hidden sm:inline">Create</span>
        </button>
        <div className="flex items-center gap-1 laptop:gap-3">
          <NotificationMenu />
          <Link
            to={brand.supportUrl}
            className="group hidden rounded-full p-2 transition-colors hover:bg-white/5 sm:block"
          >
            <HelpCircle
              aria-hidden
              className="size-5 text-on-surface-variant group-hover:text-on-surface"
              strokeWidth={1.75}
            />
            <span className="sr-only">Help</span>
          </Link>
          <UserMenu />
        </div>
      </div>
    </header>
  );
};
