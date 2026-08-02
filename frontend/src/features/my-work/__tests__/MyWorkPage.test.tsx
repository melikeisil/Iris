import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { api } from '@/api/client/endpoints';
import { MyWorkPage } from '../MyWorkPage';
import { renderWithProviders } from '@/test/utils';
import { employeeUser, makePage, makeWorkItemSummary, pending } from '@/test/fixtures';
import type { WorkItemSummary } from '@/api/types';

vi.mock('@/api/client/endpoints', async () => {
  const { createApiMock } = await import('@/test/apiMock');
  return { api: createApiMock() };
});

const assigned: WorkItemSummary[] = [
  makeWorkItemSummary({
    id: 'wi-1',
    referenceKey: 'TASK-101',
    summary: 'Rotate the signing keys',
    priority: 'CRITICAL',
  }),
  makeWorkItemSummary({
    id: 'wi-2',
    referenceKey: 'BUG-202',
    summary: 'Login loop on Safari',
    workItemType: 'BUG',
    priority: 'LOW',
    status: 'CODE_REVIEW',
    statusCategory: 'REVIEW',
  }),
];

describe('MyWorkPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.auth.me).mockResolvedValue(employeeUser());
    vi.mocked(api.workItems.mine).mockResolvedValue(assigned);
    vi.mocked(api.workItems.createdByMe).mockResolvedValue(makePage<WorkItemSummary>([]));
    vi.mocked(api.workItems.watching).mockResolvedValue(makePage<WorkItemSummary>([]));
  });

  it('shows a loading state until the assigned work arrives', async () => {
    vi.mocked(api.workItems.mine).mockReturnValue(pending<WorkItemSummary[]>());

    renderWithProviders(<MyWorkPage />, { route: '/my-work' });

    expect(await screen.findByText('Loading your work')).toBeInTheDocument();
  });

  it('lists the assigned work items', async () => {
    renderWithProviders(<MyWorkPage />, { route: '/my-work' });

    expect(await screen.findByRole('link', { name: 'Rotate the signing keys' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Login loop on Safari' })).toBeInTheDocument();
    expect(screen.queryByTestId('empty-state')).not.toBeInTheDocument();
  });

  it('narrows the list to the review category when the Reviews tab is chosen', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MyWorkPage />, { route: '/my-work' });

    await screen.findByRole('link', { name: 'Rotate the signing keys' });
    await user.click(screen.getByRole('tab', { name: 'Reviews' }));

    expect(screen.getByRole('link', { name: 'Login loop on Safari' })).toBeInTheDocument();
    expect(
      screen.queryByRole('link', { name: 'Rotate the signing keys' }),
    ).not.toBeInTheDocument();
  });

  it('narrows the list with the priority filter, and says so when nothing matches', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MyWorkPage />, { route: '/my-work' });

    await screen.findByRole('link', { name: 'Rotate the signing keys' });
    await user.selectOptions(screen.getByLabelText('Priority'), 'LOW');

    expect(screen.getByRole('link', { name: 'Login loop on Safari' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Rotate the signing keys' })).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('Priority'), 'CRITICAL');
    await user.selectOptions(screen.getByLabelText('Type'), 'BUG');

    const empty = await screen.findByTestId('empty-state');
    expect(within(empty).getByText('Nothing matches these filters')).toBeInTheDocument();
  });

  it('shows the empty state when nothing is assigned', async () => {
    vi.mocked(api.workItems.mine).mockResolvedValue([]);

    renderWithProviders(<MyWorkPage />, { route: '/my-work' });

    expect(await screen.findByTestId('empty-state')).toBeInTheDocument();
    expect(screen.getByText('Nothing matches these filters')).toBeInTheDocument();
  });
});
