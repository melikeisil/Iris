package com.smartwork.workitem.application;

import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.identity.application.UserDirectory;
import com.smartwork.workflow.application.WorkflowCatalog;
import com.smartwork.workflow.domain.GuardContext;
import com.smartwork.workitem.domain.WorkItem;
import com.smartwork.workitem.infrastructure.WorkItemLinkRepository;
import com.smartwork.workitem.infrastructure.WorkItemRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Gathers the facts a guard needs.
 *
 * <p>The workflow module deliberately knows nothing about work items, users or sprints;
 * this class is the seam. It costs a handful of queries per transition, which is the
 * price of keeping the state machine pure and unit-testable.
 */
@Component
@Transactional(readOnly = true)
public class GuardContextAssembler {

    private final WorkItemRepository workItems;
    private final WorkItemLinkRepository links;
    private final WorkItemAccessEvaluator accessEvaluator;
    private final WorkflowCatalog workflowCatalog;
    private final UserDirectory users;

    public GuardContextAssembler(WorkItemRepository workItems, WorkItemLinkRepository links,
                                 WorkItemAccessEvaluator accessEvaluator,
                                 WorkflowCatalog workflowCatalog, UserDirectory users) {
        this.workItems = workItems;
        this.links = links;
        this.accessEvaluator = accessEvaluator;
        this.workflowCatalog = workflowCatalog;
        this.users = users;
    }

    public GuardContext assemble(WorkItem item, AuthPrincipal principal, String reason,
                                 UUID prospectiveAssigneeId) {
        UUID effectiveAssignee = prospectiveAssigneeId != null
                ? prospectiveAssigneeId : item.getAssigneeId();

        return new GuardContext(
                item.getId(),
                item.getWorkItemType().name(),
                item.getStatus(),
                null,                         // filled by the engine once the edge is known
                principal.userId(),
                principal.roles(),
                accessEvaluator.relationshipsOf(principal, item),
                reason,
                effectiveAssignee,
                item.getReporterId(),
                item.getProcessOwnerId(),
                levelOf(item.getOrganizationId(), effectiveAssignee),
                item.getAcceptanceCriteria(),
                item.getClosureType().name(),
                item.getPriority().name(),
                item.getEstimatedHours(),
                hasChildren(item),
                hasUnfinishedChildren(item),
                openDependencyKeys(item),
                item.getSprintId(),
                item.getSprintId() != null,   // sprint activity is verified in Phase 5
                item.isSecuritySensitive(),
                item.isProductionImpacting(),
                item.isBudgetSensitive(),
                item.isComplianceSensitive());
    }

    private String levelOf(UUID organizationId, UUID userId) {
        if (userId == null) {
            return null;
        }
        return users.findSummary(organizationId, userId)
                .map(UserDirectory.UserSummary::employeeLevel)
                .orElse(null);
    }

    private boolean hasChildren(WorkItem item) {
        return workItems.countChildren(item.getId()) > 0;
    }

    /**
     * A child counts as finished when its own workflow says so. Asking each child's
     * profile rather than matching status names is what lets a parent ticket roll up a
     * BUG in {@code VERIFIED} and a TASK in {@code DONE} without knowing either profile.
     */
    private boolean hasUnfinishedChildren(WorkItem item) {
        List<WorkItem> children = workItems.findChildren(item.getId());
        return children.stream().anyMatch(child -> !isTerminalSuccess(child));
    }

    private boolean isTerminalSuccess(WorkItem item) {
        return workflowCatalog.requireState(item.getWorkflowDefinitionId(), item.getStatus())
                .terminalSuccess();
    }

    private Set<String> openDependencyKeys(WorkItem item) {
        return new LinkedHashSet<>(links.findOpenDependencyKeys(item.getId()));
    }
}
