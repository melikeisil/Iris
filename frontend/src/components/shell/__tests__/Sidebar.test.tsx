import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import { api } from '@/api/client/endpoints';
import { Sidebar } from '../Sidebar';
import { renderWithProviders } from '@/test/utils';
import { employeeUser, teamLeadUser } from '@/test/fixtures';

vi.mock('@/api/client/endpoints', async () => {
  const { createApiMock } = await import('@/test/apiMock');
  return { api: createApiMock() };
});

const nav = () => screen.getByRole('navigation', { name: /main navigation/i });

describe('Sidebar', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders only the workspace links an employee can use', async () => {
    vi.mocked(api.auth.me).mockResolvedValue(employeeUser());
    renderWithProviders(<Sidebar pathname="/dashboard" />);

    expect(await screen.findByRole('link', { name: /my work/i })).toBeInTheDocument();
    expect(within(nav()).queryByRole('link', { name: /ai review/i })).not.toBeInTheDocument();
    expect(
      within(nav()).queryByRole('link', { name: /manager overview/i }),
    ).not.toBeInTheDocument();
  });

  it('marks the current route as the active page', async () => {
    vi.mocked(api.auth.me).mockResolvedValue(employeeUser());
    renderWithProviders(<Sidebar pathname="/my-work" />);

    const link = await screen.findByRole('link', { name: /my work/i });
    expect(link).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: /dashboard/i })).not.toHaveAttribute('aria-current');
  });

  it('does not mark any workspace item active on an unknown route', async () => {
    vi.mocked(api.auth.me).mockResolvedValue(employeeUser());
    renderWithProviders(<Sidebar pathname="/reports/quarterly" />);

    await screen.findByRole('link', { name: /dashboard/i });
    expect(within(nav()).queryByRole('link', { current: 'page' })).not.toBeInTheDocument();
  });

  it('shows a team lead the AI review link with its queue badge', async () => {
    vi.mocked(api.auth.me).mockResolvedValue(teamLeadUser());
    renderWithProviders(<Sidebar pathname="/dashboard" badges={{ 'ai-review': 3 }} />);

    const link = await screen.findByRole('link', { name: /ai review/i });
    expect(within(link).getByText('3')).toBeInTheDocument();
  });
});
