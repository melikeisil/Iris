import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from '@/components/shell/AppShell';
import { CenteredLayout } from '../layouts/CenteredLayout';
import { RequireAuth } from '../guards/RequireAuth';
import { RequireCapability } from '../guards/RequireCapability';
import { LoadingState } from '@/design-system/components/states';
import { paths } from './paths';

/**
 * Exactly nine routes plus the not-found fallback. Anything else is out of MVP scope
 * and must not be reachable - see frontend/README.md, "Scope".
 *
 * Routes are code-split at the feature boundary so the first paint after sign-in
 * carries only the shell and the dashboard.
 */
const LoginPage = lazy(() => import('@/features/auth/LoginPage'));
const DashboardPage = lazy(() => import('@/features/dashboard/DashboardPage'));
const CreateRequestPage = lazy(() => import('@/features/tickets/CreateRequestPage'));
const MyWorkPage = lazy(() => import('@/features/my-work/MyWorkPage'));
const WorkItemDetailPage = lazy(() => import('@/features/work-items/WorkItemDetailPage'));
const AiReviewPage = lazy(() => import('@/features/team-lead/AiReviewPage'));
const ManagerOverviewPage = lazy(() => import('@/features/manager/ManagerOverviewPage'));
const HelpCenterPage = lazy(() => import('@/features/help/HelpCenterPage'));
const SettingsPage = lazy(() => import('@/features/settings/SettingsPage'));
const NotFoundPage = lazy(() => import('@/features/misc/NotFoundPage'));

const Fallback = () => <LoadingState variant="page" label="Loading" />;

export const AppRoutes = () => (
  <Suspense fallback={<Fallback />}>
    <Routes>
      <Route element={<CenteredLayout />}>
        <Route path={paths.login} element={<LoginPage />} />
      </Route>

      {/* Everything else requires a session. */}
      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route index element={<Navigate to={paths.dashboard} replace />} />
          <Route path={paths.dashboard} element={<DashboardPage />} />
          <Route path={paths.newRequest} element={<CreateRequestPage />} />
          <Route path={paths.myWork} element={<MyWorkPage />} />
          <Route path={paths.workItemPattern} element={<WorkItemDetailPage />} />
          <Route path={paths.help} element={<HelpCenterPage />} />
          <Route path={paths.settings} element={<SettingsPage />} />

          <Route element={<RequireCapability anyOf={['team.review_queue', 'manager.dashboard']} />}>
            <Route path={paths.aiReview} element={<AiReviewPage />} />
          </Route>

          <Route element={<RequireCapability anyOf={['manager.dashboard', 'manager.escalations']} />}>
            <Route path={paths.manager} element={<ManagerOverviewPage />} />
          </Route>

          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  </Suspense>
);
