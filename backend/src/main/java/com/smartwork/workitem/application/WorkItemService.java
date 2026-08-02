package com.smartwork.workitem.application;

import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.identity.application.UserDirectory;
import com.smartwork.project.application.ProjectDirectory;
import com.smartwork.workflow.application.WorkflowCatalog;
import com.smartwork.workitem.domain.ClosureType;
import com.smartwork.workitem.domain.Complexity;
import com.smartwork.workitem.domain.OriginType;
import com.smartwork.workitem.domain.Priority;
import com.smartwork.workitem.domain.ProgressCalculator;
import com.smartwork.workitem.domain.RiskLevel;
import com.smartwork.workitem.domain.WorkFamily;
import com.smartwork.workitem.domain.WorkFamilyResolver;
import com.smartwork.workitem.domain.WorkItem;
import com.smartwork.workitem.domain.WorkItemComment;
import com.smartwork.workitem.domain.WorkItemType;
import com.smartwork.workitem.domain.WorkItemValidator;
import com.smartwork.workitem.domain.WorkItemWatcher;
import com.smartwork.workitem.infrastructure.ReferenceKeyGenerator;
import com.smartwork.workitem.infrastructure.WorkItemCommentRepository;
import com.smartwork.workitem.infrastructure.WorkItemRepository;
import com.smartwork.workitem.infrastructure.WorkItemWatcherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Creation, update and lifecycle of work items. Transitions live in {@link WorkItemTransitionService}. */
@Service
@Transactional(readOnly = true)
public class WorkItemService {

    private static final Logger log = LoggerFactory.getLogger(WorkItemService.class);

    private final WorkItemRepository workItems;
    private final WorkItemCommentRepository comments;
    private final WorkItemWatcherRepository watchers;
    private final WorkItemAccessEvaluator access;
    private final WorkflowCatalog workflows;
    private final ProjectDirectory projects;
    private final UserDirectory users;
    private final ReferenceKeyGenerator referenceKeys;
    private final Clock clock;

    public WorkItemService(WorkItemRepository workItems, WorkItemCommentRepository comments,
                           WorkItemWatcherRepository watchers, WorkItemAccessEvaluator access,
                           WorkflowCatalog workflows, ProjectDirectory projects,
                           UserDirectory users, ReferenceKeyGenerator referenceKeys, Clock clock) {
        this.workItems = workItems;
        this.comments = comments;
        this.watchers = watchers;
        this.access = access;
        this.workflows = workflows;
        this.projects = projects;
        this.users = users;
        this.referenceKeys = referenceKeys;
        this.clock = clock;
    }

    @Transactional
    public WorkItem create(AuthPrincipal principal, CreateCommand command) {
        return persistCreated(principal, command);
    }

    @Transactional
    public CreatedItemView createForProcess(AuthPrincipal principal, CreateCommand command) {
        return CreatedItemView.from(persistCreated(principal, command));
    }

    private WorkItem persistCreated(AuthPrincipal principal, CreateCommand command) {
        UUID organizationId = principal.organizationId();

        WorkItem parent = command.parentId() == null ? null : requireVisible(principal, command.parentId());

        WorkItemValidator.validateForCreation(
                new WorkItemValidator.Command(command.type(), command.summary(), command.parentId(),
                        command.projectId(), command.storyPoints(), command.estimatedHours(),
                        command.dueDate(), command.priority()),
                parent == null ? null : parent.getWorkItemType(),
                LocalDate.now(clock.withZone(ZoneOffset.UTC)));

        WorkFamily family = WorkFamilyResolver.resolve(
                command.type(), command.originType(), parent == null ? null : parent.getWorkFamily());

        WorkflowCatalog.DefinitionView workflow = workflows.requireByCode(
                organizationId, WorkFamilyResolver.workflowCodeFor(command.type(), family));

        String projectPrefix = command.projectId() == null ? null
                : projects.find(organizationId, command.projectId())
                        .map(ProjectDirectory.ProjectView::keyPrefix)
                        .orElseThrow(() -> ApiException.notFound("Project", command.projectId()));

        String referenceKey = referenceKeys.generate(organizationId, command.type(), projectPrefix);

        WorkItem item = new WorkItem(organizationId, referenceKey, command.type(), family,
                command.originType(), workflow.id(), workflow.initialState(),
                command.summary().trim(), principal.userId());

        item.updateContent(command.summary().trim(), command.description(), command.acceptanceCriteria());
        item.updatePlanning(command.priority(), command.dueDate(), command.estimatedHours(),
                command.storyPoints(), command.complexity());
        item.placeIn(command.projectId(), command.sprintId(), command.parentId());
        item.redirect(command.departmentId(), command.teamId(), family);

        // An incident is at least HIGH: the validator rejects LOW, and MEDIUM is raised
        // rather than refused, because arguing about severity while production is down
        // helps nobody.
        if (command.type() == WorkItemType.INCIDENT && !command.priority().isAtLeast(Priority.HIGH)) {
            item.updatePlanning(Priority.HIGH, command.dueDate(), command.estimatedHours(),
                    command.storyPoints(), command.complexity());
        }

        WorkItem saved = workItems.save(item);

        // The creator watches their own item, so they are notified without having to
        // remember to subscribe.
        watchers.save(new WorkItemWatcher(saved.getId(), principal.userId()));

        log.info("Created {} {} ({}) in organization {}", command.type(), saved.getReferenceKey(),
                saved.getId(), organizationId);
        return saved;
    }

    @Transactional
    public WorkItem update(AuthPrincipal principal, UUID workItemId, UpdateCommand command, Long expectedVersion) {
        WorkItem item = requireEditable(principal, workItemId);
        requireVersion(item, expectedVersion);

        WorkItemValidator.validateForUpdate(
                new WorkItemValidator.Command(item.getWorkItemType(), command.summary(),
                        item.getParentId(), item.getProjectId(), command.storyPoints(),
                        command.estimatedHours(), command.dueDate(), command.priority()),
                LocalDate.now(clock.withZone(ZoneOffset.UTC)));

        item.updateContent(command.summary().trim(), command.description(), command.acceptanceCriteria());
        item.updatePlanning(command.priority(), command.dueDate(), command.estimatedHours(),
                command.storyPoints(), command.complexity());

        // Sensitivity flags escalate freely but only a manager may clear one: a flag that
        // anyone can remove is not a control.
        if (command.sensitivity() != null) {
            applySensitivity(principal, item, command.sensitivity());
        }
        return item;
    }

    private void applySensitivity(AuthPrincipal principal, WorkItem item, Sensitivity requested) {
        boolean clearing = (item.isSecuritySensitive() && !requested.security())
                || (item.isProductionImpacting() && !requested.production())
                || (item.isBudgetSensitive() && !requested.budget())
                || (item.isComplianceSensitive() && !requested.compliance());

        if (clearing && !principal.isManager()) {
            throw ApiException.forbidden(
                    "Only a manager can clear a security, production, budget or compliance flag.");
        }
        item.setSensitivity(requested.security(), requested.production(),
                requested.budget(), requested.compliance());
    }

    @Transactional
    public void updateRemainingHours(AuthPrincipal principal, UUID workItemId, BigDecimal remainingHours) {
        WorkItem item = requireEditable(principal, workItemId);
        if (remainingHours != null && remainingHours.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Remaining hours cannot be negative.");
        }
        item.updateRemainingHours(remainingHours);
    }

    @Transactional
    public WorkItemComment comment(AuthPrincipal principal, UUID workItemId, String body, boolean internal) {
        WorkItem item = requireVisible(principal, workItemId);
        if (internal && !access.canSeeInternalComments(principal, item)) {
            throw ApiException.forbidden("You cannot post an internal comment on this item.");
        }
        return comments.save(new WorkItemComment(item.getId(), principal.userId(), body.trim(), internal));
    }

    public List<WorkItemComment> comments(AuthPrincipal principal, UUID workItemId) {
        WorkItem item = requireVisible(principal, workItemId);
        return comments.findForItem(item.getId(), access.canSeeInternalComments(principal, item));
    }

    @Transactional
    public void watch(AuthPrincipal principal, UUID workItemId) {
        WorkItem item = requireVisible(principal, workItemId);
        if (!watchers.existsByWorkItemIdAndUserId(item.getId(), principal.userId())) {
            watchers.save(new WorkItemWatcher(item.getId(), principal.userId()));
        }
    }

    @Transactional
    public void unwatch(AuthPrincipal principal, UUID workItemId) {
        WorkItem item = requireVisible(principal, workItemId);
        watchers.deleteByWorkItemIdAndUserId(item.getId(), principal.userId());
    }

    /**
     * Recomputes and stores the parent's progress.
     *
     * <p>Called after any child change. Only direct children count - grandchildren roll up
     * through their own parent, which keeps this O(children) rather than O(subtree).
     */
    @Transactional
    public ProgressCalculator.Result recalculateProgress(UUID parentId) {
        WorkItem parent = workItems.findById(parentId)
                .orElseThrow(() -> ApiException.notFound("Work item", parentId));

        List<ProgressCalculator.Child> children = workItems.findChildren(parentId).stream()
                .map(child -> {
                    var state = workflows.requireState(child.getWorkflowDefinitionId(), child.getStatus());
                    boolean cancelled = state.terminal() && !state.terminalSuccess();
                    return new ProgressCalculator.Child(state.terminalSuccess(), cancelled,
                            state.blocked(), child.getEstimatedHours(), child.getStoryPoints());
                })
                .toList();

        ProgressCalculator.Result result =
                ProgressCalculator.calculate(parent.getWorkItemType(), children);
        parent.updateProgress(result.percent());
        return result;
    }

    // ------------------------------------------------------------- lookups

    public WorkItem requireVisible(AuthPrincipal principal, UUID workItemId) {
        WorkItem item = workItems.findByIdInOrganization(workItemId, principal.organizationId())
                .orElseThrow(() -> ApiException.notFound("Work item", workItemId));

        // Invisible is reported as not-found, never forbidden: a 403 would confirm the
        // item exists and leak the ID space.
        if (!access.canRead(principal, item)) {
            throw ApiException.notFound("Work item", workItemId);
        }
        return item;
    }

    public AnalysisView requireVisibleForAnalysis(AuthPrincipal principal, UUID workItemId) {
        return AnalysisView.from(requireVisible(principal, workItemId));
    }

    @Transactional
    public AnalysisView requireVisibleForAnalysisLocked(AuthPrincipal principal, UUID workItemId) {
        WorkItem item = workItems.findByIdInOrganizationForUpdate(workItemId, principal.organizationId())
                .orElseThrow(() -> ApiException.notFound("Work item", workItemId));
        if (!access.canRead(principal, item)) {
            throw ApiException.notFound("Work item", workItemId);
        }
        return AnalysisView.from(item);
    }

    /**
     * Status-only projection for cross-module callers (e.g. {@code analysis}'s
     * reanalyze-and-reroute flow) that must not hold a reference to the {@link WorkItem}
     * entity itself (ADR-028).
     */
    public String requireVisibleStatus(AuthPrincipal principal, UUID workItemId) {
        return requireVisible(principal, workItemId).getStatus();
    }

    public WorkItem requireEditable(AuthPrincipal principal, UUID workItemId) {
        WorkItem item = requireVisible(principal, workItemId);
        if (!access.canEdit(principal, item)) {
            throw ApiException.forbidden("You cannot modify this work item.");
        }
        return item;
    }

    public ProcessRootView requireEditableForProcess(AuthPrincipal principal, UUID workItemId) {
        return ProcessRootView.from(requireEditable(principal, workItemId));
    }

    @Transactional
    public void setProcessOwner(AuthPrincipal principal, UUID workItemId, UUID processOwnerId) {
        WorkItem item = requireEditable(principal, workItemId);
        item.setProcessOwner(processOwnerId);
    }

    @Transactional
    public WorkItem overrideAnalysisAndRouting(
            AuthPrincipal principal, UUID workItemId,
            UUID departmentId, UUID teamId, Priority priority, RiskLevel riskLevel) {
        WorkItem item = requireEditable(principal, workItemId);
        if (departmentId != null || teamId != null) {
            item.redirect(departmentId, teamId, null);
        }
        if (priority != null) {
            item.updatePlanning(priority, item.getDueDate(), item.getEstimatedHours(),
                    item.getStoryPoints(), item.getComplexity());
        }
        if (riskLevel != null) {
            item.recordAnalysis(item.getAiConfidence(), riskLevel, null);
        }
        return item;
    }

    /**
     * Mirrors the AI's clarification questions onto the item so the detail response can
     * show them without the workitem module reading analysis's tables.
     *
     * <p>Called from {@code analysis} (which depends on {@code workitem::application}),
     * never from a controller. There is no principal here because this is not a user
     * action; it is a side effect of an AI analysis that already happened under its own
     * authorization.
     */
    @Transactional
    public void recordPendingClarification(UUID workItemId, List<Map<String, Object>> questions) {
        workItems.findById(workItemId).ifPresent(item -> item.recordPendingClarification(questions));
    }

    /** Called once the item leaves WAITING_INFORMATION, or a fresh analysis needs nothing more. */
    @Transactional
    public void clearPendingClarification(UUID workItemId) {
        workItems.findById(workItemId).ifPresent(WorkItem::clearPendingClarification);
    }

    public List<WorkItem> children(AuthPrincipal principal, UUID workItemId) {
        requireVisible(principal, workItemId);
        return workItems.findChildren(workItemId);
    }

    /**
     * Possible duplicates of a ticket. Advisory only - shown to the person filing so they
     * can decide, never used to block or auto-merge.
     */
    public List<WorkItem> possibleDuplicates(AuthPrincipal principal, UUID workItemId) {
        WorkItem item = requireVisible(principal, workItemId);
        return workItems.findSimilar(principal.organizationId(), item.getId(),
                        item.getWorkItemType().name(), item.getSummary(), 0.45, 5).stream()
                .filter(candidate -> access.canRead(principal, candidate))
                .toList();
    }

    private void requireVersion(WorkItem item, Long expectedVersion) {
        if (expectedVersion != null && item.getVersion() != expectedVersion) {
            throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION,
                    "This item was changed by someone else (version %d, you sent %d). Reload and try again."
                            .formatted(item.getVersion(), expectedVersion));
        }
    }

    // -------------------------------------------------------------- commands

    public record CreateCommand(WorkItemType type,
                                OriginType originType,
                                String summary,
                                String description,
                                String acceptanceCriteria,
                                UUID projectId,
                                UUID sprintId,
                                UUID parentId,
                                UUID departmentId,
                                UUID teamId,
                                Priority priority,
                                Complexity complexity,
                                BigDecimal estimatedHours,
                                Short storyPoints,
                                LocalDate dueDate) {
    }

    public record UpdateCommand(String summary,
                                String description,
                                String acceptanceCriteria,
                                Priority priority,
                                Complexity complexity,
                                BigDecimal estimatedHours,
                                Short storyPoints,
                                LocalDate dueDate,
                                Sensitivity sensitivity) {
    }

    public record Sensitivity(boolean security, boolean production, boolean budget, boolean compliance) {
    }

    public record AnalysisView(
            UUID id,
            UUID organizationId,
            UUID departmentId,
            UUID projectId,
            UUID processOwnerId,
            UUID parentId,
            String referenceKey,
            String summary,
            String description,
            String acceptanceCriteria,
            OriginType originType,
            WorkItemType workItemType,
            Priority priority,
            java.time.Instant updatedAt,
            LocalDate dueDate) {

        static AnalysisView from(WorkItem item) {
            return new AnalysisView(
                    item.getId(),
                    item.getOrganizationId(),
                    item.getDepartmentId(),
                    item.getProjectId(),
                    item.getProcessOwnerId(),
                    item.getParentId(),
                    item.getReferenceKey(),
                    item.getSummary(),
                    item.getDescription(),
                    item.getAcceptanceCriteria(),
                    item.getOriginType(),
                    item.getWorkItemType(),
                    item.getPriority(),
                    item.getUpdatedAt(),
                    item.getDueDate());
        }
    }

    public record CreatedItemView(
            UUID id,
            String referenceKey,
            String summary) {

        static CreatedItemView from(WorkItem item) {
            return new CreatedItemView(item.getId(), item.getReferenceKey(), item.getSummary());
        }
    }

    public record ProcessRootView(
            UUID id,
            UUID organizationId,
            UUID projectId,
            UUID processOwnerId,
            String referenceKey,
            LocalDate dueDate) {

        static ProcessRootView from(WorkItem item) {
            return new ProcessRootView(
                    item.getId(),
                    item.getOrganizationId(),
                    item.getProjectId(),
                    item.getProcessOwnerId(),
                    item.getReferenceKey(),
                    item.getDueDate());
        }
    }

    /** Exposed for the transition service, which needs the closure default on conversion. */
    ClosureType defaultClosureFor(WorkFamily family) {
        return family.defaultClosureType();
    }
}
