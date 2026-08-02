import { describe, expect, it } from 'vitest';
import type { Capability, CurrentUser, SystemRole } from '@/api/types';
import { employeeUser, managerUser, teamLeadUser } from '@/test/fixtures';
import { breadcrumbFor, isNavItemActive, navSections, visibleSections } from '../navigation';

/**
 * Navigation visibility is the one place role awareness is data rather than markup, so
 * it is tested against the same `can` / `hasRole` predicates AuthProvider builds.
 */
const predicatesFor = (user: CurrentUser) => {
  const capabilities = new Set<string>(user.capabilities);
  const roles = new Set<string>(user.roles);
  return {
    can: (...required: Capability[]) =>
      required.length === 0 || required.some((capability) => capabilities.has(capability)),
    hasRole: (...required: SystemRole[]) => required.some((role) => roles.has(role)),
  };
};

const labelsFor = (user: CurrentUser): string[] => {
  const { can, hasRole } = predicatesFor(user);
  return visibleSections(can, hasRole).flatMap((section) =>
    section.items.map((item) => item.label),
  );
};

describe('navigation visibility', () => {
  it('shows an employee exactly Dashboard, Create Request and My Work', () => {
    expect(labelsFor(employeeUser())).toEqual(['Dashboard', 'Create Request', 'My Work']);
  });

  it('adds AI Review for a team lead and nothing from Management', () => {
    const labels = labelsFor(teamLeadUser());

    expect(labels).toContain('AI Review');
    expect(labels).not.toContain('Manager Overview');
  });

  it('adds Manager Overview for a manager', () => {
    expect(labelsFor(managerUser())).toContain('Manager Overview');
  });

  it('offers no admin navigation to any role', () => {
    for (const user of [employeeUser(), teamLeadUser(), managerUser()]) {
      expect(labelsFor(user).some((label) => /admin/i.test(label))).toBe(false);
    }
    expect(navSections.some((section) => /admin/i.test(section.id))).toBe(false);
  });

  it('drops a section entirely when the role sees none of its items', () => {
    const { can, hasRole } = predicatesFor(employeeUser());

    expect(visibleSections(can, hasRole).map((section) => section.id)).toEqual(['workspace']);
  });
});

describe('active item matching', () => {
  const myWork = navSections
    .flatMap((section) => section.items)
    .find((item) => item.id === 'my-work');

  it('highlights My Work from a work item detail route', () => {
    expect(myWork).toBeDefined();
    expect(isNavItemActive(myWork!, '/work-items/abc-123')).toBe(true);
    expect(isNavItemActive(myWork!, '/dashboard')).toBe(false);
  });

  it('builds the breadcrumb from the matching item, falling back for unknown paths', () => {
    expect(breadcrumbFor('/my-work')).toEqual({ section: 'Workspace', page: 'My Work' });
    expect(breadcrumbFor('/manager')).toEqual({ section: 'Management', page: 'Manager Overview' });
    expect(breadcrumbFor('/nowhere')).toEqual({ section: 'Workspace', page: 'Iris' });
  });
});
