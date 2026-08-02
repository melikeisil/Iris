package com.smartwork.workitem.api;

import com.smartwork.common.api.PageResponse;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.workitem.application.WorkItemLinkService;
import com.smartwork.workitem.application.WorkItemService;
import com.smartwork.workitem.application.WorkItemJiraService;
import com.smartwork.workitem.application.WorkItemTransitionService;
import com.smartwork.workitem.domain.OriginType;
import com.smartwork.workitem.domain.Priority;
import com.smartwork.workitem.domain.ProgressCalculator;
import com.smartwork.workitem.domain.WorkFamily;
import com.smartwork.workitem.domain.WorkItem;
import com.smartwork.workitem.domain.WorkItemType;
import com.smartwork.workitem.infrastructure.WorkItemRepository;
import com.smartwork.workitem.infrastructure.WorkItemSearchRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-items")
@Tag(name = "work-items", description = "Tickets, incidents and development work - one API for all of them")
public class WorkItemController {

    private final WorkItemService workItems;
    private final WorkItemJiraService jira;
    private final WorkItemTransitionService transitions;
    private final WorkItemLinkService linkService;
    private final WorkItemSearchRepository search;
    private final WorkItemRepository repository;
    private final WorkItemMapper mapper;

    public WorkItemController(WorkItemService workItems, WorkItemJiraService jira,
                              WorkItemTransitionService transitions,
                              WorkItemLinkService linkService, WorkItemSearchRepository search,
                              WorkItemRepository repository, WorkItemMapper mapper) {
        this.workItems = workItems;
        this.jira = jira;
        this.transitions = transitions;
        this.linkService = linkService;
        this.search = search;
        this.repository = repository;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- create

    @PostMapping
    @Operation(summary = "Create a work item of any type",
            description = "Work family, workflow profile, initial status and reference key are all "
                    + "derived server-side. A task does not need a ticket, a project or a parent.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED - see `errors` for the offending fields")
    })
    public ResponseEntity<WorkItemDtos.WorkItemDetailResponse> create(
            @Valid @RequestBody WorkItemDtos.CreateWorkItemRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {

        WorkItem created = workItems.create(principal, new WorkItemService.CreateCommand(
                request.workItemType(), request.originOrDefault(), request.summary(),
                request.description(), request.acceptanceCriteria(), request.projectId(),
                request.sprintId(), request.parentId(), request.departmentId(), request.teamId(),
                request.priorityOrDefault(), request.complexity(), request.estimatedHours(),
                request.storyPoints(), request.dueDate()));

        return ResponseEntity.status(201).body(detail(created, principal));
    }

    // ----------------------------------------------------------------- reads

    @GetMapping
    @Operation(summary = "Search work items",
            description = "The caller's visibility is applied in SQL, so the pagination total "
                    + "never reveals items they cannot see.")
    public PageResponse<WorkItemDtos.WorkItemSummaryResponse> list(
            @RequestParam(required = false) List<WorkItemType> type,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<Priority> priority,
            @RequestParam(required = false) List<WorkFamily> family,
            @RequestParam(required = false) List<OriginType> origin,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID sprintId,
            @RequestParam(required = false) UUID parentId,
            @RequestParam(required = false) Boolean unassigned,
            @RequestParam(required = false) Boolean blocked,
            @RequestParam(required = false) LocalDate dueBefore,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @AuthenticationPrincipal AuthPrincipal principal) {

        var criteria = new WorkItemSearchRepository.Criteria(type, status, priority, family, origin,
                teamId, departmentId, assigneeId, projectId, sprintId, parentId,
                unassigned, blocked, dueBefore, q);

        Page<WorkItem> results = search.search(principal, criteria,
                PageRequest.of(page, size, parseSort(sort)));

        return PageResponse.from(results, mapper::toSummary);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Work item detail, with this caller's permissions and legal transitions")
    public WorkItemDtos.WorkItemDetailResponse get(@PathVariable UUID id,
                                                   @AuthenticationPrincipal AuthPrincipal principal) {
        return detail(workItems.requireVisible(principal, id), principal);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update content and planning fields")
    @ApiResponses(@ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION"))
    public WorkItemDtos.WorkItemDetailResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody WorkItemDtos.UpdateWorkItemRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {

        WorkItemService.Sensitivity sensitivity = request.sensitivity() == null ? null
                : new WorkItemService.Sensitivity(request.sensitivity().security(),
                        request.sensitivity().production(), request.sensitivity().budget(),
                        request.sensitivity().compliance());

        WorkItem updated = workItems.update(principal, id, new WorkItemService.UpdateCommand(
                request.summary(), request.description(), request.acceptanceCriteria(),
                request.priority(), request.complexity(), request.estimatedHours(),
                request.storyPoints(), request.dueDate(), sensitivity), request.version());

        return detail(updated, principal);
    }

    @PostMapping("/{id}/override")
    @Operation(summary = "Manually override department, team, priority, risk level when AI recommendation is rejected")
    public WorkItemDtos.WorkItemDetailResponse override(
            @PathVariable UUID id,
            @RequestBody WorkItemDtos.ManualOverrideRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        WorkItem updated = workItems.overrideAnalysisAndRouting(
                principal, id, request.departmentId(), request.teamId(),
                request.priority(), request.riskLevel());
        return detail(updated, principal);
    }

    @GetMapping("/my")
    @Operation(summary = "Work assigned to me, ordered the way I should work through it")
    public List<WorkItemDtos.WorkItemSummaryResponse> myWork(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return repository.findAssignedTo(principal.userId(), principal.organizationId()).stream()
                .map(mapper::toSummary)
                .toList();
    }

    @GetMapping("/created-by-me")
    @Operation(summary = "Tickets and items I raised - where I am the REQUESTER")
    public PageResponse<WorkItemDtos.WorkItemSummaryResponse> createdByMe(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal AuthPrincipal principal) {

        return PageResponse.from(
                repository.findReportedBy(principal.userId(), principal.organizationId(),
                        PageRequest.of(page, size)),
                mapper::toSummary);
    }

    @GetMapping("/watching")
    @Operation(summary = "Items I watch")
    public PageResponse<WorkItemDtos.WorkItemSummaryResponse> watching(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal AuthPrincipal principal) {

        return PageResponse.from(
                repository.findWatchedBy(principal.userId(), principal.organizationId(),
                        PageRequest.of(page, size)),
                mapper::toSummary);
    }

    @GetMapping("/{id}/children")
    @Operation(summary = "Direct children and the parent's rolled-up progress")
    public WorkItemDtos.ChildrenResponse children(@PathVariable UUID id,
                                                  @AuthenticationPrincipal AuthPrincipal principal) {
        List<WorkItem> children = workItems.children(principal, id);
        ProgressCalculator.Result progress = workItems.recalculateProgress(id);

        return new WorkItemDtos.ChildrenResponse(
                children.stream().map(mapper::toSummary).toList(),
                progress.percent(),
                progress.strategy().name(),
                progress.hasBlockedChildren());
    }

    @GetMapping("/{id}/possible-duplicates")
    @Operation(summary = "Possible duplicates by summary similarity",
            description = "Advisory. Shown to the person filing so they can decide; never used "
                    + "to block or merge automatically.")
    public List<WorkItemDtos.WorkItemSummaryResponse> possibleDuplicates(
            @PathVariable UUID id, @AuthenticationPrincipal AuthPrincipal principal) {
        return workItems.possibleDuplicates(principal, id).stream().map(mapper::toSummary).toList();
    }

    // ----------------------------------------------------------- transitions

    @PostMapping("/{id}/transition")
    @Operation(summary = "Execute a named workflow transition",
            description = "Takes a transition name, never a status. Returns 422 INVALID_TRANSITION "
                    + "with the legal alternatives, or TRANSITION_GUARD_FAILED with each failing guard.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Applied"),
            @ApiResponse(responseCode = "403", description = "TRANSITION_NOT_PERMITTED"),
            @ApiResponse(responseCode = "422", description = "INVALID_TRANSITION or TRANSITION_GUARD_FAILED")
    })
    public WorkItemDtos.TransitionResultResponse transition(
            @PathVariable UUID id,
            @Valid @RequestBody WorkItemDtos.TransitionRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {

        var result = transitions.transition(principal, id, new WorkItemTransitionService.Command(
                request.transition(), request.reason(), request.assigneeId(),
                request.reviewerId(), request.version()));

        return new WorkItemDtos.TransitionResultResponse(
                result.workItem().getId(), result.workItem().getReferenceKey(),
                result.transition().fromState(), result.transition().toState(),
                result.transition().targetState().category().name(),
                result.transition().transitionCode(), result.workItem().getVersion());
    }

    @PostMapping("/{id}/block")
    @Operation(summary = "Block with a required reason")
    public WorkItemDtos.TransitionResultResponse block(
            @PathVariable UUID id,
            @Valid @RequestBody WorkItemDtos.BlockRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {

        var result = transitions.block(principal, id, request.reason(), request.version());

        // A named blocker is recorded as a real dependency, so it shows up in the
        // blocked-work investigation instead of living only in free text.
        if (request.blockedByWorkItemId() != null) {
            linkService.create(principal, id, com.smartwork.workitem.domain.LinkType.DEPENDS_ON,
                    request.blockedByWorkItemId(), "Blocking reason: " + request.reason());
        }

        return new WorkItemDtos.TransitionResultResponse(
                result.workItem().getId(), result.workItem().getReferenceKey(),
                result.transition().fromState(), result.transition().toState(),
                result.transition().targetState().category().name(),
                result.transition().transitionCode(), result.workItem().getVersion());
    }

    @PutMapping("/{id}/remaining-hours")
    @Operation(summary = "Update remaining effort")
    public ResponseEntity<Void> remainingHours(
            @PathVariable UUID id,
            @Valid @RequestBody WorkItemDtos.RemainingHoursRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        workItems.updateRemainingHours(principal, id, request.remainingHours());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/jira-create")
    @Operation(summary = "Create a Jira issue for this work item",
            description = "Idempotent: if the work item is already linked to Jira, the existing "
                    + "issue key and URL are returned and no second issue is created.")
    public WorkItemDtos.JiraLinkResponse createInJira(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        var link = jira.create(principal, id);
        return new WorkItemDtos.JiraLinkResponse(link.issueKey(), link.issueUrl(), link.createdAt());
    }

    // -------------------------------------------------------------- comments

    @GetMapping("/{id}/comments")
    @Operation(summary = "Comments, filtered by whether the caller may see internal ones")
    public List<WorkItemDtos.CommentResponse> comments(@PathVariable UUID id,
                                                       @AuthenticationPrincipal AuthPrincipal principal) {
        return workItems.comments(principal, id).stream().map(mapper::toComment).toList();
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "Add a comment")
    public ResponseEntity<WorkItemDtos.CommentResponse> comment(
            @PathVariable UUID id,
            @Valid @RequestBody WorkItemDtos.CommentRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(201).body(mapper.toComment(
                workItems.comment(principal, id, request.body(), request.internal())));
    }

    // -------------------------------------------------------------- watching

    @PostMapping("/{id}/watch")
    @Operation(summary = "Watch this item")
    public ResponseEntity<Void> watch(@PathVariable UUID id,
                                      @AuthenticationPrincipal AuthPrincipal principal) {
        workItems.watch(principal, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/watch")
    @Operation(summary = "Stop watching this item")
    public ResponseEntity<Void> unwatch(@PathVariable UUID id,
                                        @AuthenticationPrincipal AuthPrincipal principal) {
        workItems.unwatch(principal, id);
        return ResponseEntity.noContent().build();
    }

    // --------------------------------------------------------------- helpers

    private WorkItemDtos.WorkItemDetailResponse detail(WorkItem item, AuthPrincipal principal) {
        Optional<WorkItem> parent = item.getParentId() == null
                ? Optional.empty()
                : repository.findById(item.getParentId());

        List<WorkItem> children = repository.findChildren(item.getId());
        long done = children.stream().filter(mapper::isTerminalSuccess).count();
        long blocked = children.stream().filter(mapper::isBlockedState).count();

        String strategy = children.isEmpty() ? null
                : ProgressCalculator.calculate(item.getWorkItemType(),
                        children.stream()
                                .map(child -> new ProgressCalculator.Child(
                                        mapper.isTerminalSuccess(child), false,
                                        mapper.isBlockedState(child),
                                        child.getEstimatedHours(), child.getStoryPoints()))
                                .toList())
                        .strategy().name();

        var childrenSummary = new WorkItemDtos.ChildrenSummary(children.size(), done, blocked, strategy);

        return mapper.toDetail(item, principal, parent, childrenSummary,
                transitions.availableTransitions(principal, item));
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        boolean descending = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
        return Sort.by(descending ? Sort.Direction.DESC : Sort.Direction.ASC, property);
    }
}
