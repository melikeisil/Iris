package com.smartwork.workitem.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Work family and workflow resolution")
class WorkFamilyResolverTest {

    @Test
    void aTaskTakesItsFamilyFromItsOrigin() {
        // Same type, four different boards. Getting this wrong routes the work to the
        // wrong team with a workflow that has the wrong stages.
        assertThat(resolve(WorkItemType.TASK, OriginType.TICKET_GENERATED, null))
                .isEqualTo(WorkFamily.SERVICE_WORK);
        assertThat(resolve(WorkItemType.TASK, OriginType.PROCESS_TEMPLATE, null))
                .isEqualTo(WorkFamily.PROCESS_WORK);
        assertThat(resolve(WorkItemType.TASK, OriginType.INCIDENT_GENERATED, null))
                .isEqualTo(WorkFamily.INCIDENT_WORK);
        assertThat(resolve(WorkItemType.TASK, OriginType.PROJECT_PLANNING, null))
                .isEqualTo(WorkFamily.DEVELOPMENT_WORK);
    }

    @Test
    void aTaskUnderAParentInheritsTheParentsFamily() {
        assertThat(resolve(WorkItemType.TASK, OriginType.USER_CREATED, WorkFamily.DEVELOPMENT_WORK))
                .isEqualTo(WorkFamily.DEVELOPMENT_WORK);
        assertThat(resolve(WorkItemType.TASK, OriginType.AI_GENERATED, WorkFamily.PROCESS_WORK))
                .isEqualTo(WorkFamily.PROCESS_WORK);
    }

    @Test
    void aStandaloneUserCreatedTaskIsGeneralOperationalWork() {
        assertThat(resolve(WorkItemType.TASK, OriginType.USER_CREATED, null))
                .isEqualTo(WorkFamily.GENERAL_OPERATIONAL_WORK);
    }

    @Test
    void aSubtaskAlwaysFollowsItsParent() {
        for (WorkFamily parentFamily : WorkFamily.values()) {
            assertThat(resolve(WorkItemType.SUBTASK, OriginType.USER_CREATED, parentFamily))
                    .as("subtask under %s", parentFamily)
                    .isEqualTo(parentFamily);
        }
    }

    @Test
    void aBugIsDevelopmentWorkEvenWhenRaisedFromATicket() {
        // The fix is code and belongs on the development board with the rest of the code,
        // however the defect was reported.
        assertThat(resolve(WorkItemType.BUG, OriginType.TICKET_GENERATED, WorkFamily.SERVICE_WORK))
                .isEqualTo(WorkFamily.DEVELOPMENT_WORK);
    }

    @ParameterizedTest
    @CsvSource({
            "REQUEST, SERVICE_WORK",
            "INCIDENT, INCIDENT_WORK",
            "EPIC, DEVELOPMENT_WORK",
            "FEATURE, DEVELOPMENT_WORK",
            "STORY, DEVELOPMENT_WORK",
            "TECHNICAL_DEBT, DEVELOPMENT_WORK"
    })
    void someTypesDetermineTheirOwnFamily(WorkItemType type, WorkFamily expected) {
        for (OriginType origin : OriginType.values()) {
            assertThat(resolve(type, origin, WorkFamily.GENERAL_OPERATIONAL_WORK))
                    .as("%s from %s", type, origin)
                    .isEqualTo(expected);
        }
    }

    @Test
    void bugsAndResearchGetTheirOwnWorkflowProfiles() {
        // Both are development work, but a bug needs root-cause analysis and QA while a
        // spike needs findings review; the generic development flow loses both.
        assertThat(WorkFamilyResolver.workflowCodeFor(WorkItemType.BUG, WorkFamily.DEVELOPMENT_WORK))
                .isEqualTo("BUG");
        assertThat(WorkFamilyResolver.workflowCodeFor(WorkItemType.RESEARCH, WorkFamily.DEVELOPMENT_WORK))
                .isEqualTo("RESEARCH");
        assertThat(WorkFamilyResolver.workflowCodeFor(WorkItemType.STORY, WorkFamily.DEVELOPMENT_WORK))
                .isEqualTo("DEVELOPMENT");
        assertThat(WorkFamilyResolver.workflowCodeFor(WorkItemType.REQUEST, WorkFamily.SERVICE_WORK))
                .isEqualTo("SERVICE");
        assertThat(WorkFamilyResolver.workflowCodeFor(WorkItemType.TASK, WorkFamily.PROCESS_WORK))
                .isEqualTo("PROCESS");
    }

    @Test
    void closureDefaultsMatchWhoShouldConfirmTheWork() {
        assertThat(WorkFamily.SERVICE_WORK.defaultClosureType())
                .isEqualTo(ClosureType.REQUESTER_VERIFICATION);
        assertThat(WorkFamily.PROCESS_WORK.defaultClosureType())
                .isEqualTo(ClosureType.PROCESS_OWNER_VERIFICATION);
        assertThat(WorkFamily.INCIDENT_WORK.defaultClosureType())
                .isEqualTo(ClosureType.TEAM_LEAD_VERIFICATION);
        assertThat(WorkFamily.DEVELOPMENT_WORK.defaultClosureType())
                .isEqualTo(ClosureType.AUTO_CLOSE);
    }

    private static WorkFamily resolve(WorkItemType type, OriginType origin, WorkFamily parentFamily) {
        return WorkFamilyResolver.resolve(type, origin, parentFamily);
    }
}
