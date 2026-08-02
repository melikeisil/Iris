import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { api } from '@/api/client/endpoints';
import { AppRoutes } from '../AppRoutes';
import { paths } from '../paths';
import { renderWithProviders } from '@/test/utils';
import { employeeUser, managerUser, teamLeadUser } from '@/test/fixtures';

vi.mock('@/api/client/endpoints', async () => {
  const { createApiMock } = await import('@/test/apiMock');
  return { api: createApiMock() };
});

/**
 * The nine routes, the two guards and the fallback.
 *
 * Guarding a route is presentation rather than security - the backend authorises every
 * request independently - but a user must never be dropped on a screen they cannot use,
 * so each role/route pair is asserted explicitly.
 */
describe('AppRoutes', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.auth.me).mockResolvedValue(employeeUser());
    vi.mocked(api.workItems.mine).mockResolvedValue([]);
  });

  it('sends an anonymous visitor on a protected route to the login screen', async () => {
    renderWithProviders(<AppRoutes />, { route: paths.dashboard, authenticated: false });

    expect(await screen.findByLabelText(/work email/i)).toBeInTheDocument();
    expect(screen.queryByText(/my work preview/i)).not.toBeInTheDocument();
  });

  it('redirects the index route to the dashboard', async () => {
    renderWithProviders(<AppRoutes />, { route: '/' });

    expect(await screen.findByText(/my work preview/i)).toBeInTheDocument();
  });

  it('blocks an employee from the AI review queue', async () => {
    renderWithProviders(<AppRoutes />, { route: paths.aiReview });

    expect(await screen.findByTestId('permission-state')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /review queue/i })).not.toBeInTheDocument();
  });

  it('blocks an employee from the manager overview', async () => {
    renderWithProviders(<AppRoutes />, { route: paths.manager });

    expect(await screen.findByTestId('permission-state')).toBeInTheDocument();
    expect(screen.queryByText(/operational overview/i)).not.toBeInTheDocument();
  });

  it('lets a team lead reach the AI review queue', async () => {
    vi.mocked(api.auth.me).mockResolvedValue(teamLeadUser());
    renderWithProviders(<AppRoutes />, { route: paths.aiReview });

    expect(await screen.findByRole('heading', { name: /review queue/i })).toBeInTheDocument();
    expect(screen.queryByTestId('permission-state')).not.toBeInTheDocument();
  });

  it('lets a manager reach the manager overview', async () => {
    vi.mocked(api.auth.me).mockResolvedValue(managerUser());
    renderWithProviders(<AppRoutes />, { route: paths.manager });

    expect(await screen.findByText(/operational overview/i)).toBeInTheDocument();
    expect(screen.queryByTestId('permission-state')).not.toBeInTheDocument();
  });

  it('renders the help center from its real route', async () => {
    renderWithProviders(<AppRoutes />, { route: paths.help });

    expect(await screen.findByRole('heading', { name: /quick guidance for the iris workspace/i })).toBeInTheDocument();
  });

  it('renders settings from its real route', async () => {
    renderWithProviders(<AppRoutes />, { route: paths.settings });

    expect(await screen.findByRole('heading', { name: /personal workspace preferences/i })).toBeInTheDocument();
  });

  it('renders the not-found page for a path outside the nine routes', async () => {
    renderWithProviders(<AppRoutes />, { route: '/reports/quarterly' });

    expect(await screen.findByText(/that page does not exist/i)).toBeInTheDocument();
  });
});
