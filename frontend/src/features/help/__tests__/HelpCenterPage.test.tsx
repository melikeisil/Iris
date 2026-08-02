import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { api } from '@/api/client/endpoints';
import { HelpCenterPage } from '../HelpCenterPage';
import { renderWithProviders } from '@/test/utils';
import { employeeUser } from '@/test/fixtures';

vi.mock('@/api/client/endpoints', async () => {
  const { createApiMock } = await import('@/test/apiMock');
  return { api: createApiMock() };
});

describe('HelpCenterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.auth.me).mockResolvedValue(employeeUser());
  });

  it('renders the core help topics for the hackathon build', async () => {
    renderWithProviders(<HelpCenterPage />, { route: '/help' });

    expect(await screen.findByRole('heading', { name: /quick guidance for the iris workspace/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Create Request' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'My Work' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'AI Review' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Quick FAQs/i })).toBeInTheDocument();
  });

  it('filters the topic list with the search field', async () => {
    const user = userEvent.setup();
    renderWithProviders(<HelpCenterPage />, { route: '/help' });

    await user.type(await screen.findByLabelText(/search help topics/i), 'manager');

    expect(screen.getByRole('heading', { name: 'Roles and permissions' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Create Request' })).not.toBeInTheDocument();
  });
});
