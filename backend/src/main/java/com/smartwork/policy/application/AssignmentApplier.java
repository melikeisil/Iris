package com.smartwork.policy.application;

import com.smartwork.assignment.application.AssignmentHistory;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.policy.domain.RoutingDecision;
import com.smartwork.workitem.application.WorkItemAssignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Places work on a person, and records why.
 *
 * <p>Two writes that must not come apart: the work item's current assignee, and a row in
 * {@code assignments} describing how it got there. The second is not bookkeeping — the
 * fairness score counts assignments in the last fourteen days, and an item reassigned away
 * still consumed that person's attention. A single {@code assignee_id} column cannot answer
 * that after the fact, which is why both happen here, in one transaction.
 */
@Service
public class AssignmentApplier {

    private static final Logger log = LoggerFactory.getLogger(AssignmentApplier.class);

    private final WorkItemAssignment workItems;
    private final AssignmentHistory history;

    public AssignmentApplier(WorkItemAssignment workItems, AssignmentHistory history) {
        this.workItems = workItems;
        this.history = history;
    }

    /**
     * Assignment with no human in the loop.
     *
     * <p>Runs as the system rather than as the caller: nobody chose this person, the policy
     * did, and attributing it to whoever happened to file the ticket would make the audit
     * trail actively misleading.
     */
    @Transactional
    public void assignAutomatically(
            AuthPrincipal principal, UUID workItemId, UUID assigneeId,
            UUID recommendationId, RoutingDecision decision) {

        workItems.assignBySystem(principal.organizationId(), workItemId, assigneeId);
        history.record(new AssignmentHistory.Entry(
                principal.organizationId(), workItemId, assigneeId,
                AssignmentHistory.Source.AUTO, recommendationId, null,
                decision.explanation()));

        log.info("Work item {} auto-assigned to {}: {}", workItemId, assigneeId, decision.reasonCodesCsv());
    }

    /**
     * Assignment following a human approval. {@code decidedBy} is recorded as the actor,
     * because a reviewer approving a recommendation is making the decision themselves —
     * "the AI suggested it" is not an accountability model.
     */
    @Transactional
    public void assignAfterApproval(
            UUID organizationId, UUID workItemId, UUID assigneeId, UUID recommendationId,
            UUID decidedBy, AssignmentHistory.Source source, String note) {

        workItems.assignBySystem(organizationId, workItemId, assigneeId);
        history.record(new AssignmentHistory.Entry(
                organizationId, workItemId, assigneeId, source, recommendationId, decidedBy, note));

        log.info("Work item {} assigned to {} by {} after {} approval",
                workItemId, assigneeId, decidedBy, source);
    }
}
