package com.smartwork.workitem.domain;

import com.smartwork.common.error.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Type-aware work item validation")
class WorkItemValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 1);

    @Nested
    @DisplayName("Hierarchy")
    class Hierarchy {

        @Test
        void aSubtaskMustHaveAParent() {
            assertThatThrownBy(() -> validate(command(WorkItemType.SUBTASK).parent(null).build(), null))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(fields(e)).contains("parentId"));
        }

        @Test
        void aTicketIncidentOrEpicIsAlwaysARoot() {
            for (WorkItemType type : new WorkItemType[]{
                    WorkItemType.REQUEST, WorkItemType.INCIDENT, WorkItemType.EPIC}) {
                assertThatThrownBy(() -> validate(
                        command(type).parent(UUID.randomUUID()).project(UUID.randomUUID()).build(),
                        WorkItemType.EPIC))
                        .as("%s with a parent", type)
                        .isInstanceOf(ApiException.class);
            }
        }

        @Test
        void aSubtaskCannotBeAParent() {
            assertThatThrownBy(() -> validate(
                    command(WorkItemType.TASK).parent(UUID.randomUUID()).build(), WorkItemType.SUBTASK))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(fields(e)).contains("parentId"));
        }

        @Test
        void aTaskNeedsNoParentProjectOrTicket() {
            // The central claim of ADR-002: work does not have to come from anywhere.
            assertThatCode(() -> validate(command(WorkItemType.TASK).build(), null))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Story points")
    class StoryPoints {

        @Test
        void applyOnlyToEstimatedDevelopmentTypes() {
            assertThatCode(() -> validate(
                    command(WorkItemType.STORY).project(UUID.randomUUID()).points((short) 8).build(), null))
                    .doesNotThrowAnyException();

            for (WorkItemType type : new WorkItemType[]{
                    WorkItemType.REQUEST, WorkItemType.TASK, WorkItemType.SUBTASK,
                    WorkItemType.RESEARCH, WorkItemType.TEST, WorkItemType.DOCUMENTATION}) {
                assertThatThrownBy(() -> validate(
                        command(type).parent(type == WorkItemType.SUBTASK ? UUID.randomUUID() : null)
                                .points((short) 5).build(), WorkItemType.STORY))
                        .as("%s with story points", type)
                        .isInstanceOf(ApiException.class);
            }
        }

        @Test
        void mustBeFibonacci() {
            assertThatThrownBy(() -> validate(
                    command(WorkItemType.STORY).project(UUID.randomUUID()).points((short) 4).build(), null))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(fields(e)).contains("storyPoints"));
        }

        @Test
        void acceptTheWholeFibonacciScale() {
            for (short points : new short[]{1, 2, 3, 5, 8, 13, 21}) {
                assertThatCode(() -> validate(
                        command(WorkItemType.STORY).project(UUID.randomUUID()).points(points).build(), null))
                        .as("%d points", points)
                        .doesNotThrowAnyException();
            }
        }
    }

    @Nested
    @DisplayName("Other rules")
    class OtherRules {

        @Test
        void developmentContainersRequireAProject() {
            for (WorkItemType type : new WorkItemType[]{
                    WorkItemType.EPIC, WorkItemType.FEATURE, WorkItemType.STORY,
                    WorkItemType.TECHNICAL_DEBT}) {
                assertThatThrownBy(() -> validate(command(type).project(null).build(), null))
                        .as("%s without a project", type)
                        .isInstanceOf(ApiException.class)
                        .satisfies(e -> assertThat(fields(e)).contains("projectId"));
            }
        }

        @Test
        void anIncidentCannotBeLowPriority() {
            assertThatThrownBy(() -> validate(
                    command(WorkItemType.INCIDENT).priority(Priority.LOW).build(), null))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(fields(e)).contains("priority"));
        }

        @Test
        void estimatesAreBounded() {
            assertThatThrownBy(() -> validate(
                    command(WorkItemType.TASK).hours(new BigDecimal("500")).build(), null))
                    .isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> validate(
                    command(WorkItemType.TASK).hours(BigDecimal.ZERO).build(), null))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        void aDueDateInThePastIsRejectedOnCreationButAllowedOnUpdate() {
            var command = command(WorkItemType.TASK).due(TODAY.minusDays(3)).build();

            assertThatThrownBy(() -> validate(command, null)).isInstanceOf(ApiException.class);
            // An item can legitimately become overdue; refusing the edit would make it
            // impossible to record that it did.
            assertThatCode(() -> WorkItemValidator.validateForUpdate(command, TODAY))
                    .doesNotThrowAnyException();
        }

        @Test
        void everyProblemIsReportedAtOnce() {
            // A form should not be fixed one field per round trip.
            var command = new WorkItemValidator.Command(
                    WorkItemType.STORY, "no", UUID.randomUUID(), null,
                    (short) 4, new BigDecimal("999"), TODAY.minusDays(1), Priority.MEDIUM);

            assertThatThrownBy(() -> validate(command, WorkItemType.EPIC))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getFieldErrors()).hasSizeGreaterThanOrEqualTo(4));
        }
    }

    // ------------------------------------------------------------- helpers

    private static void validate(WorkItemValidator.Command command, WorkItemType parentType) {
        WorkItemValidator.validateForCreation(command, parentType, TODAY);
    }

    private static java.util.List<String> fields(Throwable thrown) {
        return ((ApiException) thrown).getFieldErrors().stream()
                .map(ApiException.FieldError::field).toList();
    }

    private static Builder command(WorkItemType type) {
        return new Builder(type);
    }

    private static final class Builder {
        private final WorkItemType type;
        private String summary = "A sufficiently descriptive summary";
        private UUID parentId;
        private UUID projectId;
        private Short storyPoints;
        private BigDecimal estimatedHours;
        private LocalDate dueDate;
        private Priority priority = Priority.MEDIUM;

        private Builder(WorkItemType type) {
            this.type = type;
            if (type.requiresProject()) {
                this.projectId = UUID.randomUUID();
            }
            if (type.mustHaveParent()) {
                this.parentId = UUID.randomUUID();
            }
            if (type == WorkItemType.INCIDENT) {
                this.priority = Priority.HIGH;
            }
        }

        Builder parent(UUID value) {
            this.parentId = value;
            return this;
        }

        Builder project(UUID value) {
            this.projectId = value;
            return this;
        }

        Builder points(Short value) {
            this.storyPoints = value;
            return this;
        }

        Builder hours(BigDecimal value) {
            this.estimatedHours = value;
            return this;
        }

        Builder due(LocalDate value) {
            this.dueDate = value;
            return this;
        }

        Builder priority(Priority value) {
            this.priority = value;
            return this;
        }

        WorkItemValidator.Command build() {
            return new WorkItemValidator.Command(type, summary, parentId, projectId,
                    storyPoints, estimatedHours, dueDate, priority);
        }
    }
}
