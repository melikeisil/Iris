import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { api } from '@/api/client/endpoints';
import { ApiError } from '@/api/errors/ApiError';
import { WorkItemDetailPage } from '../WorkItemDetailPage';
import { paths } from '@/app/router/paths';
import { renderWithProviders } from '@/test/utils';
import {
  employeeUser,
  makeAdditionalInformationResponse,
  makeClarificationRecommendation,
  managerUser,
  makeRecommendation,
  makeTransitionResult,
  makeWorkItemDetail,
  pending,
  teamLeadUser,
} from '@/test/fixtures';
import type { WorkItemDetail } from '@/api/types';

vi.mock('@/api/client/endpoints', async () => {
  const { createApiMock } = await import('@/test/apiMock');
  return { api: createApiMock() };
});

const renderDetail = () =>
  renderWithProviders(
    <Routes>
      <Route path={paths.workItemPattern} element={<WorkItemDetailPage />} />
    </Routes>,
    { route: paths.workItem('wi-1') },
  );

describe('WorkItemDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.auth.me).mockResolvedValue(employeeUser());
    vi.mocked(api.workItems.get).mockResolvedValue(makeWorkItemDetail());
  });

  it('shows a loading state while the item is fetched', async () => {
    vi.mocked(api.workItems.get).mockReturnValue(pending<WorkItemDetail>());

    renderDetail();

    expect(await screen.findByText('Loading work item')).toBeInTheDocument();
  });

  it('explains a 404 rather than rendering an empty shell', async () => {
    vi.mocked(api.workItems.get).mockRejectedValue(
      new ApiError({ message: 'Not found', status: 404, code: 'NOT_FOUND' }),
    );

    renderDetail();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Not found');
    expect(alert).toHaveTextContent('That item could not be found, or you cannot see it.');
  });

  it('renders the backend status, priority and the id from the route', async () => {
    renderDetail();

    expect(
      await screen.findByRole('heading', { name: 'Rotate the signing keys' }),
    ).toBeInTheDocument();
    expect(api.workItems.get).toHaveBeenCalledWith('wi-1', expect.anything());
    expect(screen.getAllByTestId('status-badge').length).toBeGreaterThan(0);
    expect(screen.getByTestId('priority-badge')).toHaveTextContent('High');
  });

  it('shows em dashes for missing effort values, but preserves valid zero-hour values', async () => {
    vi.mocked(api.workItems.get).mockResolvedValue(
      makeWorkItemDetail({ estimatedHours: undefined as never, remainingHours: 0 }),
    );
    renderDetail();

    await screen.findByRole('heading', { name: 'Rotate the signing keys' });
    expect(screen.getByText('Estimated').nextElementSibling).toHaveTextContent('—');
    expect(screen.getByText('Remaining').nextElementSibling).toHaveTextContent('0h');
  });

  it('posts the transition code and the optimistic-locking version, never a status', async () => {
    vi.mocked(api.workItems.transition).mockResolvedValue(makeTransitionResult());
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Submit for review' }));

    await waitFor(() =>
      expect(api.workItems.transition).toHaveBeenCalledWith('wi-1', {
        transition: 'SUBMIT_FOR_REVIEW',
        version: 7,
      }),
    );
  });

  it('runs backend routing from the AI & Policy tab once analysis exists', async () => {
    vi.mocked(api.analysis.history).mockResolvedValue([
      makeRecommendation({ confidence: 0.91 }),
    ]);
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('tab', { name: /ai & policy/i }));
    await user.click(await screen.findByRole('button', { name: 'Run routing' }));

    await waitFor(() => expect(api.routing.route).toHaveBeenCalledWith('wi-1'));
  });

  it('labels a demo fallback analysis clearly, rather than as a real Gemini result', async () => {
    vi.mocked(api.analysis.history).mockResolvedValue([
      makeRecommendation({
        confidence: 0.91,
        analysis: { summary: 'Need more information', priority: 'HIGH' },
        provenance: {
          provider: 'demo-fallback',
          model: 'mock-deterministic-v1',
          latencyMs: 820,
          isMock: true,
        },
      }),
    ]);
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('tab', { name: /ai & policy/i }));

    expect(await screen.findByText(/demo fallback used after the real gemini provider was rate-limited/i))
      .toBeInTheDocument();
    expect(screen.queryByText(/Produced by the deterministic mock AI provider/i)).not.toBeInTheDocument();
  });

  it('renders a transition the backend disabled as disabled, with its reason', async () => {
    vi.mocked(api.workItems.get).mockResolvedValue(
      makeWorkItemDetail({
        availableTransitions: [
          {
            code: 'SUBMIT_FOR_REVIEW',
            label: 'Submit for review',
            toState: 'CODE_REVIEW',
            requiresReason: false,
            enabled: true,
            blockedReasons: [],
          },
          {
            code: 'CLOSE',
            label: 'Close',
            toState: 'CLOSED',
            requiresReason: false,
            enabled: false,
            blockedReasons: ['Child work items are still open.'],
          },
        ],
      }),
    );
    const user = userEvent.setup();
    renderDetail();

    const close = await screen.findByRole('button', { name: 'Close' });
    expect(close).toBeDisabled();

    await user.click(screen.getByRole('button', { name: /why is close unavailable/i }));
    expect(screen.getByText('Child work items are still open.')).toBeInTheDocument();
    expect(api.workItems.transition).not.toHaveBeenCalled();
  });

  it('hides workflow controls entirely when the backend exposes no allowed transition', async () => {
    vi.mocked(api.workItems.get).mockResolvedValue(
      makeWorkItemDetail({
        availableTransitions: [
          {
            code: 'BLOCK',
            label: 'Block',
            toState: 'BLOCKED',
            requiresReason: true,
            enabled: false,
            blockedReasons: ['Assignment is required first.'],
          },
        ],
      }),
    );
    renderDetail();

    await screen.findByRole('heading', { name: 'Rotate the signing keys' });
    expect(screen.queryByTestId('workflow-actions')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /block this item/i })).not.toBeInTheDocument();
  });

  it('shows a create button when the item is not linked and this caller may create the Jira issue', async () => {
    vi.mocked(api.auth.me).mockResolvedValue(teamLeadUser());
    vi.mocked(api.workItems.get).mockResolvedValue(
      makeWorkItemDetail({
        jira: null,
        permissions: {
          canEdit: true,
          canAssign: true,
          canComment: true,
          canLink: false,
          canSeeInternalComments: false,
        },
      }),
    );

    renderDetail();

    expect(await screen.findByRole('button', { name: 'Create in Jira' })).toBeInTheDocument();
  });

  it('creates a Jira issue and then shows the returned link', async () => {
    vi.mocked(api.auth.me).mockResolvedValue(teamLeadUser());
    vi.mocked(api.workItems.get)
      .mockResolvedValueOnce(
        makeWorkItemDetail({
          jira: null,
          permissions: {
            canEdit: true,
            canAssign: true,
            canComment: true,
            canLink: false,
            canSeeInternalComments: false,
          },
        }),
      )
      .mockResolvedValueOnce(
        makeWorkItemDetail({
          jira: {
            issueKey: 'IRIS-123',
            issueUrl: 'https://example.atlassian.net/browse/IRIS-123',
            createdAt: '2026-01-05T10:00:00Z',
          },
          permissions: {
            canEdit: true,
            canAssign: true,
            canComment: true,
            canLink: false,
            canSeeInternalComments: false,
          },
        }),
      );
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Create in Jira' }));

    await waitFor(() => expect(api.workItems.createInJira).toHaveBeenCalledWith('wi-1'));
    expect(await screen.findByRole('link', { name: /open in jira/i })).toHaveAttribute(
      'href',
      'https://example.atlassian.net/browse/IRIS-123',
    );
  });

  it('renders an already-linked Jira issue without a create button', async () => {
    vi.mocked(api.auth.me).mockResolvedValue(teamLeadUser());
    vi.mocked(api.workItems.get).mockResolvedValue(
      makeWorkItemDetail({
        jira: {
          issueKey: 'IRIS-123',
          issueUrl: 'https://example.atlassian.net/browse/IRIS-123',
          createdAt: '2026-01-05T10:00:00Z',
        },
      }),
    );

    renderDetail();

    expect(await screen.findByText('IRIS-123')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Create in Jira' })).not.toBeInTheDocument();
  });

  it('does not render a create button for a caller without permission', async () => {
    vi.mocked(api.auth.me).mockResolvedValue(managerUser());
    vi.mocked(api.workItems.get).mockResolvedValue(
      makeWorkItemDetail({
        jira: null,
        permissions: {
          canEdit: true,
          canAssign: false,
          canComment: true,
          canLink: false,
          canSeeInternalComments: false,
        },
      }),
    );

    renderDetail();

    await screen.findByText('You can create Jira issues only for work items inside your team or manager scope.');
    expect(screen.queryByRole('button', { name: 'Create in Jira' })).not.toBeInTheDocument();
  });

  it('shows a retryable error when Jira creation fails', async () => {
    vi.mocked(api.auth.me).mockResolvedValue(teamLeadUser());
    vi.mocked(api.workItems.get).mockResolvedValue(
      makeWorkItemDetail({
        jira: null,
        permissions: {
          canEdit: true,
          canAssign: true,
          canComment: true,
          canLink: false,
          canSeeInternalComments: false,
        },
      }),
    );
    vi.mocked(api.workItems.createInJira).mockRejectedValue(
      new ApiError({
        message: 'Jira is unavailable right now.',
        status: 502,
        code: 'JIRA_UNAVAILABLE',
      }),
    );
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Create in Jira' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Jira is unavailable right now.');
    expect(screen.getByRole('button', { name: 'Create in Jira' })).toBeInTheDocument();
  });

  it('explains when Jira is not configured in this environment', async () => {
    vi.mocked(api.auth.me).mockResolvedValue(teamLeadUser());
    vi.mocked(api.jira.status).mockResolvedValue({
      configured: false,
      baseUrl: null,
      projectKey: null,
      issueType: null,
    });
    vi.mocked(api.workItems.get).mockResolvedValue(
      makeWorkItemDetail({
        jira: null,
        permissions: {
          canEdit: true,
          canAssign: true,
          canComment: true,
          canLink: false,
          canSeeInternalComments: false,
        },
      }),
    );

    renderDetail();

    await screen.findByText('Jira integration is not configured for this environment yet.');
    expect(screen.queryByRole('button', { name: 'Create in Jira' })).not.toBeInTheDocument();
  });

  describe('clarification questions', () => {
    const goToAiTab = async (user: ReturnType<typeof userEvent.setup>) => {
      await user.click(await screen.findByRole('tab', { name: /ai & policy/i }));
    };

    beforeEach(() => {
      vi.mocked(api.workItems.get).mockResolvedValue(
        makeWorkItemDetail({ relationships: ['REQUESTER'] }),
      );
      vi.mocked(api.analysis.history).mockResolvedValue([makeClarificationRecommendation()]);
    });

    it('does not show a clarification card when the analysis did not ask for more information', async () => {
      vi.mocked(api.analysis.history).mockResolvedValue([makeRecommendation()]);
      const user = userEvent.setup();
      renderDetail();

      await goToAiTab(user);
      await screen.findByText('Advisory AI analysis');

      expect(screen.queryByText('Clarification needed')).not.toBeInTheDocument();
    });

    it('shows a read-only notice to someone who is not the requester', async () => {
      vi.mocked(api.workItems.get).mockResolvedValue(
        makeWorkItemDetail({ relationships: ['ASSIGNEE'] }),
      );
      const user = userEvent.setup();
      renderDetail();

      await goToAiTab(user);

      await screen.findByText('Clarification needed');
      expect(
        screen.getByText(/waiting on the requester to answer clarification questions/i),
      ).toBeInTheDocument();
      expect(screen.queryByRole('form', { name: /answer clarification questions/i })).not.toBeInTheDocument();
    });

    it('renders each question as a field and blocks submission while a required answer is missing', async () => {
      const user = userEvent.setup();
      renderDetail();

      await goToAiTab(user);
      await screen.findByText('Clarification needed');

      expect(
        screen.getByLabelText(/which database and tables does the intern need access to\?/i),
      ).toBeInTheDocument();
      expect(
        screen.getByLabelText('Is this access temporary, and if so, for how long?'),
      ).toBeInTheDocument();

      const requiredQuestion = screen.getByLabelText(
        /which database and tables does the intern need access to\?/i,
      );
      expect(requiredQuestion).toBeRequired();

      await user.click(screen.getByRole('button', { name: 'Submit answers' }));

      expect(api.analysis.submitAdditionalInformation).not.toHaveBeenCalled();
    });

    it('submits only the questions the requester answered, echoing questionId and field', async () => {
      vi.mocked(api.analysis.submitAdditionalInformation).mockResolvedValue(
        makeAdditionalInformationResponse(),
      );
      const user = userEvent.setup();
      renderDetail();

      await goToAiTab(user);
      await screen.findByText('Clarification needed');

      await user.type(
        screen.getByLabelText(/which database and tables does the intern need access to\?/i),
        'The reporting replica, read-only.',
      );
      await user.click(screen.getByRole('button', { name: 'Submit answers' }));

      await waitFor(() =>
        expect(api.analysis.submitAdditionalInformation).toHaveBeenCalledWith('wi-1', {
          answers: [
            {
              questionId: 'q1',
              field: 'scope',
              answer: 'The reporting replica, read-only.',
            },
          ],
        }),
      );

      expect(await screen.findAllByText('Answers submitted')).toHaveLength(2);
    });

    it('shows the pending state on the submit button while the request is in flight', async () => {
      vi.mocked(api.analysis.submitAdditionalInformation).mockReturnValue(pending());
      const user = userEvent.setup();
      renderDetail();

      await goToAiTab(user);
      await screen.findByText('Clarification needed');

      await user.type(
        screen.getByLabelText(/which database and tables does the intern need access to\?/i),
        'Anything, just to pass validation.',
      );
      const submit = screen.getByRole('button', { name: 'Submit answers' });
      await user.click(submit);

      await waitFor(() => expect(submit).toBeDisabled());
      expect(submit).toHaveAttribute('aria-busy', 'true');
    });

    it('shows a backend error and leaves the form in place so the requester can retry', async () => {
      vi.mocked(api.analysis.submitAdditionalInformation).mockRejectedValue(
        new ApiError({
          message: 'That transition is not available from the current state.',
          status: 422,
          code: 'INVALID_TRANSITION',
        }),
      );
      const user = userEvent.setup();
      renderDetail();

      await goToAiTab(user);
      await screen.findByText('Clarification needed');

      await user.type(
        screen.getByLabelText(/which database and tables does the intern need access to\?/i),
        'The reporting replica, read-only.',
      );
      await user.click(screen.getByRole('button', { name: 'Submit answers' }));

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'That transition is not available from the current state.',
      );
      expect(screen.getByRole('button', { name: 'Submit answers' })).toBeInTheDocument();
      expect(screen.queryByText('Answers submitted')).not.toBeInTheDocument();
    });
  });
});
