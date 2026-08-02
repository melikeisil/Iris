import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { api } from '@/api/client/endpoints';
import { AppShell } from '../AppShell';
import { renderWithProviders } from '@/test/utils';
import { employeeUser } from '@/test/fixtures';

vi.mock('@/api/client/endpoints', async () => {
  const { createApiMock } = await import('@/test/apiMock');
  return { api: createApiMock() };
});

describe('AppShell', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.auth.me).mockResolvedValue(employeeUser());
    vi.mocked(api.workItems.mine).mockResolvedValue([]);
  });

  it('keeps the skip link visually hidden until focus and moves focus to main content', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/dashboard" element={<div>Dashboard body</div>} />
        </Route>
      </Routes>,
      { route: '/dashboard' },
    );

    const skipLink = screen.getByRole('link', { name: /skip to main content/i });
    expect(skipLink.className).toContain('sr-only');
    expect(skipLink.className).toContain('focus:not-sr-only');

    await user.tab();
    expect(skipLink).toHaveFocus();

    await user.keyboard('{Enter}');
    expect(document.getElementById('main-content')).toHaveFocus();
  });
});
