import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { api } from '@/api/client/endpoints';
import { AiReviewPage } from '../AiReviewPage';
import { renderWithProviders } from '@/test/utils';
import { makeApproval, makeWorkItemDetail, teamLeadUser } from '@/test/fixtures';

vi.mock('@/api/client/endpoints', async () => {
  const { createApiMock } = await import('@/test/apiMock');
  return { api: createApiMock() };
});

const queue = [
  makeApproval({ id: 'approval-1', workItemId: 'wi-1', route: 'TEAM_LEAD_REVIEW' }),
  makeApproval({
    id: 'approval-2',
    workItemId: 'wi-2',
    route: 'PROCESS_OWNER_REVIEW',
    overdue: true,
    reasons: [{ code: 'SLA_BREACH', meaning: 'The approval passed its SLA' }],
  }),
];

describe('AiReviewPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.auth.me).mockResolvedValue(teamLeadUser());
    vi.mocked(api.approvals.teamLeadQueue).mockResolvedValue(queue);
    vi.mocked(api.workItems.get).mockResolvedValue(makeWorkItemDetail());
  });

  it('renders the queue the backend returned and selects the first entry', async () => {
    renderWithProviders(<AiReviewPage />, { route: '/ai-review' });

    expect(await screen.findByText('2 items require review')).toBeInTheDocument();
    expect(screen.getByText('The approval passed its SLA')).toBeInTheDocument();
    expect(screen.getByText('Overdue')).toBeInTheDocument();
    // The first entry is selected by default: its route shows in the queue and again in
    // the backend-policy card of the decision chain.
    expect(screen.getAllByText('TEAM_LEAD_REVIEW')).toHaveLength(2);
  });

  it('switches the centre pane when another queue entry is selected', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AiReviewPage />, { route: '/ai-review' });

    await screen.findByText('2 items require review');
    const second = screen.getByText('The approval passed its SLA');
    await user.click(second);

    await waitFor(() =>
      expect(screen.getAllByText('PROCESS_OWNER_REVIEW').length).toBeGreaterThan(1),
    );
  });

  it('approves through POST /approvals/{id}/approve with no fabricated assignee', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AiReviewPage />, { route: '/ai-review' });

    await user.click(await screen.findByRole('button', { name: 'Approve' }));

    await waitFor(() => expect(api.approvals.approve).toHaveBeenCalledWith('approval-1', {}));
  });

  it('requires a substantive reason before it will send an item back', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AiReviewPage />, { route: '/ai-review' });

    await user.click(await screen.findByRole('button', { name: 'Request changes' }));

    const dialog = await screen.findByRole('dialog', { name: 'Request changes' });
    const send = within(dialog).getByRole('button', { name: 'Send back' });
    expect(send).toBeDisabled();

    const reasonField = within(dialog).getByRole('textbox');
    fireEvent.change(reasonField, { target: { value: 'The estimate is far too low.' } });
    expect(within(dialog).getByRole('textbox')).toHaveValue('The estimate is far too low.');
    await waitFor(() => expect(send).toBeEnabled());

    await user.click(send);
    await waitFor(() =>
      expect(api.approvals.reject).toHaveBeenCalledWith(
        'approval-1',
        'The estimate is far too low.',
      ),
    );
  });

  it('flags the candidate ranking as demo data when no routing outcome is cached', async () => {
    renderWithProviders(<AiReviewPage />, { route: '/ai-review' });

    await screen.findByText('2 items require review');
    const candidatesRegion = screen
      .getByText('Backend-ranked assignment candidates')
      .closest('section');
    expect(candidatesRegion).not.toBeNull();
    expect(within(candidatesRegion as HTMLElement).getByTestId('demo-data-badge')).toBeInTheDocument();
    expect(within(candidatesRegion as HTMLElement).getAllByTestId('assignment-candidate-card').length).toBeGreaterThan(0);
    expect(within(candidatesRegion as HTMLElement).queryByRole('button', { name: 'Select' })).not.toBeInTheDocument();
  });

  it('shows an empty queue plainly', async () => {
    vi.mocked(api.approvals.teamLeadQueue).mockResolvedValue([]);

    renderWithProviders(<AiReviewPage />, { route: '/ai-review' });

    const queuePane = screen.getByText('Review queue').closest('section');
    expect(queuePane).not.toBeNull();
    expect(within(queuePane as HTMLElement).getByText('The queue is clear')).toBeInTheDocument();
    expect(screen.getByText('Select an item')).toBeInTheDocument();
  });
});
