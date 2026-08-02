package com.smartwork.integration;

import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.identity.domain.SystemRole;
import com.smartwork.process.application.ProcessInstantiator;
import com.smartwork.process.domain.ProcessTemplate;
import com.smartwork.process.domain.ProcessTemplateTask;
import com.smartwork.process.infrastructure.ProcessDefinitionRepository;
import com.smartwork.process.infrastructure.ProcessOwnerRepository;
import com.smartwork.process.infrastructure.ProcessTemplateRepository;
import com.smartwork.support.IntegrationTest;
import com.smartwork.support.TestFixtures;
import com.smartwork.workitem.application.WorkItemLinkService;
import com.smartwork.workitem.application.WorkItemService;
import com.smartwork.workitem.domain.LinkType;
import com.smartwork.workitem.domain.OriginType;
import com.smartwork.workitem.domain.Priority;
import com.smartwork.workitem.domain.WorkItem;
import com.smartwork.workitem.domain.WorkItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 5's stated exit criterion: an onboarding ticket produces a cross-department plan.
 *
 * <p>Asserts the properties that make the plan <em>correct</em> rather than merely present:
 * the tasks span three departments, the dependency edges declared as sequence numbers in
 * the template become real typed links, and the Process Owner is a plain EMPLOYEE.
 */
@IntegrationTest
class ProcessInstantiationIT {

    @Autowired
    private TestFixtures fixtures;
    @Autowired
    private ProcessTemplateRepository templates;
    @Autowired
    private ProcessInstantiator instantiator;
    @Autowired
    private WorkItemService workItems;
    @Autowired
    private WorkItemLinkService links;
    @Autowired
    private ProcessDefinitionRepository processDefinitions;
    @Autowired
    private ProcessOwnerRepository processOwners;

    private UUID organizationId;
    private UUID hrDepartmentId;
    private UUID itDepartmentId;
    private UUID financeDepartmentId;
    private AuthPrincipal requester;
    private UUID processOwnerId;

    @BeforeEach
    void setUp() {
        organizationId = fixtures.organization("PROC" + System.nanoTime() % 100000);
        hrDepartmentId = fixtures.department(organizationId, "HR").id();
        itDepartmentId = fixtures.department(organizationId, "IT").id();
        financeDepartmentId = fixtures.department(organizationId, "FINANCE").id();

        UUID requesterId = fixtures.user(
                organizationId, "requester-" + UUID.randomUUID() + "@acme.io",
                Set.of(SystemRole.EMPLOYEE, SystemRole.TEAM_LEAD), hrDepartmentId);

        // Deliberately an EMPLOYEE, not a TEAM_LEAD: PROCESS_OWNER is a resource
        // relationship, not a system role, and the demo depends on that being true.
        processOwnerId = fixtures.user(
                organizationId, "hr-lead-" + UUID.randomUUID() + "@acme.io",
                Set.of(SystemRole.EMPLOYEE), hrDepartmentId);

        requester = new AuthPrincipal(
                requesterId, organizationId, hrDepartmentId, Set.of(),
                Set.of("EMPLOYEE", "TEAM_LEAD"), "SENIOR", false);
    }

    private ProcessTemplate onboardingTemplate() {
        ProcessTemplate template = new ProcessTemplate(organizationId, "EMPLOYEE_ONBOARDING", "Employee Onboarding");
        template.setMatchKeywords("onboarding onboard new hire employee starting");
        template.setDefaultProcessOwnerId(processOwnerId);
        template.setDefaultPriority(Priority.HIGH);

        template.addTask(task(1, "Create HR personnel file and offer paperwork",
                hrDepartmentId, "1.50", null));
        template.addTask(task(2, "Provision Active Directory account and email",
                itDepartmentId, "1.00", "1"));
        template.addTask(task(3, "Assign Microsoft 365 licence and mailbox",
                itDepartmentId, "0.50", "2"));
        template.addTask(task(4, "Purchase desk setup and office equipment",
                financeDepartmentId, "1.00", "1"));
        template.addTask(task(5, "Confirm payroll setup and benefits enrollment",
                financeDepartmentId, "2.00", "1"));

        return templates.save(template);
    }

    private ProcessTemplateTask task(
            int sequence, String title, UUID departmentId, String hours, String dependsOn) {
        ProcessTemplateTask task = new ProcessTemplateTask(sequence, title);
        task.setDepartmentId(departmentId);
        task.setEstimatedHours(new BigDecimal(hours));
        task.setDependsOnSequences(dependsOn);
        task.setWorkItemType(WorkItemType.TASK);
        return task;
    }

    private WorkItem onboardingRequest() {
        return workItems.create(requester, new WorkItemService.CreateCommand(
                WorkItemType.REQUEST, OriginType.USER_CREATED,
                "Prepare all onboarding activities for a new employee starting Monday",
                "New backend engineer joins next week.", null,
                null, null, null, hrDepartmentId, null,
                Priority.HIGH, null, null, null, null));
    }

    private WorkItemService.ProcessRootView editableRoot(WorkItem request) {
        return workItems.requireEditableForProcess(requester, request.getId());
    }

    @Test
    @DisplayName("an onboarding ticket produces a cross-department plan with real dependency links")
    void onboardingTicketProducesACrossDepartmentPlan() {
        ProcessTemplate template = onboardingTemplate();
        WorkItem request = onboardingRequest();

        ProcessInstantiator.Result result =
                instantiator.instantiate(requester, editableRoot(request), template, null);

        assertThat(result.createdItems()).hasSize(5);

        // Three departments, which is what makes this a PROCESS_OWNER_REVIEW case in
        // Phase 6 rather than something a single team lead can wave through.
        assertThat(result.createdItems())
                .extracting(item -> workItems.requireVisible(requester, item.id()).getDepartmentId())
                .containsExactlyInAnyOrder(
                        hrDepartmentId, itDepartmentId, itDepartmentId,
                        financeDepartmentId, financeDepartmentId);
        assertThat(template.departmentSpan()).isEqualTo(3);

        // Four declared dependencies (tasks 2, 3, 4, 5 each name a prerequisite).
        assertThat(result.dependencyLinksCreated()).isEqualTo(4);

        assertThat(result.createdItems())
                .allSatisfy(item -> assertThat(workItems.requireVisible(requester, item.id()).getParentId()).isEqualTo(request.getId()))
                .allSatisfy(item -> assertThat(workItems.requireVisible(requester, item.id()).getOriginType()).isEqualTo(OriginType.PROCESS_TEMPLATE));
    }

    @Test
    @DisplayName("a step that depends on a later-created sibling still gets its link")
    void forwardReferencesAreResolvedInASecondPass() {
        // Task 3 depends on task 2, which does not exist yet when task 3's row is read.
        // A single-pass instantiator would drop this edge silently.
        ProcessTemplate template = onboardingTemplate();
        WorkItem request = onboardingRequest();

        ProcessInstantiator.Result result =
                instantiator.instantiate(requester, editableRoot(request), template, null);

        ProcessInstantiator.CreatedItem licenceTask = result.createdItems().stream()
                .filter(item -> item.summary().contains("Microsoft 365"))
                .findFirst()
                .orElseThrow();

        ProcessInstantiator.CreatedItem accountTask = result.createdItems().stream()
                .filter(item -> item.summary().contains("Active Directory"))
                .findFirst()
                .orElseThrow();

        WorkItemLinkService.LinkGraph graph = links.graph(requester, licenceTask.id());
        assertThat(graph.outgoing())
                .as("the licence task must depend on the account-provisioning task created after it")
                .anySatisfy(link -> {
                    assertThat(link.linkType()).isEqualTo(LinkType.DEPENDS_ON);
                    assertThat(link.workItemId()).isEqualTo(accountTask.id());
                });
    }

    @Test
    void theProcessOwnerIsRecordedWithSubtreeScope() {
        ProcessTemplate template = onboardingTemplate();
        WorkItem request = onboardingRequest();

        ProcessInstantiator.Result result =
                instantiator.instantiate(requester, editableRoot(request), template, null);

        assertThat(result.processOwnerId()).isEqualTo(processOwnerId);
        // One row, not one per generated task — that is what SUBTREE scope buys.
        assertThat(processOwners.findByWorkItemId(request.getId()))
                .singleElement()
                .satisfies(owner -> {
                    assertThat(owner.getUserId()).isEqualTo(processOwnerId);
                    assertThat(owner.getScope())
                            .isEqualTo(com.smartwork.process.domain.ProcessOwnerAssignment.Scope.SUBTREE);
                });
    }

    @Test
    void aRunningProcessIsRecordedAgainstTheRootItem() {
        ProcessTemplate template = onboardingTemplate();
        WorkItem request = onboardingRequest();

        instantiator.instantiate(requester, editableRoot(request), template, null);

        assertThat(processDefinitions.findByRootWorkItemIdAndStatus(
                request.getId(), com.smartwork.process.domain.ProcessDefinition.Status.RUNNING))
                .isPresent();
    }

    @Test
    @DisplayName("instantiating twice is refused rather than silently duplicating nine tasks")
    void aSecondInstantiationIsRejectedWhileOneIsRunning() {
        ProcessTemplate template = onboardingTemplate();
        WorkItem request = onboardingRequest();

        instantiator.instantiate(requester, editableRoot(request), template, null);

        assertThatThrownBy(() -> instantiator.instantiate(requester, editableRoot(request), template, null))
                .hasMessageContaining("already running");
    }

    @Test
    void anEmptyTemplateIsRefusedRatherThanProducingAnEmptyPlan() {
        ProcessTemplate empty = templates.save(
                new ProcessTemplate(organizationId, "EMPTY_TEMPLATE", "Empty"));
        WorkItem request = onboardingRequest();

        assertThatThrownBy(() -> instantiator.instantiate(requester, editableRoot(request), empty, null))
                .hasMessageContaining("no tasks");
    }
}
