package com.smartwork.workflow.domain;

import com.smartwork.common.security.ResourceRole;
import com.smartwork.workflow.domain.guards.StandardGuards;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards are pure predicates over a context, so the whole enforcement suite is testable
 * without a database, a web container or a workflow definition.
 */
@DisplayName("Workflow transition guards")
class StandardGuardsTest {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID SOMEONE_ELSE = UUID.randomUUID();

    @Test
    void everyGuardNamedByTheSeededProfilesIsImplemented() {
        // A guard named in configuration but missing in code would be an invariant that
        // quietly stopped holding. The engine fails closed on one; this catches it earlier.
        assertThat(StandardGuards.all()).extracting(TransitionGuard::code)
                .containsExactlyInAnyOrder(
                        "REQUIRE_REASON", "REQUIRE_ASSIGNEE", "NOT_SELF_REVIEW",
                        "NO_OPEN_DEPENDENCIES", "REQUIRE_ACCEPTANCE_CRITERIA",
                        "ALL_CHILDREN_TERMINAL", "REQUIRE_VERIFICATION_ACTOR",
                        "REQUIRE_POSTMORTEM", "REQUIRE_SPRINT", "LEVEL_RESTRICTION");
    }

    @Nested
    @DisplayName("NOT_SELF_REVIEW")
    class NotSelfReview {
        private final TransitionGuard guard = new StandardGuards.NotSelfReview();

        @Test
        void refusesTheAssigneeApprovingTheirOwnWork() {
            var context = base().assignee(ACTOR).build();

            assertThat(guard.evaluate(context).passed()).isFalse();
        }

        @Test
        void allowsAnyoneElse() {
            var context = base().assignee(SOMEONE_ELSE).build();

            assertThat(guard.evaluate(context).passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("NO_OPEN_DEPENDENCIES")
    class NoOpenDependencies {
        private final TransitionGuard guard = new StandardGuards.NoOpenDependencies();

        @Test
        void namesTheBlockingItems() {
            var context = base().dependencies(Set.of("DEV-458", "DEV-459")).build();
            var result = guard.evaluate(context);

            assertThat(result.passed()).isFalse();
            // "Blocked by DEV-458" is actionable; "guard failed" is not.
            assertThat(result.message()).contains("DEV-458").contains("DEV-459");
        }

        @Test
        void passesWhenNothingIsOutstanding() {
            assertThat(guard.evaluate(base().build()).passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("REQUIRE_ACCEPTANCE_CRITERIA")
    class AcceptanceCriteria {
        private final TransitionGuard guard = new StandardGuards.RequireAcceptanceCriteria();

        @Test
        void appliesToStoriesAndFeaturesOnly() {
            assertThat(guard.evaluate(base().type("STORY").build()).passed()).isFalse();
            assertThat(guard.evaluate(base().type("FEATURE").build()).passed()).isFalse();
            assertThat(guard.evaluate(base().type("TASK").build()).passed()).isTrue();
            assertThat(guard.evaluate(base().type("BUG").build()).passed()).isTrue();
        }

        @Test
        void passesOnceCriteriaExist() {
            var context = base().type("STORY").criteria("Given a user, when they upload...").build();

            assertThat(guard.evaluate(context).passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("REQUIRE_VERIFICATION_ACTOR")
    class VerificationActor {
        private final TransitionGuard guard = new StandardGuards.RequireVerificationActor();

        @Test
        void onlyTheRequesterMayVerifyAConvertedTicket() {
            var wrongPerson = base().closure("REQUESTER_VERIFICATION")
                    .relationships(Set.of(ResourceRole.ASSIGNEE)).build();
            var requester = base().closure("REQUESTER_VERIFICATION")
                    .relationships(Set.of(ResourceRole.REQUESTER)).build();

            assertThat(guard.evaluate(wrongPerson).passed()).isFalse();
            assertThat(guard.evaluate(requester).passed()).isTrue();
        }

        @Test
        void autoCloseImposesNoRestriction() {
            assertThat(guard.evaluate(base().closure("AUTO_CLOSE").build()).passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("REQUIRE_POSTMORTEM")
    class Postmortem {
        private final TransitionGuard guard = new StandardGuards.RequirePostmortem();

        @Test
        void criticalOrProductionIncidentsCannotSkipIt() {
            assertThat(guard.evaluate(base().priority("CRITICAL").build()).passed()).isFalse();
            assertThat(guard.evaluate(base().production(true).build()).passed()).isFalse();
        }

        @Test
        void aRoutineIncidentMayCloseDirectly() {
            assertThat(guard.evaluate(base().priority("MEDIUM").build()).passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("LEVEL_RESTRICTION")
    class LevelRestriction {
        private final TransitionGuard guard = new StandardGuards.LevelRestriction();

        @Test
        void anInternCannotTakeProductionBudgetSecurityOrCriticalWork() {
            assertThat(guard.evaluate(intern().production(true).build()).passed()).isFalse();
            assertThat(guard.evaluate(intern().budget(true).build()).passed()).isFalse();
            assertThat(guard.evaluate(intern().security(true).build()).passed()).isFalse();
            assertThat(guard.evaluate(intern().priority("CRITICAL").build()).passed()).isFalse();
        }

        @Test
        void anInternCannotTakeAnOversizedItem() {
            assertThat(guard.evaluate(intern().hours(new BigDecimal("12")).build()).passed()).isFalse();
            assertThat(guard.evaluate(intern().hours(new BigDecimal("6")).build()).passed()).isTrue();
        }

        @Test
        void aJuniorIsRestrictedLessThanAnIntern() {
            var junior = base().assignee(SOMEONE_ELSE).level("JUNIOR");

            assertThat(guard.evaluate(junior.hours(new BigDecimal("12")).build()).passed()).isTrue();
            assertThat(guard.evaluate(junior.hours(new BigDecimal("20")).build()).passed()).isFalse();
            assertThat(guard.evaluate(junior.production(true).build()).passed()).isFalse();
        }

        @Test
        void seniorsAreUnrestricted() {
            var senior = base().assignee(SOMEONE_ELSE).level("SENIOR")
                    .production(true).security(true).priority("CRITICAL")
                    .hours(new BigDecimal("100")).build();

            assertThat(guard.evaluate(senior).passed()).isTrue();
        }

        @Test
        void anUnassignedItemIsNotJudged() {
            assertThat(guard.evaluate(base().assignee(null).level(null).build()).passed()).isTrue();
        }

        private Builder intern() {
            return base().assignee(SOMEONE_ELSE).level("INTERN");
        }
    }

    @Nested
    @DisplayName("REQUIRE_REASON")
    class Reason {
        private final TransitionGuard guard = new StandardGuards.RequireReason();

        @Test
        void rejectsBlankAndTokenReasons() {
            assertThat(guard.evaluate(base().reason(null).build()).passed()).isFalse();
            assertThat(guard.evaluate(base().reason("   ").build()).passed()).isFalse();
            assertThat(guard.evaluate(base().reason("wip").build()).passed()).isFalse();
        }

        @Test
        void acceptsAGenuineExplanation() {
            var context = base().reason("Waiting for the storage bucket policy from Platform").build();

            assertThat(guard.evaluate(context).passed()).isTrue();
        }
    }

    // ------------------------------------------------------------- builder

    private static Builder base() {
        return new Builder();
    }

    private static final class Builder {
        private String type = "TASK";
        private String reason;
        private UUID assigneeId = SOMEONE_ELSE;
        private String level;
        private String criteria;
        private String closure = "AUTO_CLOSE";
        private String priority = "MEDIUM";
        private BigDecimal hours;
        private boolean hasChildren;
        private boolean unfinishedChildren;
        private Set<String> dependencies = Set.of();
        private UUID sprintId;
        private boolean sprintActive;
        private boolean security;
        private boolean production;
        private boolean budget;
        private Set<ResourceRole> relationships = Set.of();

        Builder type(String value) { this.type = value; return this; }
        Builder reason(String value) { this.reason = value; return this; }
        Builder assignee(UUID value) { this.assigneeId = value; return this; }
        Builder level(String value) { this.level = value; return this; }
        Builder criteria(String value) { this.criteria = value; return this; }
        Builder closure(String value) { this.closure = value; return this; }
        Builder priority(String value) { this.priority = value; return this; }
        Builder hours(BigDecimal value) { this.hours = value; return this; }
        Builder dependencies(Set<String> value) { this.dependencies = value; return this; }
        Builder security(boolean value) { this.security = value; return this; }
        Builder production(boolean value) { this.production = value; return this; }
        Builder budget(boolean value) { this.budget = value; return this; }
        Builder relationships(Set<ResourceRole> value) { this.relationships = value; return this; }

        GuardContext build() {
            return new GuardContext(UUID.randomUUID(), type, "IN_PROGRESS", "DONE",
                    ACTOR, Set.of("EMPLOYEE"), relationships, reason,
                    assigneeId, SOMEONE_ELSE, null, level,
                    criteria, closure, priority, hours,
                    hasChildren, unfinishedChildren, dependencies,
                    sprintId, sprintActive,
                    security, production, budget, false);
        }
    }
}
