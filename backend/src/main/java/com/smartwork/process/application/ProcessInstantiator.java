package com.smartwork.process.application;

import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.process.domain.ProcessDefinition;
import com.smartwork.process.domain.ProcessOwnerAssignment;
import com.smartwork.process.domain.ProcessTemplate;
import com.smartwork.process.domain.ProcessTemplateTask;
import com.smartwork.process.infrastructure.ProcessDefinitionRepository;
import com.smartwork.process.infrastructure.ProcessOwnerRepository;
import com.smartwork.workitem.application.WorkItemLinkService;
import com.smartwork.workitem.application.WorkItemService;
import com.smartwork.workitem.domain.LinkType;
import com.smartwork.workitem.domain.OriginType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a template into a real tree of work items, with dependency links.
 *
 * <p>The interesting part is the second pass. Tasks are created first and linked second,
 * because a step can depend on a sibling that has not been created yet — a single pass
 * would either fail on forward references or force template authors to order their steps
 * so dependencies always precede dependants, which is a constraint on the author to work
 * around a limitation of the code.
 */
@Service
public class ProcessInstantiator {

    private static final Logger log = LoggerFactory.getLogger(ProcessInstantiator.class);

    private final WorkItemService workItemService;
    private final WorkItemLinkService linkService;
    private final ProcessDefinitionRepository processDefinitions;
    private final ProcessOwnerRepository processOwners;

    public ProcessInstantiator(
            WorkItemService workItemService,
            WorkItemLinkService linkService,
            ProcessDefinitionRepository processDefinitions,
            ProcessOwnerRepository processOwners) {
        this.workItemService = workItemService;
        this.linkService = linkService;
        this.processDefinitions = processDefinitions;
        this.processOwners = processOwners;
    }

    public record Result(
            UUID processDefinitionId,
            List<CreatedItem> createdItems,
            int dependencyLinksCreated,
            UUID processOwnerId) {
    }

    public record CreatedItem(UUID id, String referenceKey, String summary) {
    }

    /**
     * Instantiates {@code template} under {@code rootWorkItem}.
     *
     * @param dueDate optional target date; individual steps inherit it so the deadline
     *                score has something to work with rather than scoring every generated
     *                task as undated.
     */
    @Transactional
    public Result instantiate(
            AuthPrincipal principal,
            WorkItemService.ProcessRootView rootWorkItem,
            ProcessTemplate template,
            LocalDate dueDate) {

        if (template.getTasks().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Template '" + template.getCode() + "' has no tasks and would produce an empty plan.");
        }
        processDefinitions.findByRootWorkItemIdAndStatus(rootWorkItem.id(), ProcessDefinition.Status.RUNNING)
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.DUPLICATE_RESOURCE,
                            "A process is already running for " + rootWorkItem.referenceKey()
                                    + ". Cancel it before instantiating another.");
                });

        ProcessDefinition definition = processDefinitions.save(new ProcessDefinition(
                rootWorkItem.organizationId(), rootWorkItem.id(), template.getId()));

        // Pass 1: create every task, remembering its template sequence.
        Map<Integer, CreatedItem> bySequence = new HashMap<>();
        List<CreatedItem> created = new ArrayList<>();
        for (ProcessTemplateTask task : template.getTasks()) {
            var item = workItemService.createForProcess(principal, new WorkItemService.CreateCommand(
                    task.getWorkItemType(),
                    OriginType.PROCESS_TEMPLATE,
                    task.getTitle(),
                    task.getDescription(),
                    null,
                    rootWorkItem.projectId(),
                    null,
                    rootWorkItem.id(),
                    task.getDepartmentId(),
                    task.getTeamId(),
                    task.getPriority() != null ? task.getPriority() : template.getDefaultPriority(),
                    null,
                    task.getEstimatedHours(),
                    null,
                    dueDate));

            CreatedItem createdItem = new CreatedItem(
                    item.id(), item.referenceKey(), item.summary());
            bySequence.put((int) task.getSequence(), createdItem);
            created.add(createdItem);
        }

        // Pass 2: resolve depends_on_sequences into real DEPENDS_ON links.
        int links = 0;
        for (ProcessTemplateTask task : template.getTasks()) {
            CreatedItem dependant = bySequence.get((int) task.getSequence());
            for (Integer prerequisiteSequence : task.dependsOn()) {
                CreatedItem prerequisite = bySequence.get(prerequisiteSequence);
                if (prerequisite == null) {
                    log.warn("Template {} task {} depends on unknown sequence {}; link skipped",
                            template.getCode(), task.getSequence(), prerequisiteSequence);
                    continue;
                }
                linkService.create(principal, dependant.id(), LinkType.DEPENDS_ON,
                        prerequisite.id(), "Process template " + template.getCode());
                links++;
            }
        }

        UUID ownerId = resolveProcessOwner(rootWorkItem, template);
        if (ownerId != null) {
            // SUBTREE scope: one row covers the root and every generated child, which is
            // what makes the Process Owner's queue work without a row per task.
            processOwners.save(ProcessOwnerAssignment.forWorkItem(
                    rootWorkItem.organizationId(), rootWorkItem.id(), ownerId,
                    ProcessOwnerAssignment.Scope.SUBTREE));
            workItemService.setProcessOwner(principal, rootWorkItem.id(), ownerId);
        }

        log.info("Instantiated template {} under {}: {} tasks, {} dependency links",
                template.getCode(), rootWorkItem.referenceKey(), created.size(), links);

        return new Result(definition.getId(), created, links, ownerId);
    }

    /**
     * An explicit owner on the item wins over the template default — someone who has already
     * been named should not be silently replaced by instantiating a template.
     */
    private UUID resolveProcessOwner(WorkItemService.ProcessRootView rootWorkItem, ProcessTemplate template) {
        return rootWorkItem.processOwnerId() != null
                ? rootWorkItem.processOwnerId()
                : template.getDefaultProcessOwnerId();
    }
}
