package com.smartwork.workitem.application;

import com.smartwork.common.error.ApiException;
import com.smartwork.identity.application.UserDirectory;
import com.smartwork.workitem.domain.WorkItem;
import com.smartwork.workitem.infrastructure.WorkItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WorkItemAssignmentService implements WorkItemAssignment {

    private final WorkItemRepository workItems;
    private final UserDirectory users;

    public WorkItemAssignmentService(WorkItemRepository workItems, UserDirectory users) {
        this.workItems = workItems;
        this.users = users;
    }

    @Override
    @Transactional
    public void assignBySystem(UUID organizationId, UUID workItemId, UUID assigneeId) {
        WorkItem item = require(organizationId, workItemId);

        // No actor check — see the interface Javadoc — but the tenancy check stays. An
        // assignee from another organization would be a cross-tenant leak that the
        // absence of an actor makes easier to miss, not harder.
        UserDirectory.AuthenticationView assignee = users.findForAuthentication(assigneeId)
                .filter(user -> user.organizationId().equals(organizationId))
                .orElseThrow(() -> ApiException.notFound("User", assigneeId));

        item.assignTo(assignee.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public RoutingView routingView(UUID organizationId, UUID workItemId) {
        WorkItem item = require(organizationId, workItemId);
        return new RoutingView(
                item.getId(),
                item.getOrganizationId(),
                item.getReferenceKey(),
                item.getStatus(),
                item.getTeamId(),
                item.getDepartmentId(),
                item.getProjectId(),
                item.getProcessOwnerId(),
                item.getReporterId(),
                item.getAssigneeId(),
                item.getReviewerId(),
                item.getWorkItemType().name(),
                item.getWorkFamily().name(),
                item.getPriority(),
                item.getRiskLevel(),
                item.getEstimatedHours(),
                item.getDueDate(),
                item.isSecuritySensitive(),
                item.isProductionImpacting(),
                item.isBudgetSensitive(),
                item.isComplianceSensitive(),
                workItems.countChildren(item.getId()) == 0,
                item.getUpdatedAt());
    }

    private WorkItem require(UUID organizationId, UUID workItemId) {
        return workItems.findByIdInOrganization(workItemId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Work item", workItemId));
    }
}
