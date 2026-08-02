package com.smartwork.workitem.api;

import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.workitem.application.WorkItemLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-items/{id}/links")
@Tag(name = "work-items")
public class WorkItemLinkController {

    private final WorkItemLinkService links;

    public WorkItemLinkController(WorkItemLinkService links) {
        this.links = links;
    }

    @GetMapping
    @Operation(summary = "Typed relationships, rendered in both directions",
            description = "Links are stored once canonically. An incoming DEPENDS_ON is presented "
                    + "as BLOCKS, so each end reads naturally. Links to items the caller cannot "
                    + "see are omitted rather than shown as opaque IDs.")
    public WorkItemLinkService.LinkGraph list(@PathVariable UUID id,
                                              @AuthenticationPrincipal AuthPrincipal principal) {
        return links.graph(principal, id);
    }

    @PostMapping
    @Operation(summary = "Create a typed link",
            description = "Cross-family links are expected: REQUEST RESOLVED_BY BUG is the "
                    + "ticket-to-development conversion.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "LINK_CYCLE_DETECTED or INVALID_LINK_TYPE"),
            @ApiResponse(responseCode = "409", description = "DUPLICATE_RESOURCE")
    })
    public ResponseEntity<WorkItemLinkService.LinkGraph> create(
            @PathVariable UUID id,
            @Valid @RequestBody WorkItemDtos.CreateLinkRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {

        links.create(principal, id, request.linkType(), request.targetId(), request.note());
        return ResponseEntity.status(201).body(links.graph(principal, id));
    }

    @DeleteMapping("/{linkId}")
    @Operation(summary = "Remove a link")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @PathVariable UUID linkId,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
        links.delete(principal, id, linkId);
        return ResponseEntity.noContent().build();
    }
}
