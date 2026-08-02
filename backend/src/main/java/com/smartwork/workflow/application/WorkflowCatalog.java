package com.smartwork.workflow.application;

import com.smartwork.workflow.domain.StateCategory;

import java.util.List;
import java.util.UUID;

/**
 * Read contract over the workflow profiles, for modules that need to reason about states
 * without depending on the engine's internals.
 */
public interface WorkflowCatalog {

    /** @throws com.smartwork.common.error.ApiException NOT_FOUND if the code is unknown. */
    DefinitionView requireByCode(UUID organizationId, String code);

    DefinitionView requireById(UUID workflowDefinitionId);

    StateView requireState(UUID workflowDefinitionId, String stateCode);

    List<TransitionView> transitionsFrom(UUID workflowDefinitionId, String stateCode);

    record DefinitionView(UUID id, String code, String name, String appliesToFamily,
                          String initialState) {
    }

    record StateView(String code, String name, StateCategory category,
                     boolean initial, boolean terminal, boolean terminalSuccess,
                     boolean activeWork, boolean blocked, boolean waitingExternal) {
    }

    record TransitionView(String code, String label, String fromState, String toState,
                          boolean requiresReason, boolean automatic, List<String> guards) {
    }
}
