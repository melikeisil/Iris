import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { api } from '@/api/client/endpoints';
import { ApiError } from '@/api/errors/ApiError';
import { WeeklyReportModal } from '../WeeklyReportModal';
import { renderWithProviders } from '@/test/utils';
import { makeWeeklyReport } from '@/test/fixtures';

vi.mock('@/api/client/endpoints', async () => {
  const { createApiMock } = await import('@/test/apiMock');
  return { api: createApiMock() };
});

describe('WeeklyReportModal', () => {
  beforeEach(() => vi.clearAllMocks());

  it('generates the report, then shows the preview with default recipients pre-filled', async () => {
    vi.mocked(api.weeklyReport.generate).mockResolvedValue(makeWeeklyReport());
    const user = userEvent.setup();
    renderWithProviders(<WeeklyReportModal open onClose={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: /generate report/i }));

    expect(await screen.findByText(/management summary/i)).toBeInTheDocument();
    expect(
      screen.getByText('Three tasks completed this week; one remains blocked on vendor access.'),
    ).toBeInTheDocument();
    const recipients = screen.getByLabelText(/recipients/i) as HTMLTextAreaElement;
    expect(recipients.value).toBe('manager@acme.test');
    expect(api.weeklyReport.generate).toHaveBeenCalledTimes(1);
  });

  it('disables Generate Report while the request is in flight and never sends it twice', async () => {
    let resolveGenerate: (value: ReturnType<typeof makeWeeklyReport>) => void = () => undefined;
    vi.mocked(api.weeklyReport.generate).mockReturnValue(
      new Promise((resolve) => {
        resolveGenerate = resolve;
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<WeeklyReportModal open onClose={vi.fn()} />);

    const button = screen.getByRole('button', { name: /generate report/i });
    await user.click(button);
    await user.click(button); // a fast second click while still pending

    expect(button).toBeDisabled();
    expect(api.weeklyReport.generate).toHaveBeenCalledTimes(1);

    resolveGenerate(makeWeeklyReport());
    await waitFor(() => expect(screen.getByRole('button', { name: /send report/i })).toBeEnabled());
  });

  it('lets the Manager edit recipients before sending, then sends the edited list', async () => {
    vi.mocked(api.weeklyReport.generate).mockResolvedValue(makeWeeklyReport());
    vi.mocked(api.weeklyReport.send).mockResolvedValue(
      makeWeeklyReport({ status: 'SENT', emailStatus: 'SENT', recipients: ['new-recipient@acme.test'] }),
    );
    const user = userEvent.setup();
    renderWithProviders(<WeeklyReportModal open onClose={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: /generate report/i }));
    const recipients = await screen.findByLabelText(/recipients/i);
    fireEvent.change(recipients, { target: { value: 'new-recipient@acme.test' } });

    await user.click(screen.getByRole('button', { name: /send report/i }));

    await waitFor(() =>
      expect(api.weeklyReport.send).toHaveBeenCalledWith('report-1', ['new-recipient@acme.test']),
    );
    expect(await screen.findByText(/report sent to/i)).toBeInTheDocument();
  });

  it('disables Send Report while sending and after a successful send', async () => {
    vi.mocked(api.weeklyReport.generate).mockResolvedValue(makeWeeklyReport());
    let resolveSend: (value: ReturnType<typeof makeWeeklyReport>) => void = () => undefined;
    vi.mocked(api.weeklyReport.send).mockReturnValue(
      new Promise((resolve) => {
        resolveSend = resolve;
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<WeeklyReportModal open onClose={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: /generate report/i }));
    await screen.findByLabelText(/recipients/i);

    const sendButton = screen.getByRole('button', { name: /send report/i });
    await user.click(sendButton);
    await user.click(sendButton); // duplicate click while the first send is still in flight

    expect(sendButton).toBeDisabled();
    expect(api.weeklyReport.send).toHaveBeenCalledTimes(1);

    resolveSend(makeWeeklyReport({ status: 'SENT', emailStatus: 'SENT', recipients: ['manager@acme.test'] }));
    await waitFor(() => expect(screen.getByRole('button', { name: /sent/i })).toBeDisabled());
  });

  it('shows a clear conflict message rather than resending when the report was already sent', async () => {
    vi.mocked(api.weeklyReport.generate).mockResolvedValue(makeWeeklyReport());
    vi.mocked(api.weeklyReport.send).mockRejectedValue(
      new ApiError({
        message: 'This report has already been sent.',
        detail: 'This report has already been sent.',
        status: 409,
        code: 'DUPLICATE_RESOURCE',
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<WeeklyReportModal open onClose={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: /generate report/i }));
    await screen.findByLabelText(/recipients/i);
    await user.click(screen.getByRole('button', { name: /send report/i }));

    const errorRegion = await screen.findByRole('alert');
    expect(within(errorRegion).getByText(/already been sent/i)).toBeInTheDocument();
    // A conflict is not something "Try again" should retry - see WeeklyReportModal.
    expect(within(errorRegion).queryByRole('button', { name: /try again/i })).not.toBeInTheDocument();
  });

  it('surfaces a generation failure without ever calling send', async () => {
    vi.mocked(api.weeklyReport.generate).mockRejectedValue(
      new ApiError({ message: 'Unable to compute report', status: 500, code: 'INTERNAL_ERROR' }),
    );
    const user = userEvent.setup();
    renderWithProviders(<WeeklyReportModal open onClose={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: /generate report/i }));

    expect(await screen.findByTestId('error-state')).toBeInTheDocument();
    expect(api.weeklyReport.send).not.toHaveBeenCalled();
  });
});
