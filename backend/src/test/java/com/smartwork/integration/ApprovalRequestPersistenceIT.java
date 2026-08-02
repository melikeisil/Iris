package com.smartwork.integration;

import com.smartwork.policy.domain.ApprovalRequest;
import com.smartwork.policy.domain.ApprovalRoute;
import com.smartwork.policy.domain.ApproverScope;
import com.smartwork.policy.domain.ReasonCode;
import com.smartwork.policy.domain.RoutingDecision;
import com.smartwork.policy.infrastructure.ApprovalRequestRepository;
import com.smartwork.support.IntegrationTest;
import com.smartwork.support.TestFixtures;
import com.smartwork.workitem.application.WorkItemService;
import com.smartwork.workitem.domain.OriginType;
import com.smartwork.workitem.domain.Priority;
import com.smartwork.workitem.domain.WorkItem;
import com.smartwork.workitem.domain.WorkItemType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ApprovalRequestPersistenceIT {

    @Autowired
    private TestFixtures fixtures;
    @Autowired
    private WorkItemService workItems;
    @Autowired
    private ApprovalRequestRepository approvalRequests;

    @Test
    void aPersonScopedApprovalRequestPersists() {
        UUID organizationId = fixtures.organization("APR" + System.nanoTime() % 100000);
        UUID departmentId = fixtures.department(organizationId, "HR").id();
        UUID requesterId = fixtures.user(
                organizationId,
                "requester-" + UUID.randomUUID() + "@acme.io",
                Set.of(com.smartwork.identity.domain.SystemRole.EMPLOYEE),
                departmentId);

        var principal = new com.smartwork.common.security.AuthPrincipal(
                requesterId, organizationId, departmentId, Set.of(),
                Set.of("EMPLOYEE"), "MID", false);

        WorkItem item = workItems.create(principal, new WorkItemService.CreateCommand(
                WorkItemType.REQUEST, OriginType.USER_CREATED,
                "Need clarification about onboarding scope",
                "The requester must answer a follow-up question.",
                null, null, null, null, departmentId, null,
                Priority.HIGH, null, null, null, null));

        RoutingDecision decision = new RoutingDecision(
                ApprovalRoute.REQUEST_MORE_INFORMATION,
                List.of(ReasonCode.MORE_INFORMATION_REQUIRED),
                "We need a direct answer from the requester.",
                Duration.ofHours(24),
                null, null, 1);

        ApprovalRequest saved = approvalRequests.save(ApprovalRequest.open(
                organizationId, item.getId(), UUID.randomUUID(),
                decision, new ApproverScope.Person(requesterId),
                Instant.parse("2026-08-01T18:00:00Z")));

        assertThat(saved.getAssignedToUserId()).isEqualTo(requesterId);
        assertThat(saved.getAssignedToScope()).isEqualTo("USER:" + requesterId);
    }
}
