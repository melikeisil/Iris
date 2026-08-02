import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { api } from '@/api/client/endpoints';
import { ApiError } from '@/api/errors/ApiError';
import { CreateRequestPage } from '../CreateRequestPage';
import { renderWithProviders } from '@/test/utils';
import { makeRecommendation, makeWorkItemDetail } from '@/test/fixtures';

vi.mock('@/api/client/endpoints', async () => {
  const { createApiMock } = await import('@/test/apiMock');
  return { api: createApiMock() };
});

const fillForm = async (user: ReturnType<typeof userEvent.setup>) => {
  await user.type(screen.getByLabelText(/^title/i), 'Production database is slow');
  await user.type(
    screen.getByLabelText(/^description/i),
    'Queries take thirty seconds since the deploy this morning.',
  );
  await user.selectOptions(screen.getByLabelText(/business impact/i), 'HIGH');
};

describe('CreateRequestPage', () => {
  // Call history only; the default implementations in createApiMock survive.
  beforeEach(() => vi.clearAllMocks());

  it('blocks the submit until the required fields are valid', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CreateRequestPage />, { route: '/requests/new' });

    await user.click(screen.getByRole('button', { name: /save and analyse/i }));

    expect(
      await screen.findByText('Give the ticket a title of at least 5 characters.'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Describe what happened, what you expected, and how to reproduce it.'),
    ).toBeInTheDocument();
    expect(api.workItems.create).not.toHaveBeenCalled();
  });

  it('posts the CreateWorkItemRequest DTO the backend expects', async () => {
    vi.mocked(api.workItems.create).mockResolvedValue(
      makeWorkItemDetail({ id: 'wi-77', referenceKey: 'REQ-77', status: 'NEW' }),
    );
    vi.mocked(api.analysis.analyze).mockResolvedValue(makeRecommendation({ confidence: 0.9 }));

    const user = userEvent.setup();
    renderWithProviders(<CreateRequestPage />, { route: '/requests/new' });

    await fillForm(user);
    await user.click(screen.getByRole('button', { name: /save and analyse/i }));

    await waitFor(() => expect(api.workItems.create).toHaveBeenCalledTimes(1));
    expect(api.workItems.create).toHaveBeenCalledWith({
      workItemType: 'REQUEST',
      summary: 'Production database is slow',
      description: expect.stringContaining('Reported business impact: HIGH'),
      priority: 'HIGH',
      dueDate: null,
    });

    // The advisory analysis is chained onto the created item, never a guessed id.
    await waitFor(() => expect(api.analysis.analyze).toHaveBeenCalledWith('wi-77'));
    expect(await screen.findByText('Request submitted')).toBeInTheDocument();
    expect(screen.getAllByText('REQ-77').length).toBeGreaterThan(0);
  });

  it('keeps the user on the form and explains the failure when creation is rejected', async () => {
    vi.mocked(api.workItems.create).mockRejectedValue(
      new ApiError({
        message: 'Validation failed',
        status: 400,
        code: 'VALIDATION_FAILED',
        detail: 'Summary is already used by another open request.',
      }),
    );

    const user = userEvent.setup();
    renderWithProviders(<CreateRequestPage />, { route: '/requests/new' });

    await fillForm(user);
    await user.click(screen.getByRole('button', { name: /save and analyse/i }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Summary is already used by another open request.');
    expect(api.analysis.analyze).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /save and analyse/i })).toBeInTheDocument();
  });
});
