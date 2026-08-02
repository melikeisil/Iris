package com.smartwork.workitem.application;

import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.workitem.domain.LinkType;
import com.smartwork.workitem.domain.LinkTypeRules;
import com.smartwork.workitem.domain.WorkItem;
import com.smartwork.workitem.domain.WorkItemLink;
import com.smartwork.workitem.infrastructure.LinkCycleDetector;
import com.smartwork.workitem.infrastructure.WorkItemLinkRepository;
import com.smartwork.workitem.infrastructure.WorkItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Typed relationships between work items.
 *
 * <p>Links are stored once in the canonical direction and rendered in both. A request for
 * {@code A CHILD_OF B} is persisted as {@code B PARENT_OF A}, which is what makes the
 * uniqueness constraint meaningful.
 */
@Service
@Transactional(readOnly = true)
public class WorkItemLinkService {

    private static final Logger log = LoggerFactory.getLogger(WorkItemLinkService.class);
    private static final int MAX_HIERARCHY_DEPTH = 5;

    private final WorkItemLinkRepository links;
    private final WorkItemRepository workItems;
    private final WorkItemService workItemService;
    private final WorkItemAccessEvaluator access;
    private final LinkCycleDetector cycleDetector;

    public WorkItemLinkService(WorkItemLinkRepository links, WorkItemRepository workItems,
                               WorkItemService workItemService, WorkItemAccessEvaluator access,
                               LinkCycleDetector cycleDetector) {
        this.links = links;
        this.workItems = workItems;
        this.workItemService = workItemService;
        this.access = access;
        this.cycleDetector = cycleDetector;
    }

    @Transactional
    public WorkItemLink create(AuthPrincipal principal, UUID sourceId, LinkType requestedType,
                               UUID targetId, String note) {
        WorkItem source = workItemService.requireVisible(principal, sourceId);
        WorkItem target = workItemService.requireVisible(principal, targetId);

        if (!access.canEdit(principal, source) && !access.canEdit(principal, target)) {
            throw ApiException.forbidden("You cannot link these work items.");
        }
        if (sourceId.equals(targetId)) {
            throw new ApiException(ErrorCode.INVALID_LINK_TYPE, "A work item cannot link to itself.");
        }

        LinkTypeRules.validate(requestedType, source.getWorkItemType(), source.getWorkFamily(),
                target.getWorkItemType(), target.getWorkFamily());

        // Canonicalize before persisting: CHILD_OF becomes PARENT_OF with the ends
        // swapped, BLOCKS becomes DEPENDS_ON, RESOLVES becomes RESOLVED_BY.
        LinkType canonicalType = requestedType.canonicalType();
        UUID canonicalSource = requestedType.isCanonical() ? sourceId : targetId;
        UUID canonicalTarget = requestedType.isCanonical() ? targetId : sourceId;

        links.findBySourceIdAndLinkTypeAndTargetId(canonicalSource, canonicalType, canonicalTarget)
                .ifPresent(existing -> {
                    throw ApiException.duplicate("That link already exists.");
                });

        if (cycleDetector.wouldCreateCycle(canonicalSource, canonicalType, canonicalTarget)) {
            throw new ApiException(ErrorCode.LINK_CYCLE_DETECTED,
                    "That link would create a cycle: the target already depends on the source, "
                            + "directly or through other items.");
        }

        WorkItemLink link = links.save(new WorkItemLink(principal.organizationId(),
                canonicalSource, canonicalType, canonicalTarget, note, principal.userId()));

        // PARENT_OF is doubly represented: the link table for uniform traversal, and
        // work_items.parent_id for cheap queries. They are kept in step here.
        if (canonicalType == LinkType.PARENT_OF) {
            applyHierarchy(canonicalSource, canonicalTarget);
        }

        log.info("Linked {} {} {} by {}", canonicalSource, canonicalType, canonicalTarget,
                principal.userId());
        return link;
    }

    private void applyHierarchy(UUID parentId, UUID childId) {
        if (cycleDetector.ancestorDepth(parentId) >= MAX_HIERARCHY_DEPTH) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Hierarchy is already %d levels deep. Deeper trees stop being navigable; "
                            .formatted(MAX_HIERARCHY_DEPTH)
                            + "consider linking with RELATES_TO instead.");
        }
        workItems.findById(childId).ifPresent(child ->
                child.placeIn(child.getProjectId(), child.getSprintId(), parentId));
        workItemService.recalculateProgress(parentId);
    }

    @Transactional
    public void delete(AuthPrincipal principal, UUID sourceId, UUID linkId) {
        WorkItem source = workItemService.requireVisible(principal, sourceId);

        WorkItemLink link = links.findByIdInOrganization(linkId, principal.organizationId())
                .orElseThrow(() -> ApiException.notFound("Link", linkId));

        if (!link.getSourceId().equals(source.getId()) && !link.getTargetId().equals(source.getId())) {
            throw ApiException.notFound("Link", linkId);
        }
        if (!access.canEdit(principal, source)) {
            throw ApiException.forbidden("You cannot remove links from this work item.");
        }

        if (link.getLinkType() == LinkType.PARENT_OF) {
            workItems.findById(link.getTargetId()).ifPresent(child ->
                    child.placeIn(child.getProjectId(), child.getSprintId(), null));
        }
        links.delete(link);
    }

    /**
     * Both directions, rendered from canonical storage.
     *
     * <p>An incoming {@code DEPENDS_ON} is presented as {@code BLOCKS}, so a user looking
     * at the blocking item sees that it blocks something - rather than having to reason
     * about which end the row was stored from.
     */
    public LinkGraph graph(AuthPrincipal principal, UUID workItemId) {
        WorkItem item = workItemService.requireVisible(principal, workItemId);

        List<LinkView> outgoing = new ArrayList<>();
        for (WorkItemLink link : links.findOutgoing(item.getId())) {
            related(principal, link.getTargetId()).ifPresent(other ->
                    outgoing.add(toView(link.getId(), link.getLinkType(), other, link.getNote())));
        }

        List<LinkView> incoming = new ArrayList<>();
        for (WorkItemLink link : links.findIncoming(item.getId())) {
            LinkType presented = link.getLinkType().inverse().orElse(link.getLinkType());
            related(principal, link.getSourceId()).ifPresent(other ->
                    incoming.add(toView(link.getId(), presented, other, link.getNote())));
        }

        return new LinkGraph(outgoing, incoming);
    }

    /** A link to an item the caller cannot see is omitted rather than shown as a UUID. */
    private Optional<WorkItem> related(AuthPrincipal principal, UUID relatedId) {
        return workItems.findById(relatedId).filter(other -> access.canRead(principal, other));
    }

    private LinkView toView(UUID linkId, LinkType type, WorkItem other, String note) {
        return new LinkView(linkId, type, other.getId(), other.getReferenceKey(),
                other.getWorkItemType().name(), other.getSummary(), other.getStatus(),
                type.isBlockingDependency(), note);
    }

    public record LinkView(UUID linkId, LinkType linkType, UUID workItemId, String referenceKey,
                           String workItemType, String summary, String status,
                           boolean blocking, String note) {
    }

    public record LinkGraph(List<LinkView> outgoing, List<LinkView> incoming) {
    }
}
