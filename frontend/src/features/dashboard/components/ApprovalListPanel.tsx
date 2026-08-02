import { Link } from 'react-router-dom';
import type { ApprovalRequest } from '@/api/types';
import { PolicyDecisionBadge } from '@/design-system/components/badges';
import { EmptyState } from '@/design-system/components/states';
import { paths } from '@/app/router/paths';
import { formatRelative, humanizeEnum } from '@/lib/format';

/**
 * A queue of policy approval requests, styled after the review-queue cards in
 * iris_team_lead_review (p-4 rounded-xl border, mono key, risk/confidence chips).
 *
 * Fed by real endpoints: GET /team-lead/review-queue, GET /approvals/pending,
 * GET /approvals/overdue and GET /manager/escalations.
 */
export const ApprovalListPanel = ({
  approvals,
  emptyTitle,
  emptyDescription,
  linkTo = (approval) => `${paths.aiReview}?approval=${approval.id}`,
}: {
  approvals: readonly ApprovalRequest[];
  emptyTitle: string;
  emptyDescription?: string;
  linkTo?: (approval: ApprovalRequest) => string;
}) => {
  if (approvals.length === 0) {
    return (
      <div className="p-6">
        <EmptyState title={emptyTitle} description={emptyDescription} />
      </div>
    );
  }

  return (
    <ul className="divide-y divide-border-structural/30">
      {approvals.slice(0, 6).map((approval) => (
        <li key={approval.id}>
          <Link
            to={linkTo(approval)}
            className="flex flex-col gap-2 px-6 py-4 transition-colors hover:bg-white/5"
          >
            <div className="flex flex-wrap items-center justify-between gap-2">
              <PolicyDecisionBadge
                route={approval.route}
                policyException={approval.policyException}
              />
              <span className="font-technical-label text-[10px] uppercase text-on-surface-variant">
                {approval.overdue ? 'Overdue' : formatRelative(approval.createdAt)}
              </span>
            </div>
            <p className="text-sm text-on-surface">
              {approval.reasons.length > 0
                ? approval.reasons.map((reason) => reason.meaning).join(' · ')
                : humanizeEnum(approval.status)}
            </p>
          </Link>
        </li>
      ))}
    </ul>
  );
};
