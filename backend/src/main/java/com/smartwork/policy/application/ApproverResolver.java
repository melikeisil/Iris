package com.smartwork.policy.application;

import com.smartwork.organization.application.DepartmentDirectory;
import com.smartwork.policy.domain.ApprovalRoute;
import com.smartwork.policy.domain.ApproverScope;
import com.smartwork.team.application.TeamDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Turns a route into an addressee (docs/08 §5.5).
 *
 * <p>Deliberately separate from {@link com.smartwork.policy.domain.PolicyEngine}: the
 * engine decides <em>what scrutiny</em> is needed, this decides <em>whose queue</em>. The
 * split is what lets a stored routing decision be replayed years later — reorganising a
 * department changes who reviews something, but must not change whether it needed review.
 *
 * <p>Every fallback chain ends somewhere reachable. A resolver that can return "nobody"
 * produces approval requests that sit in no queue, which looks exactly like work that
 * quietly stopped — the failure mode this whole product exists to prevent. The last
 * fallback is always the manager role, which is scope-addressed and therefore always
 * answerable by someone.
 */
@Service
public class ApproverResolver {

    private final TeamDirectory teams;
    private final DepartmentDirectory departments;

    public ApproverResolver(TeamDirectory teams, DepartmentDirectory departments) {
        this.teams = teams;
        this.departments = departments;
    }

    /** Everything the resolver needs about the item, without depending on the entity. */
    public record Target(
            UUID organizationId,
            UUID teamId,
            UUID departmentId,
            UUID processOwnerId,
            UUID nearestAncestorProcessOwnerId,
            UUID templateDefaultProcessOwnerId,
            UUID requesterId,
            UUID reporterId) {
    }

    @Transactional(readOnly = true)
    public ApproverScope resolve(ApprovalRoute route, Target target) {
        return switch (route) {
            case TEAM_LEAD_REVIEW -> teamLeadScope(target);
            case PROCESS_OWNER_REVIEW -> processOwnerScope(target);
            case MANAGER_APPROVAL -> new ApproverScope.ManagerRole();
            case REQUEST_MORE_INFORMATION -> requesterScope(target);
            // The team lead handles it and an ADMIN is notified separately; the request
            // itself is addressed to the lead so it appears in a queue someone works.
            case MANUAL_EXCEPTION_REVIEW -> teamLeadScope(target);
            case AUTO_ASSIGN -> throw new IllegalArgumentException(
                    "AUTO_ASSIGN creates no approval request and needs no approver");
        };
    }

    /** Team scope, falling back to the department, then to the manager role. */
    private ApproverScope teamLeadScope(Target target) {
        if (target.teamId() != null
                && teams.find(target.organizationId(), target.teamId()).isPresent()) {
            return new ApproverScope.Team(target.teamId());
        }
        if (target.departmentId() != null
                && departments.find(target.organizationId(), target.departmentId()).isPresent()) {
            return new ApproverScope.Department(target.departmentId());
        }
        return new ApproverScope.ManagerRole();
    }

    /**
     * The item's own process owner, else the nearest ancestor's, else the template
     * default, else the owning department's lead — exactly the chain in docs/08 §5.5.
     */
    private ApproverScope processOwnerScope(Target target) {
        if (target.processOwnerId() != null) {
            return new ApproverScope.Person(target.processOwnerId());
        }
        if (target.nearestAncestorProcessOwnerId() != null) {
            return new ApproverScope.Person(target.nearestAncestorProcessOwnerId());
        }
        if (target.templateDefaultProcessOwnerId() != null) {
            return new ApproverScope.Person(target.templateDefaultProcessOwnerId());
        }
        if (target.departmentId() != null) {
            return new ApproverScope.Department(target.departmentId());
        }
        return new ApproverScope.ManagerRole();
    }

    /** The requester, else whoever reported it. Never a scope: only they can answer. */
    private ApproverScope requesterScope(Target target) {
        UUID person = target.requesterId() != null ? target.requesterId() : target.reporterId();
        return person != null ? new ApproverScope.Person(person) : new ApproverScope.ManagerRole();
    }
}
