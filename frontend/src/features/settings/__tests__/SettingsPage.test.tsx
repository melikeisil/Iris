import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { api } from '@/api/client/endpoints';
import { SettingsPage } from '../SettingsPage';
import { preferencesStore } from '../preferences';
import { renderWithProviders } from '@/test/utils';
import { managerUser } from '@/test/fixtures';

vi.mock('@/api/client/endpoints', async () => {
  const { createApiMock } = await import('@/test/apiMock');
  return { api: createApiMock() };
});

describe('SettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
    vi.mocked(api.auth.me).mockResolvedValue(managerUser());
  });

  it('shows backend-managed profile data separately from local preferences', async () => {
    renderWithProviders(<SettingsPage />, { route: '/settings' });

    expect(await screen.findByRole('heading', { name: /personal workspace preferences/i })).toBeInTheDocument();
    expect(screen.getByText('Mira Yilmaz')).toBeInTheDocument();
    expect(screen.getByText('ada@novatech.test')).toBeInTheDocument();
    expect(screen.getByText(preferencesStore.key)).toBeInTheDocument();
  });

  it('persists frontend-only preferences to localStorage', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SettingsPage />, { route: '/settings' });

    await user.selectOptions(await screen.findByLabelText('Appearance'), 'system');
    await user.click(screen.getByLabelText('Desktop notifications'));

    await waitFor(() => {
      const saved = JSON.parse(window.localStorage.getItem(preferencesStore.key) ?? '{}');
      expect(saved.appearance).toBe('system');
      expect(saved.desktopNotifications).toBe(true);
    });
  });

  it('signs out through the auth API', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SettingsPage />, { route: '/settings' });

    await user.click(await screen.findByRole('button', { name: 'Sign out' }));

    await waitFor(() => expect(api.auth.logout).toHaveBeenCalled());
  });
});
