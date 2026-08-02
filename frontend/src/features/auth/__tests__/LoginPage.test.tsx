import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { api } from '@/api/client/endpoints';
import { ApiError } from '@/api/errors/ApiError';
import { tokenStore } from '@/api/client/tokenStore';
import { LoginPage } from '../LoginPage';
import { renderWithProviders } from '@/test/utils';
import { makeTokenResponse } from '@/test/fixtures';
import type { TokenResponse } from '@/api/types';

vi.mock('@/api/client/endpoints', async () => {
  const { createApiMock } = await import('@/test/apiMock');
  return { api: createApiMock() };
});

const renderLogin = () =>
  renderWithProviders(<LoginPage />, { route: '/login', authenticated: false });

describe('LoginPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('validates both fields before calling the backend', async () => {
    const user = userEvent.setup();
    renderLogin();

    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText('Enter your work email.')).toBeInTheDocument();
    expect(screen.getByText('Passwords are at least 8 characters.')).toBeInTheDocument();
    expect(api.auth.login).not.toHaveBeenCalled();
  });

  it('rejects a malformed email without a request', async () => {
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText(/work email/i), 'not-an-email');
    await user.type(screen.getByLabelText(/password/i), 'correct-horse');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(
      await screen.findByText('That does not look like an email address.'),
    ).toBeInTheDocument();
    expect(api.auth.login).not.toHaveBeenCalled();
  });

  it('surfaces the backend message when the credentials are refused', async () => {
    vi.mocked(api.auth.login).mockRejectedValue(
      new ApiError({ message: 'Invalid credentials', status: 401, code: 'INVALID_CREDENTIALS' }),
    );
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText(/work email/i), 'ada@novatech.test');
    await user.type(screen.getByLabelText(/password/i), 'wrong-password');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('That email and password combination was not recognised.');
    expect(tokenStore.current()).toBeNull();
  });

  it('disables the submit button while the request is in flight', async () => {
    let resolveLogin: ((response: TokenResponse) => void) | undefined;
    vi.mocked(api.auth.login).mockImplementation(
      () =>
        new Promise<TokenResponse>((resolve) => {
          resolveLogin = resolve;
        }),
    );
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText(/work email/i), 'ada@novatech.test');
    await user.type(screen.getByLabelText(/password/i), 'correct-horse');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    const button = screen.getByRole('button', { name: /sign in/i });
    await waitFor(() => expect(button).toBeDisabled());
    expect(button).toHaveAttribute('aria-busy', 'true');

    // Resolving signs the user in, at which point the page redirects away from the form.
    resolveLogin?.(makeTokenResponse());
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /sign in/i })).not.toBeInTheDocument(),
    );
  });

  it('stores the issued session on success', async () => {
    vi.mocked(api.auth.login).mockResolvedValue(makeTokenResponse());
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText(/work email/i), 'ada@novatech.test');
    await user.type(screen.getByLabelText(/password/i), 'correct-horse');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() =>
      expect(api.auth.login).toHaveBeenCalledWith('ada@novatech.test', 'correct-horse'),
    );
    await waitFor(() => expect(tokenStore.current()?.accessToken).toBe('issued-access'));
  });
});
