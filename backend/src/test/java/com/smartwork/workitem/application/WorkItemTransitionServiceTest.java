package com.smartwork.workitem.application;

import com.smartwork.common.error.ApiException;
import com.smartwork.identity.application.UserDirectory;
import com.smartwork.workflow.application.WorkflowCatalog;
import com.smartwork.workflow.application.WorkflowEngine;
import com.smartwork.workflow.domain.GuardContext;
import com.smartwork.workflow.domain.StateCategory;
import com.smartwork.workitem.domain.OriginType;
import com.smartwork.workitem.domain.WorkFamily;
import com.smartwork.workitem.domain.WorkItem;
import com.smartwork.workitem.domain.WorkItemType;
import com.smartwork.workitem.infrastructure.WorkItemLinkRepository;
import com.smartwork.workitem.infrastructure.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WorkItemTransitionService#applyAutomatic}, the entry point
 * {@code analysis} uses to fire {@code ANALYZE} and {@code NEEDS_INFO} without an actor.
 */
class WorkItemTransitionServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID WORK_ITEM_ID = UUID.randomUUID();
    private static final UUID WORKFLOW_DEFINITION_ID = UUID.randomUUID();

    private WorkItemRepository workItems;
    private WorkflowEngine engine;
    private WorkItemTransitionService service;
    private WorkItem workItem;

    @BeforeEach
    void setUp() {
        workItems = mock(WorkItemRepository.class);
        WorkItemLinkRepository links = mock(WorkItemLinkRepository.class);
        WorkItemService workItemService = mock(WorkItemService.class);
        WorkItemAccessEvaluator access = mock(WorkItemAccessEvaluator.class);
        GuardContextAssembler guardContexts = mock(GuardContextAssembler.class);
        engine = mock(WorkflowEngine.class);
        UserDirectory users = mock(UserDirectory.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-02T10:00:00Z"), ZoneOffset.UTC);

        service = new WorkItemTransitionService(
                workItems, links, workItemService, access, guardContexts, engine, users, clock);

        workItem = new WorkItem(ORG_ID, "TCK-1", WorkItemType.REQUEST, WorkFamily.SERVICE_WORK,
                OriginType.USER_CREATED, WORKFLOW_DEFINITION_ID, "NEW",
                "Set up a new laptop", UUID.randomUUID());
        workItem.assignId(WORK_ITEM_ID);

        when(workItems.findById(WORK_ITEM_ID)).thenReturn(Optional.of(workItem));
    }

    @Test
    @DisplayName("applies the transition and returns it when the engine resolves one")
    void appliesWhenTheEngineResolvesAnEdge() {
        WorkflowCatalog.StateView targetState = new WorkflowCatalog.StateView(
                "AI_ANALYSIS", "AI Analysis", StateCategory.TODO,
                false, false, false, false, false, false);
        WorkflowEngine.ResolvedTransition resolved = new WorkflowEngine.ResolvedTransition(
                "ANALYZE", "Analyze", "NEW", "AI_ANALYSIS", targetState);

        when(engine.resolveAutomatic(eq(WORKFLOW_DEFINITION_ID), eq("ANALYZE"), any(GuardContext.class)))
                .thenReturn(Optional.of(resolved));

        Optional<WorkflowEngine.ResolvedTransition> result = service.applyAutomatic(WORK_ITEM_ID, "ANALYZE");

        assertThat(result).contains(resolved);
        assertThat(workItem.getStatus()).isEqualTo("AI_ANALYSIS");
    }

    @Test
    @DisplayName("is a no-op, not an error, when the transition does not apply from the current state")
    void noOpWhenTheEdgeDoesNotApply() {
        when(engine.resolveAutomatic(eq(WORKFLOW_DEFINITION_ID), anyString(), any(GuardContext.class)))
                .thenReturn(Optional.empty());

        Optional<WorkflowEngine.ResolvedTransition> result = service.applyAutomatic(WORK_ITEM_ID, "NEEDS_INFO");

        assertThat(result).isEmpty();
        assertThat(workItem.getStatus()).isEqualTo("NEW");
    }

    @Test
    @DisplayName("fails with NOT_FOUND for an id that does not exist, rather than silently doing nothing")
    void notFoundForAMissingItem() {
        UUID missing = UUID.randomUUID();
        when(workItems.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyAutomatic(missing, "ANALYZE"))
                .isInstanceOf(ApiException.class);

        verify(engine, never()).resolveAutomatic(any(), anyString(), any());
    }
}
