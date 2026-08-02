package com.smartwork.workflow.application;

import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import com.smartwork.workflow.domain.GuardContext;
import com.smartwork.workflow.domain.GuardResult;
import com.smartwork.workflow.domain.TransitionGuard;
import com.smartwork.workflow.domain.WorkflowDefinition;
import com.smartwork.workflow.domain.WorkflowState;
import com.smartwork.workflow.domain.WorkflowTransition;
import com.smartwork.workflow.domain.guards.StandardGuards;
import com.smartwork.workflow.infrastructure.WorkflowDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Validates and resolves workflow transitions.
 *
 * <p>The engine never mutates a work item - it answers "is this move legal for this
 * actor, and where does it land". The caller applies the result. That keeps the state
 * machine free of persistence concerns and makes it testable in isolation.
 *
 * <p>Profiles are cached in memory: there are six of them, they change only when an admin
 * edits configuration, and every transition and every board render reads them.
 */
@Service
public class WorkflowEngine implements WorkflowCatalog {

    private final WorkflowDefinitionRepository definitions;
    private final Map<String, TransitionGuard> guards;

    private volatile Map<UUID, WorkflowDefinition> cache;

    public WorkflowEngine(WorkflowDefinitionRepository definitions) {
        this.definitions = definitions;
        this.guards = StandardGuards.all().stream()
                .collect(Collectors.toMap(TransitionGuard::code, Function.identity()));
    }

    // ---------------------------------------------------------------- catalog

    @Override
    public DefinitionView requireByCode(UUID organizationId, String code) {
        WorkflowDefinition definition = definitions
                .findByCodeForOrganization(code, organizationId).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Workflow definition", code));
        return toView(definition);
    }

    @Override
    public DefinitionView requireById(UUID workflowDefinitionId) {
        return toView(load(workflowDefinitionId));
    }

    @Override
    public StateView requireState(UUID workflowDefinitionId, String stateCode) {
        return load(workflowDefinitionId).state(stateCode)
                .map(WorkflowEngine::toView)
                .orElseThrow(() -> ApiException.notFound("Workflow state", stateCode));
    }

    @Override
    public List<TransitionView> transitionsFrom(UUID workflowDefinitionId, String stateCode) {
        return load(workflowDefinitionId).transitionsFrom(stateCode).stream()
                .sorted(java.util.Comparator.comparing(WorkflowTransition::getDisplayOrder))
                .map(WorkflowEngine::toView)
                .toList();
    }

    // ------------------------------------------------------------- evaluation

    /**
     * Transitions this actor may execute right now, with their guards already evaluated.
     *
     * <p>The API returns exactly this list, so the UI renders precisely the buttons the
     * backend will honour - rather than rendering everything and discovering on click.
     */
    public List<AvailableTransition> availableTransitions(UUID workflowDefinitionId, GuardContext context) {
        List<AvailableTransition> available = new ArrayList<>();

        for (WorkflowTransition transition : load(workflowDefinitionId).transitionsFrom(context.currentState())) {
            if (!transition.permits(context.actorRoles(), context.actorRelationships())) {
                continue;
            }
            // A guard needing a reason cannot pass before the user has typed one, so it is
            // reported as "available, requires input" rather than hidden.
            List<GuardResult> failures = evaluateGuards(transition, context).stream()
                    .filter(result -> !result.passed())
                    .filter(result -> !"REQUIRE_REASON".equals(result.guardCode()))
                    .toList();

            available.add(new AvailableTransition(
                    transition.getTransitionCode(),
                    transition.getLabel(),
                    transition.getToState(),
                    transition.isRequiresReason(),
                    failures.isEmpty(),
                    failures.stream().map(GuardResult::message).toList()));
        }
        return available;
    }

    /**
     * Validates a requested transition and returns where it lands.
     *
     * @throws ApiException INVALID_TRANSITION when no such edge leaves the current state,
     *         TRANSITION_NOT_PERMITTED when the actor lacks the role or relationship, or
     *         TRANSITION_GUARD_FAILED with every failing guard listed.
     */
    public ResolvedTransition resolve(UUID workflowDefinitionId, String transitionCode, GuardContext context) {
        WorkflowDefinition definition = load(workflowDefinitionId);

        WorkflowTransition transition = definition.transition(transitionCode, context.currentState())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TRANSITION,
                        "'%s' is not available from state '%s'. Available: %s."
                                .formatted(transitionCode, context.currentState(),
                                        legalTransitionCodes(definition, context.currentState()))));

        if (transition.isAutomatic()) {
            throw new ApiException(ErrorCode.TRANSITION_NOT_PERMITTED,
                    "'%s' is applied by the system and cannot be requested directly."
                            .formatted(transitionCode));
        }

        if (!transition.permits(context.actorRoles(), context.actorRelationships())) {
            throw new ApiException(ErrorCode.TRANSITION_NOT_PERMITTED,
                    "You do not have permission to perform '%s' on this item.".formatted(transitionCode));
        }

        List<GuardResult> failures = evaluateGuards(transition, context).stream()
                .filter(result -> !result.passed())
                .toList();

        if (!failures.isEmpty()) {
            throw new ApiException(ErrorCode.TRANSITION_GUARD_FAILED,
                    failures.stream().map(GuardResult::message).collect(Collectors.joining(" ")),
                    failures.stream()
                            .map(result -> new ApiException.FieldError(
                                    "transition", result.guardCode(), result.message()))
                            .toList());
        }

        WorkflowState target = definition.state(transition.getToState())
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                        "Workflow '%s' points at undefined state '%s'."
                                .formatted(definition.getCode(), transition.getToState())));

        return new ResolvedTransition(transition.getTransitionCode(), transition.getLabel(),
                context.currentState(), target.getCode(), toView(target));
    }

    /**
     * Resolves a system-applied transition, skipping the actor permission check.
     *
     * <p>Guards still run: an automatic transition that would violate an invariant must
     * fail loudly rather than corrupt state. Used in Phase 4 onward by AI analysis and
     * progress rollup; in Phase 3 by the ticket-to-development completion trigger.
     */
    public Optional<ResolvedTransition> resolveAutomatic(UUID workflowDefinitionId,
                                                         String transitionCode,
                                                         GuardContext context) {
        WorkflowDefinition definition = load(workflowDefinitionId);
        Optional<WorkflowTransition> candidate = definition.transition(transitionCode, context.currentState());

        if (candidate.isEmpty() || !candidate.get().isAutomatic()) {
            return Optional.empty();
        }
        WorkflowTransition transition = candidate.get();

        boolean allPassed = evaluateGuards(transition, context).stream().allMatch(GuardResult::passed);
        if (!allPassed) {
            return Optional.empty();
        }

        return definition.state(transition.getToState()).map(target ->
                new ResolvedTransition(transition.getTransitionCode(), transition.getLabel(),
                        context.currentState(), target.getCode(), toView(target)));
    }

    private List<GuardResult> evaluateGuards(WorkflowTransition transition, GuardContext context) {
        List<GuardResult> results = new ArrayList<>();
        for (String guardCode : transition.guardCodes()) {
            TransitionGuard guard = guards.get(guardCode);
            if (guard == null) {
                // Configuration names a guard the code does not implement. Fail closed:
                // a silently ignored guard is an invariant that quietly stopped holding.
                results.add(GuardResult.fail(guardCode,
                        "Workflow configuration references unknown guard '" + guardCode + "'."));
                continue;
            }
            results.add(guard.evaluate(context));
        }
        return results;
    }

    private String legalTransitionCodes(WorkflowDefinition definition, String fromState) {
        String codes = definition.transitionsFrom(fromState).stream()
                .filter(transition -> !transition.isAutomatic())
                .map(WorkflowTransition::getTransitionCode)
                .collect(Collectors.joining(", "));
        return codes.isEmpty() ? "none (terminal state)" : codes;
    }

    // ------------------------------------------------------------------ cache

    private WorkflowDefinition load(UUID workflowDefinitionId) {
        Map<UUID, WorkflowDefinition> loaded = cache;
        if (loaded == null) {
            loaded = refresh();
        }
        WorkflowDefinition definition = loaded.get(workflowDefinitionId);
        if (definition == null) {
            // Miss on an id we have not seen: refresh once before declaring it unknown.
            definition = refresh().get(workflowDefinitionId);
        }
        if (definition == null) {
            throw ApiException.notFound("Workflow definition", workflowDefinitionId);
        }
        return definition;
    }

    @Transactional(readOnly = true)
    public Map<UUID, WorkflowDefinition> refresh() {
        Map<UUID, WorkflowDefinition> loaded = new LinkedHashMap<>();
        // Two queries, not one: fetching both collections in a single join would multiply
        // states by transitions.
        definitions.findAllWithStates().forEach(definition ->
                loaded.compute(definition.getId(), (id, existing) -> mergeDefinitions(existing, definition)));
        definitions.findAllWithTransitions().forEach(definition ->
                loaded.compute(definition.getId(), (id, existing) -> mergeDefinitions(existing, definition)));

        Map<UUID, WorkflowDefinition> snapshot = new HashMap<>(loaded);
        this.cache = snapshot;
        return snapshot;
    }

    /** Called by the admin workflow endpoints in a later phase; exposed now for tests. */
    public void evict() {
        this.cache = null;
    }

    // ------------------------------------------------------------------ views

    private static DefinitionView toView(WorkflowDefinition definition) {
        return new DefinitionView(definition.getId(), definition.getCode(), definition.getName(),
                definition.getAppliesToFamily(), definition.getInitialState());
    }

    private static StateView toView(WorkflowState state) {
        return new StateView(state.getCode(), state.getName(), state.getCategory(),
                state.isInitial(), state.isTerminal(), state.isTerminalSuccess(),
                state.isActiveWork(), state.isBlocked(), state.isWaitingExternal());
    }

    private static TransitionView toView(WorkflowTransition transition) {
        return new TransitionView(transition.getTransitionCode(), transition.getLabel(),
                transition.getFromState(), transition.getToState(),
                transition.isRequiresReason(), transition.isAutomatic(), transition.guardCodes());
    }

    private static WorkflowDefinition mergeDefinitions(WorkflowDefinition existing,
                                                       WorkflowDefinition incoming) {
        if (existing == null) {
            return incoming;
        }
        if (!incoming.getStates().isEmpty()) {
            existing.replaceStates(incoming.getStates());
        }
        if (!incoming.getTransitions().isEmpty()) {
            existing.replaceTransitions(incoming.getTransitions());
        }
        return existing;
    }

    public record AvailableTransition(String code, String label, String toState,
                                      boolean requiresReason, boolean enabled,
                                      List<String> blockedReasons) {
    }

    public record ResolvedTransition(String transitionCode, String label,
                                     String fromState, String toState, StateView targetState) {
    }
}
