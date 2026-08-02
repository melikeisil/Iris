package com.smartwork.policy.api;

import com.smartwork.analysis.application.AnalysisService;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.policy.application.WorkItemRoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Answering AI clarification questions and resuming the pipeline in one request.
 *
 * <p>Lives in {@code policy}, not {@code analysis}, even though it is conceptually about
 * analysis: completing "persist the answers, move back to AI_ANALYSIS, rerun analysis,
 * rerun routing" needs both {@code analysis::application} and this module's own routing
 * pipeline, and {@code analysis}'s module configuration does not permit it to depend on
 * {@code policy}. Only this direction is legal, so the composition can only be triggered
 * from here — see {@link WorkItemRoutingService#resumeAfterAdditionalInformation}.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Clarification", description = "Answering AI clarification questions and resuming analysis and routing")
public class ClarificationController {

    private final WorkItemRoutingService routing;

    public ClarificationController(WorkItemRoutingService routing) {
        this.routing = routing;
    }

    @PostMapping("/work-items/{id}/additional-information")
    @Operation(summary = "Answer AI clarification questions",
            description = """
                    Persists the answers on the item's latest analysis, applies the
                    INFO_PROVIDED workflow transition (WAITING_INFORMATION -> AI_ANALYSIS) -
                    which is what authorizes the caller: the REQUESTER or ASSIGNEE
                    relationship, or a TEAM_LEAD/MANAGER role, per the workflow profile -
                    then immediately reanalyzes with the answers included and, provided the
                    fresh analysis is usable and does not ask for more information again,
                    routes the item. `routed` reports whether that last step ran; it can be
                    false if the AI asked another question or was unavailable, in which case
                    the answers are still saved and the item is still back in AI_ANALYSIS.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Answers recorded and the pipeline resumed"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED - no analysis, or it did "
                    + "not request more information"),
            @ApiResponse(responseCode = "403", description = "TRANSITION_NOT_PERMITTED"),
            @ApiResponse(responseCode = "422", description = "INVALID_TRANSITION - item is not currently "
                    + "WAITING_INFORMATION")
    })
    public ClarificationDtos.AdditionalInformationResponse submitAdditionalInformation(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ClarificationDtos.SubmitAdditionalInformationRequest request) {

        WorkItemRoutingService.AdditionalInformationOutcome outcome = routing.resumeAfterAdditionalInformation(
                principal, id, request.answers().stream()
                        .map(answer -> new AnalysisService.ClarificationAnswerInput(
                                answer.questionId(), answer.field(), answer.answer()))
                        .toList());

        return ClarificationDtos.AdditionalInformationResponse.from(outcome);
    }
}
