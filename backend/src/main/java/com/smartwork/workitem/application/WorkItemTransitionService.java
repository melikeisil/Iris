package com.smartwork.workitem.application;

import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.identity.application.UserDirectory;
import com.smartwork.workflow.application.WorkflowEngine;
import com.smartwork.workflow.domain.GuardContext;
import com.smartwork.workitem.domain.ClosureType;
import com.smartwork.workitem.domain.WorkItem;
import com.smartwork.workitem.infrastructure.WorkItemLinkRepository;
import com.smartwork.workitem.infrastructure.WorkItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Applies workflow transitions.
 *
 * <p>The API accepts a transition <em>name</em>, never a status string, so the frontend
 * cannot invent a state. The engine decides legality; this service applies the
 * consequences and cascades the effects - parent progress, and the ticket-to-development
 * hand-back.
 */
@Service
public class WorkItemTransitionService {

    private static final Logger log = LoggerFactory.getLogger(WorkItemTransitionService.class);

    /** Fires on a ticket in WAITING_DEVELOPMENT once every linked bug is finished. */
    private static final String DEV_COMPLETED = "DEV_COMPLETED";

    private final WorkItemRepository workItems;
    private final WorkItemLinkRepository links;
    private final WorkItemService workItemService;
    private final WorkItemAccessEvaluator access;
    private final GuardContextAssembler guardContexts;
    private final WorkflowEngine engine;
    private final UserDirectory users;
    private final Clock clock;

    public WorkItemTransitionService(WorkItemRepository workItems, WorkItemLinkRepository links,
                                     WorkItemService workItemService, WorkItemAccessEvaluator access,
                                     GuardContextAssembler guardContexts, WorkflowEngine engine,
                                     UserDirectory users, Clock clock) {
        this.workItems = workItems;
        this.links = links;
        this.workItemService = workItemService;
        this.access = access;
        this.guardContexts = guardContexts;
        this.engine = engine;
        this.users = users;
        this.clock = clock;
    }

    /** Transitions this actor may execute right now, with guards already evaluated. */
    @Transactional(readOnly = true)
    public List<WorkflowEngine.AvailableTransition> availableTransitions(AuthPrincipal principal,
                                                                        WorkItem item) {
        GuardContext context = guardContexts.assemble(item, principal, null, null);
        return engine.availableTransitions(item.getWorkflowDefinitionId(), context);
    }

    @Transactional
    public Result transition(AuthPrincipal principal, UUID workItemId, Command command) {
        WorkItem item = workItemService.requireVisible(principal, workItemId);

        if (command.expectedVersion() != null && item.getVersion() != command.expectedVersion()) {
            throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION,
                    "This item was changed by someone else. Reload and try again.");
        }

        // Assignment happens through a transition rather than a separate mutation, so the
        // same permission and guard checks apply - including the level restriction that
        // stops an intern being handed production work manually.
        UUID prospectiveAssignee = resolveProspectiveAssignee(principal, item, command);

        GuardContext context = guardContexts.assemble(item, principal, command.reason(), prospectiveAssignee);
        WorkflowEngine.ResolvedTransition resolved =
                engine.resolve(item.getWorkflowDefinitionId(), command.transition(), context);

        if (prospectiveAssignee != null) {
            item.assignTo(prospectiveAssignee);
        }
        if (command.reviewerId() != null) {
            requireSameOrganization(principal, command.reviewerId());
            item.setReviewer(command.reviewerId());
        }

        item.applyTransition(resolved.toState(),
                resolved.targetState().blocked(),
                resolved.targetState().terminal(),
                resolved.targetState().terminalSuccess(),
                command.reason(),
                clock.instant());

        log.info("{} {} -> {} by {} ({})", item.getReferenceKey(), resolved.fromState(),
                resolved.toState(), principal.userId(), resolved.transitionCode());

        cascade(item);

        return new Result(item, resolved);
    }

    /**
     * Application-boundary summary for callers that need the state change but must not
     * depend on workflow internals or the {@link WorkItem} entity itself.
     */
    @Transactional
    public TransitionSummary transitionSummary(AuthPrincipal principal, UUID workItemId, Command command) {
        Result result = transition(principal, workItemId, command);
        return new TransitionSummary(
                result.workItem().getId(),
                result.workItem().getReferenceKey(),
                result.workItem().getStatus());
    }

    /**
     * Effects that follow from a state change.
     *
     * <p>Kept in one place and called from one point, because a rollup that happens on
     * some paths and not others produces parents whose progress silently disagrees with
     * their children.
     */
    private void cascade(WorkItem item) {
        if (item.getParentId() != null) {
            workItemService.recalculateProgress(item.getParentId());
        }
        releaseTicketsAwaitingDevelopment(item);
    }

    /**
     * The ticket-to-development hand-back.
     *
     * <p>When a bug reaches a terminal-success state, any ticket linked {@code RESOLVED_BY}
     * to it returns to the requester for verification - provided every other bug resolving
     * that ticket is also finished. The requester never learned what a bug ticket is; they
     * just get told their problem is fixed.
     */
    private void releaseTicketsAwaitingDevelopment(WorkItem resolvedItem) {
        List<com.smartwork.workitem.domain.WorkItemLink> incoming = links.findIncoming(resolvedItem.getId());

        for (var link : incoming) {
            if (link.getLinkType() != com.smartwork.workitem.domain.LinkType.RESOLVED_BY) {
                continue;
            }
            Optional<WorkItem> maybeTicket = workItems.findById(link.getSourceId());
            if (maybeTicket.isEmpty()) {
                continue;
            }
            WorkItem ticket = maybeTicket.get();

            // Several bugs may resolve one ticket; hand back only when the last one lands.
            if (links.countUnresolvedResolvers(ticket.getId()) > 0) {
                continue;
            }

            GuardContext systemContext = systemContextFor(ticket);
            engine.resolveAutomatic(ticket.getWorkflowDefinitionId(), DEV_COMPLETED, systemContext)
                    .ifPresent(resolved -> {
                        ticket.applyTransition(resolved.toState(),
                                resolved.targetState().blocked(),
                                resolved.targetState().terminal(),
                                resolved.targetState().terminalSuccess(),
                                null, clock.instant());
                        log.info("{} released from {} to {} - all linked development work is complete",
                                ticket.getReferenceKey(), resolved.fromState(), resolved.toState());
                    });
        }
    }

    /**
     * Context for a system-applied transition. No actor, so no roles and no relationships:
     * an automatic edge is defined in configuration with neither, and
     * {@code resolveAutomatic} skips the permission check but still runs the guards.
     */
    private GuardContext systemContextFor(WorkItem item) {
        return new GuardContext(
                item.getId(), item.getWorkItemType().name(), item.getStatus(), null,
                null, java.util.Set.of(), java.util.Set.of(), null,
                item.getAssigneeId(), item.getReporterId(), item.getProcessOwnerId(), null,
                item.getAcceptanceCriteria(), item.getClosureType().name(),
                item.getPriority().name(), item.getEstimatedHours(),
                false, false, java.util.Set.of(),
                item.getSprintId(), item.getSprintId() != null,
                item.isSecuritySensitive(), item.isProductionImpacting(),
                item.isBudgetSensitive(), item.isComplianceSensitive());
    }

    private UUID resolveProspectiveAssignee(AuthPrincipal principal, WorkItem item, Command command) {
        if (command.assigneeId() == null) {
            return null;
        }
        // Assigning to oneself is claiming work; assigning to someone else is delegation,
        // and only a Team Lead or Manager may do that.
        boolean selfAssign = command.assigneeId().equals(principal.userId());
        if (!selfAssign && !access.canAssign(principal, item)) {
            throw ApiException.forbidden(
                    "You cannot assign this work item to someone else. Request a reassignment instead.");
        }
        requireSameOrganization(principal, command.assigneeId());
        return command.assigneeId();
    }

    private void requireSameOrganization(AuthPrincipal principal, UUID userId) {
        if (users.findSummary(principal.organizationId(), userId).isEmpty()) {
            throw ApiException.notFound("User", userId);
        }
    }

    /**
     * Fires a system-applied transition if one is legal from the item's current state,
     * skipping the actor-permission check but still running guards - the same rule
     * {@link WorkflowEngine#resolveAutomatic} already applies.
     *
     * <p>The public entry point other modules use to drive automatic edges: {@code
     * analysis} calls this for {@code ANALYZE} (so an item does not sit in NEW/BACKLOG
     * forever just because nothing asked it to move) and for {@code NEEDS_INFO} (when the
     * AI says it cannot proceed without more information). Returns empty, not an error,
     * when the transition does not apply from the current state or is not defined for this
     * workflow profile - callers outside this module cannot know that in advance without
     * duplicating the workflow engine's own logic, so this method is the honest way to ask.
     */
    @Transactional
    public Optional<WorkflowEngine.ResolvedTransition> applyAutomatic(UUID workItemId, String transitionCode) {
        WorkItem item = workItems.findById(workItemId)
                .orElseThrow(() -> ApiException.notFound("Work item", workItemId));

        GuardContext context = systemContextFor(item);
        return engine.resolveAutomatic(item.getWorkflowDefinitionId(), transitionCode, context)
                .map(resolved -> {
                    item.applyTransition(resolved.toState(),
                            resolved.targetState().blocked(),
                            resolved.targetState().terminal(),
                            resolved.targetState().terminalSuccess(),
                            null, clock.instant());
                    log.info("{} {} -> {} (system, {})", item.getReferenceKey(),
                            resolved.fromState(), resolved.toState(), transitionCode);
                    cascade(item);
                    return resolved;
                });
    }

    /** Convenience wrapper for the BLOCK transition, which the UI exposes as its own button. */
    @Transactional
    public Result block(AuthPrincipal principal, UUID workItemId, String reason, Long expectedVersion) {
        return transition(principal, workItemId,
                new Command("BLOCK", reason, null, null, expectedVersion));
    }

    /**
     * Sets the closure policy when a ticket is converted to development work.
     *
     * <p>Forced to REQUESTER_VERIFICATION: whoever reported the problem confirms it is
     * fixed, regardless of who fixed it or in which team.
     */
    @Transactional
    public void markConvertedToDevelopment(UUID ticketId) {
        workItems.findById(ticketId).ifPresent(ticket ->
                ticket.setClosureType(ClosureType.REQUESTER_VERIFICATION));
    }

    public record Command(String transition, String reason, UUID assigneeId,
                          UUID reviewerId, Long expectedVersion) {
    }

    public record Result(WorkItem workItem, WorkflowEngine.ResolvedTransition transition) {
    }

    public record TransitionSummary(UUID workItemId, String referenceKey, String status) {
    }
}
