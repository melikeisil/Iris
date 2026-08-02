import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import { api } from '@/api/client/endpoints';
import { ApiError } from '@/api/errors/ApiError';
import { DashboardPage } from '../DashboardPage';
import { renderWithProviders } from '@/test/utils';
import {
  employeeUser,
  makeWorkItemSummary,
  managerUser,
  pending,
  teamLeadUser,
} from '@/test/fixtures';
import type { WorkItemSummary } from '@/api/types';

vi.mock('@/api/client/endpoints', async () => {
  const { createApiMock } = await import('@/test/apiMock');
  return { api: createApiMock() };
});

/**
 * The dashboard is one layout whose slots are filled by role, so the interesting
 * assertions are "which widget set appeared" and "what happens when the one query the
 * page cannot render without fails".
 */
describe('DashboardPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows a loading state while the assigned-work query is in flight', async () => {
    vi.mocked(api.auth.me).mockResolvedValue(employeeUser());
    vi.mocked(api.workItems.mine).mockReturnValue(pending<WorkItemSummary[]>());

    renderWithProviders(<DashboardPage />, { route: '/dashboard' });

    expect(await screen.findByText('Loading your dashboard')).toBeInTheDocument();
    expect(screen.queryByText(/my work preview/i)).not.toBeInTheDocument();
  });

  it('shows an error state, not a blank page, when that query fails', async () => {
    vi.mocked(api.auth.me).mockResolvedValue(employeeUser());
    vi.mocked(api.workItems.mine).mockRejectedValue(
      new ApiError({ message: 'Boom', status: 500, code: 'INTERNAL_ERROR' }),
    );

    renderWithProviders(<DashboardPage />, { route: '/dashboard' });

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Server error');
    expect(alert).toHaveTextContent('Something went wrong on the server.');
  });

  it('renders the employee widget set and hides completed work from the preview', async () => {
    vi.mocked(api.auth.me).mockResolvedValue(employeeUser());
    vi.mocked(api.workItems.mine).mockResolvedValue([
      makeWorkItemSummary({ id: 'wi-1', referenceKey: 'TASK-101' }),
      makeWorkItemSummary({
        id: 'wi-2',
        referenceKey: 'TASK-999',
        status: 'DONE',
        statusCategory: 'DONE',
      }),
    ]);

    renderWithProviders(<DashboardPage />, { route: '/dashboard' });

    expect(await screen.findByText('Needs information')).toBeInTheDocument();
    const preview = screen.getByText('My work preview').closest('section');
    expect(preview).not.toBeNull();
    expect(within(preview as HTMLElement).getAllByText('TASK-101').length).toBeGreaterThan(0);
    expect(within(preview as HTMLElement).queryByText('TASK-999')).not.toBeInTheDocument();
    expect(screen.queryByText('Pending AI reviews')).not.toBeInTheDocument();
  });

  it('renders the team-lead widget set instead for a team lead', async () => {
    vi.mocked(api.auth.me).mockResolvedValue(teamLeadUser());
    vi.mocked(api.workItems.mine).mockResolvedValue([]);

    renderWithProviders(<DashboardPage />, { route: '/dashboard' });

    expect(await screen.findByText('Pending AI reviews')).toBeInTheDocument();
    expect(screen.getByText('Overdue approvals')).toBeInTheDocument();
    expect(screen.queryByText('Needs information')).not.toBeInTheDocument();
  });

  it('renders the manager widget set for a manager', async () => {
    vi.mocked(api.auth.me).mockResolvedValue(managerUser());
    vi.mocked(api.workItems.mine).mockResolvedValue([]);

    renderWithProviders(<DashboardPage />, { route: '/dashboard' });

    expect(await screen.findByText('Open work')).toBeInTheDocument();
    expect(screen.getByText('Completed work')).toBeInTheDocument();
    expect(screen.queryByText('Pending AI reviews')).not.toBeInTheDocument();
  });
});
