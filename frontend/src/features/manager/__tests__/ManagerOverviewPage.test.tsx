import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '@/api/client/endpoints';
import { renderWithProviders } from '@/test/utils';
import { emptyPage, makeApproval, managerUser } from '@/test/fixtures';

vi.mock('@/api/client/endpoints', async () => {
  const { createApiMock } = await import('@/test/apiMock');
  return { api: createApiMock() };
});

const {
  useManagerEscalations,
  useOverdueApprovals,
  usePendingApprovals,
  useApproveRecommendation,
  useRejectRecommendation,
  useWorkItem,
  useDashboard,
  useToast,
} = vi.hoisted(() => ({
  useManagerEscalations: vi.fn(),
  useOverdueApprovals: vi.fn(),
  usePendingApprovals: vi.fn(),
  useApproveRecommendation: vi.fn(),
  useRejectRecommendation: vi.fn(),
  useWorkItem: vi.fn(),
  useDashboard: vi.fn(),
  useToast: vi.fn(),
}));

vi.mock('@/features/team-lead/hooks/useReviewQueue', () => ({
  useManagerEscalations,
  useOverdueApprovals,
  usePendingApprovals,
  useApproveRecommendation,
  useRejectRecommendation,
}));

vi.mock('@/features/work-items/hooks/useWorkItems', () => ({
  useWorkItem,
}));

vi.mock('@/features/dashboard/useDashboard', () => ({
  useDashboard,
}));

vi.mock('@/app/providers/ToastProvider', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/app/providers/ToastProvider')>();
  return {
    ...actual,
    useToast,
  };
});

vi.mock('@/hooks/useDocumentTitle', () => ({
  useDocumentTitle: vi.fn(),
}));

import { ManagerOverviewPage } from '../ManagerOverviewPage';

describe('ManagerOverviewPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.auth.me).mockResolvedValue(managerUser());

    useManagerEscalations.mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });
    useOverdueApprovals.mockReturnValue({
      data: [],
      refetch: vi.fn(),
    });
    usePendingApprovals.mockReturnValue({
      data: emptyPage(),
      refetch: vi.fn(),
    });
    useApproveRecommendation.mockReturnValue({
      isPending: false,
      mutate: vi.fn(),
    });
    useRejectRecommendation.mockReturnValue({
      isPending: false,
      mutate: vi.fn(),
    });
    useWorkItem.mockReturnValue({
      data: null,
      isLoading: false,
      error: null,
    });
    useDashboard.mockReturnValue({
      manager: {
        blockedCount: { data: 0 },
        overdueCount: { data: 0 },
      },
    });
    useToast.mockReturnValue({
      notify: vi.fn(),
    });
  });

  it('shows manager approvals from the pending queue even when they are not escalated or overdue', async () => {
    usePendingApprovals.mockReturnValue({
      data: emptyPage(),
      refetch: vi.fn(),
    });
    usePendingApprovals.mockReturnValueOnce({
      data: {
        ...emptyPage(),
        content: [
          makeApproval({
            id: 'approval-pending',
            workItemId: 'wi-pending',
            route: 'MANAGER_APPROVAL',
            reasons: [{ code: 'BUDGET_SENSITIVE', meaning: 'Budget-sensitive work needs manager approval' }],
          }),
        ],
        page: { number: 0, size: 25, totalElements: 1, totalPages: 1 },
      },
      refetch: vi.fn(),
    });

    renderWithProviders(<ManagerOverviewPage />, { route: '/manager' });

    expect(screen.getByRole('heading', { name: /operational overview/i })).toBeInTheDocument();
    expect(screen.getAllByText('Budget-sensitive work needs manager approval')).toHaveLength(2);
    const decisionsCard = screen.getByText('Decisions required').parentElement?.parentElement;
    expect(decisionsCard).toHaveTextContent('1');
  });

  it('refreshes the pending queue alongside manager escalations and overdue approvals', async () => {
    const pendingRefetch = vi.fn();
    const escalationsRefetch = vi.fn();
    const overdueRefetch = vi.fn();

    usePendingApprovals.mockReturnValue({ data: emptyPage(), refetch: pendingRefetch });
    useManagerEscalations.mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      refetch: escalationsRefetch,
    });
    useOverdueApprovals.mockReturnValue({ data: [], refetch: overdueRefetch });

    const user = userEvent.setup();
    renderWithProviders(<ManagerOverviewPage />, { route: '/manager' });

    await user.click(screen.getByRole('button', { name: /refresh/i }));

    await waitFor(() => {
      expect(pendingRefetch).toHaveBeenCalledTimes(1);
      expect(escalationsRefetch).toHaveBeenCalledTimes(1);
      expect(overdueRefetch).toHaveBeenCalledTimes(1);
    });
  });
});
