package com.smartwork.workitem.application;

import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.common.security.ResourceRole;
import com.smartwork.workitem.domain.WorkItem;
import com.smartwork.workitem.infrastructure.WorkItemRepository;
import com.smartwork.workitem.infrastructure.WorkItemWatcherRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Resource-scoped authorization: layer 3 of the four described in
 * docs/03-roles-and-authorization.md.
 *
 * <p>Derives the caller's per-item relationships (axis 2) and answers read and write
 * questions from the intersection of those and their system roles (axis 1).
 */
@Component("workItemAccess")
@Transactional(readOnly = true)
public class WorkItemAccessEvaluator {

    private final WorkItemRepository workItems;
    private final WorkItemWatcherRepository watchers;

    public WorkItemAccessEvaluator(WorkItemRepository workItems, WorkItemWatcherRepository watchers) {
        this.workItems = workItems;
        this.watchers = watchers;
    }

    /**
     * What this caller is to this item.
     *
     * <p>Process ownership inherits down the hierarchy: the owner of an epic owns its
     * stories and tasks unless one of them names someone else. That is how "Process Owner
     * sees cross-team plans" works without listing every task.
     */
    public Set<ResourceRole> relationshipsOf(AuthPrincipal principal, WorkItem item) {
        Set<ResourceRole> roles = new LinkedHashSet<>();
        UUID actorId = principal.userId();

        if (actorId.equals(item.getReporterId())) {
            // REQUESTER is the reporter of a ticket or incident specifically; on a story,
            // the reporter is just the creator and carries no special authority.
            if (item.getWorkFamily().isSlaTracked()) {
                roles.add(ResourceRole.REQUESTER);
            }
        }
        if (actorId.equals(item.getAssigneeId())) {
            roles.add(ResourceRole.ASSIGNEE);
        }
        if (actorId.equals(item.getReviewerId())) {
            roles.add(ResourceRole.REVIEWER);
        }
        if (isProcessOwner(actorId, item)) {
            roles.add(ResourceRole.PROCESS_OWNER);
        }
        if (watchers.existsByWorkItemIdAndUserId(item.getId(), actorId)) {
            roles.add(ResourceRole.WATCHER);
        }
        return roles;
    }

    private boolean isProcessOwner(UUID actorId, WorkItem item) {
        if (actorId.equals(item.getProcessOwnerId())) {
            return true;
        }
        // Walk up until an ancestor names an owner. Bounded, because the hierarchy is
        // acyclic by construction (LinkCycleDetector) and shallow in practice.
        UUID parentId = item.getParentId();
        int depth = 0;
        while (parentId != null && depth++ < 20) {
            Optional<WorkItem> parent = workItems.findById(parentId);
            if (parent.isEmpty()) {
                return false;
            }
            WorkItem ancestor = parent.get();
            if (ancestor.getProcessOwnerId() != null) {
                return actorId.equals(ancestor.getProcessOwnerId());
            }
            parentId = ancestor.getParentId();
        }
        return false;
    }

    public boolean canRead(AuthPrincipal principal, WorkItem item) {
        if (!item.getOrganizationId().equals(principal.organizationId())) {
            return false;
        }

        boolean related = !relationshipsOf(principal, item).isEmpty();

        if (item.isSecuritySensitive()) {
            // Security-flagged work narrows to people directly involved, plus managers of
            // the owning department. A team lead's usual team-wide visibility is not enough.
            return related || (principal.isManager() && inManagerScope(principal, item));
        }
        if (related) {
            return true;
        }
        if (principal.isManager() && (principal.organizationWide() || inManagerScope(principal, item))) {
            return true;
        }
        return principal.hasRole("TEAM_LEAD")
                && item.getTeamId() != null
                && principal.isMemberOf(item.getTeamId());
    }

    private boolean inManagerScope(AuthPrincipal principal, WorkItem item) {
        return principal.organizationWide()
                || (principal.departmentId() != null
                    && principal.departmentId().equals(item.getDepartmentId()));
    }

    /**
     * Content edits. Deliberately narrower than reads, and note what is <em>not</em> here:
     * there is no path by which an EMPLOYEE can set another person as assignee. That is a
     * Team Lead action, and an employee who wants to hand work over files a reassignment
     * request instead - which is what keeps workload data honest.
     */
    public boolean canEdit(AuthPrincipal principal, WorkItem item) {
        if (!canRead(principal, item)) {
            return false;
        }
        Set<ResourceRole> relationships = relationshipsOf(principal, item);
        if (relationships.contains(ResourceRole.ASSIGNEE)
                || relationships.contains(ResourceRole.REQUESTER)
                || relationships.contains(ResourceRole.PROCESS_OWNER)) {
            return true;
        }
        if (principal.hasRole("TEAM_LEAD") && item.getTeamId() != null
                && principal.isMemberOf(item.getTeamId())) {
            return true;
        }
        return principal.isManager() && inManagerScope(principal, item);
    }

    /** Only a Team Lead over the owning team, or a manager over the department. */
    public boolean canAssign(AuthPrincipal principal, WorkItem item) {
        if (!canRead(principal, item)) {
            return false;
        }
        if (principal.hasRole("TEAM_LEAD") && item.getTeamId() != null
                && principal.isMemberOf(item.getTeamId())) {
            return true;
        }
        return principal.isManager() && inManagerScope(principal, item);
    }

    /**
     * Whether internal comments are visible. A pure REQUESTER sees only public ones; the
     * delivering side sees everything.
     */
    public boolean canSeeInternalComments(AuthPrincipal principal, WorkItem item) {
        Set<ResourceRole> relationships = relationshipsOf(principal, item);
        boolean onlyRequester = relationships.contains(ResourceRole.REQUESTER)
                && !relationships.contains(ResourceRole.ASSIGNEE)
                && !relationships.contains(ResourceRole.PROCESS_OWNER)
                && !relationships.contains(ResourceRole.REVIEWER);

        if (onlyRequester && !principal.hasRole("TEAM_LEAD") && !principal.isManager()) {
            return false;
        }
        return canRead(principal, item);
    }
}
