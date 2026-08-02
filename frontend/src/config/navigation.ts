import {
  LayoutDashboard,
  PlusCircle,
  Briefcase,
  Sparkles,
  LineChart,
  type LucideIcon,
} from 'lucide-react';
import type { Capability, SystemRole } from '@/api/types';
import { paths } from '@/app/router/paths';

/**
 * Role-aware navigation for the single application shell.
 *
 * There is one shell, not one shell per role: the Stitch sources ship separate
 * "employee" / "multi-role" mockups, but they differ only in which items the WORKSPACE
 * section contains, which is data, not structure.
 *
 * Visibility is decided by capability first (the backend's own answer, from
 * GET /auth/me) and falls back to the system role. A user is never assumed to have one
 * role: the seed data alone contains a MANAGER who is also an EMPLOYEE, so every check
 * is a set-membership test.
 *
 * There is deliberately no Admin section - no admin screens exist in this MVP.
 */
export interface NavItem {
  id: string;
  label: string;
  to: string;
  icon: LucideIcon;
  /** Visible when the user holds any of these. Empty means "everyone". */
  anyCapability: readonly Capability[];
  /** Fallback when the backend emits no matching capability. */
  anyRole: readonly SystemRole[];
  /** Matches child routes too, e.g. /work-items/:id highlights My Work. */
  matchPrefixes?: readonly string[];
}

export interface NavSection {
  id: string;
  /** Rendered as an IBM Plex Mono 10px tracked-out header, or omitted when null. */
  label: string | null;
  items: readonly NavItem[];
}

const WORKSPACE: readonly NavItem[] = [
  {
    id: 'dashboard',
    label: 'Dashboard',
    to: paths.dashboard,
    icon: LayoutDashboard,
    anyCapability: [],
    anyRole: [],
  },
  {
    id: 'create-request',
    label: 'Create Request',
    to: paths.newRequest,
    icon: PlusCircle,
    anyCapability: ['work_item.create'],
    anyRole: ['EMPLOYEE', 'TEAM_LEAD', 'MANAGER', 'ADMIN'],
  },
  {
    id: 'ai-review',
    label: 'AI Review',
    to: paths.aiReview,
    icon: Sparkles,
    anyCapability: ['team.review_queue'],
    anyRole: ['TEAM_LEAD', 'MANAGER'],
  },
  {
    id: 'my-work',
    label: 'My Work',
    to: paths.myWork,
    icon: Briefcase,
    anyCapability: ['work_item.view_own'],
    anyRole: [],
    matchPrefixes: ['/work-items'],
  },
];

const MANAGEMENT: readonly NavItem[] = [
  {
    id: 'manager-overview',
    label: 'Manager Overview',
    to: paths.manager,
    icon: LineChart,
    anyCapability: ['manager.dashboard'],
    anyRole: ['MANAGER'],
  },
];

export const navSections: readonly NavSection[] = [
  { id: 'workspace', label: 'Workspace', items: WORKSPACE },
  { id: 'management', label: 'Management', items: MANAGEMENT },
];

/**
 * `can` and `hasRole` come from AuthProvider. An item with neither a capability nor a
 * role requirement is always visible.
 */
export const visibleSections = (
  can: (...capabilities: Capability[]) => boolean,
  hasRole: (...roles: SystemRole[]) => boolean,
): NavSection[] =>
  navSections
    .map((section) => ({
      ...section,
      items: section.items.filter((item) => {
        if (item.anyCapability.length === 0 && item.anyRole.length === 0) return true;
        if (item.anyCapability.length > 0 && can(...item.anyCapability)) return true;
        return item.anyRole.length > 0 && hasRole(...item.anyRole);
      }),
    }))
    .filter((section) => section.items.length > 0);

/** True when `pathname` should highlight `item`. */
export const isNavItemActive = (item: NavItem, pathname: string): boolean => {
  if (pathname === item.to) return true;
  if (pathname.startsWith(`${item.to}/`)) return true;
  return (item.matchPrefixes ?? []).some(
    (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`),
  );
};

/** Breadcrumb trail for the top bar, e.g. WORKSPACE > MY WORK. */
export const breadcrumbFor = (pathname: string): { section: string; page: string } => {
  for (const section of navSections) {
    for (const item of section.items) {
      if (isNavItemActive(item, pathname)) {
        return { section: section.label ?? 'Workspace', page: item.label };
      }
    }
  }
  return { section: 'Workspace', page: 'Iris' };
};
