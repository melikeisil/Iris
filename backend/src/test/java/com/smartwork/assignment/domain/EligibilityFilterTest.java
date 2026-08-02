package com.smartwork.assignment.domain;

import com.smartwork.assignment.domain.CandidateFeatures.RequiredSkill;
import com.smartwork.assignment.domain.CandidateFeatures.WorkItemContext;
import com.smartwork.identity.domain.EmployeeLevel;
import com.smartwork.workitem.domain.Priority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Hard filters (docs/08 §3) and the level restrictions behind them (§7). */
class EligibilityFilterTest {

    private final EligibilityFilter filter = EligibilityFilter.withDefaults();

    private static CandidateFeatures person(EmployeeLevel level, Map<String, Integer> skills) {
        return new CandidateFeatures(
                UUID.randomUUID(), "Test", skills, Map.of(),
                true, true, true, new BigDecimal("0.40"), null,
                0, 0, 0, false, 0,
                true, level, 0, false);
    }

    private static WorkItemContext item() {
        return new WorkItemContext(
                List.of(), List.of(), new BigDecimal("4"), false, false,
                false, false, false, Priority.MEDIUM, null);
    }

    @Nested
    @DisplayName("general filters")
    class General {

        @Test
        void inactiveUsersAreExcluded() {
            CandidateFeatures inactive = new CandidateFeatures(
                    UUID.randomUUID(), "Gone", Map.of(), Map.of(), true, true, true,
                    BigDecimal.ZERO, null, 0, 0, 0, false, 0,
                    false, EmployeeLevel.MID, 0, false);

            assertThat(filter.evaluate(inactive, item(), true))
                    .contains(ExclusionReason.INACTIVE_USER);
        }

        @Test
        void missingAMandatorySkillExcludes() {
            WorkItemContext needsJava = new WorkItemContext(
                    List.of(new RequiredSkill("JAVA", 3, true)), List.of(), new BigDecimal("4"),
                    false, false, false, false, false, Priority.MEDIUM, null);

            assertThat(filter.evaluate(person(EmployeeLevel.MID, Map.of("PYTHON", 5)), needsJava, true))
                    .contains(ExclusionReason.MISSING_MANDATORY_SKILL);
        }

        @Test
        void holdingTheSkillBelowTheRequiredProficiencyStillExcludes() {
            WorkItemContext needsJava4 = new WorkItemContext(
                    List.of(new RequiredSkill("JAVA", 4, true)), List.of(), new BigDecimal("4"),
                    false, false, false, false, false, Priority.MEDIUM, null);

            assertThat(filter.evaluate(person(EmployeeLevel.MID, Map.of("JAVA", 2)), needsJava4, true))
                    .contains(ExclusionReason.MISSING_MANDATORY_SKILL);
        }

        @Test
        void anOptionalSkillGapDoesNotExclude() {
            WorkItemContext optional = new WorkItemContext(
                    List.of(new RequiredSkill("REACT", 3, false)), List.of(), new BigDecimal("4"),
                    false, false, false, false, false, Priority.MEDIUM, null);

            assertThat(filter.evaluate(person(EmployeeLevel.MID, Map.of()), optional, true)).isEmpty();
        }

        @Test
        void severelyOverloadedPeopleAreExcluded() {
            CandidateFeatures swamped = new CandidateFeatures(
                    UUID.randomUUID(), "Swamped", Map.of(), Map.of(), true, true, true,
                    new BigDecimal("1.30"), null, 0, 0, 0, false, 0,
                    true, EmployeeLevel.MID, 0, false);

            assertThat(filter.evaluate(swamped, item(), true))
                    .contains(ExclusionReason.SEVERELY_OVERLOADED);
        }

        @Test
        void merelyBusyPeopleAreNotExcluded() {
            // 1.1 is overloaded and will score badly, but excluding it entirely would
            // empty the pool on a busy team and route everything to a human.
            CandidateFeatures busy = new CandidateFeatures(
                    UUID.randomUUID(), "Busy", Map.of(), Map.of(), true, true, true,
                    new BigDecimal("1.10"), null, 0, 0, 0, false, 0,
                    true, EmployeeLevel.MID, 0, false);

            assertThat(filter.evaluate(busy, item(), true)).isEmpty();
        }

        @Test
        void theReporterCannotTakeWorkThatNeedsIndependentReview() {
            CandidateFeatures reporter = new CandidateFeatures(
                    UUID.randomUUID(), "Reporter", Map.of(), Map.of(), true, true, true,
                    BigDecimal.ZERO, null, 0, 0, 0, false, 0,
                    true, EmployeeLevel.MID, 0, true);

            WorkItemContext needsReview = new WorkItemContext(
                    List.of(), List.of(), new BigDecimal("4"), false, true,
                    false, false, false, Priority.MEDIUM, null);

            assertThat(filter.evaluate(reporter, needsReview, true))
                    .contains(ExclusionReason.SELF_ASSIGNMENT_CONFLICT);
        }
    }

    @Nested
    @DisplayName("intern restrictions (§7)")
    class InternRestrictions {

        private final CandidateFeatures intern = person(EmployeeLevel.INTERN, Map.of());

        @Test
        void internsAreExcludedFromProductionWork() {
            WorkItemContext production = new WorkItemContext(
                    List.of(), List.of(), new BigDecimal("4"), false, false,
                    true, false, false, Priority.MEDIUM, null);

            assertThat(filter.evaluate(intern, production, true))
                    .contains(ExclusionReason.LEVEL_RESTRICTION_PRODUCTION_ACCESS);
        }

        @Test
        void internsAreExcludedFromBudgetWork() {
            WorkItemContext budget = new WorkItemContext(
                    List.of(), List.of(), new BigDecimal("4"), false, false,
                    false, true, false, Priority.MEDIUM, null);

            assertThat(filter.evaluate(intern, budget, true))
                    .contains(ExclusionReason.LEVEL_RESTRICTION_FINANCIAL);
        }

        @Test
        void internsAreExcludedFromSecuritySensitiveWork() {
            WorkItemContext security = new WorkItemContext(
                    List.of(), List.of(), new BigDecimal("4"), false, false,
                    false, false, true, Priority.MEDIUM, null);

            assertThat(filter.evaluate(intern, security, true))
                    .contains(ExclusionReason.LEVEL_RESTRICTION_SECURITY);
        }

        @Test
        void internsAreExcludedFromCriticalWork() {
            WorkItemContext critical = new WorkItemContext(
                    List.of(), List.of(), new BigDecimal("4"), false, false,
                    false, false, false, Priority.CRITICAL, null);

            assertThat(filter.evaluate(intern, critical, true))
                    .contains(ExclusionReason.LEVEL_RESTRICTION_PRIORITY);
        }

        @Test
        @DisplayName("the ceiling is inclusive — an intern capped at HIGH may take HIGH work")
        void internsMayTakeHighPriorityWork() {
            WorkItemContext high = new WorkItemContext(
                    List.of(), List.of(), new BigDecimal("4"), false, false,
                    false, false, false, Priority.HIGH, null);

            assertThat(filter.evaluate(intern, high, true)).isEmpty();
        }

        @Test
        void internsAreExcludedFromOversizedWork() {
            WorkItemContext oversized = new WorkItemContext(
                    List.of(), List.of(), new BigDecimal("16"), false, false,
                    false, false, false, Priority.MEDIUM, null);

            assertThat(filter.evaluate(intern, oversized, true))
                    .contains(ExclusionReason.LEVEL_RESTRICTION_MAX_HOURS);
        }

        @Test
        void internsAreExcludedOnceTheyHoldThreeActiveItems() {
            CandidateFeatures loaded = new CandidateFeatures(
                    UUID.randomUUID(), "Intern", Map.of(), Map.of(), true, true, true,
                    BigDecimal.ZERO, null, 0, 0, 0, false, 0,
                    true, EmployeeLevel.INTERN, 3, false);

            assertThat(filter.evaluate(loaded, item(), true))
                    .contains(ExclusionReason.LEVEL_RESTRICTION_MAX_ACTIVE);
        }

        @Test
        void internsRequireASeniorReviewer() {
            assertThat(filter.evaluate(intern, item(), false))
                    .contains(ExclusionReason.LEVEL_RESTRICTION_MENTOR_REQUIRED);
        }

        @Test
        void aSeniorTakesTheSameWorkWithoutRestriction() {
            WorkItemContext hardWork = new WorkItemContext(
                    List.of(), List.of(), new BigDecimal("40"), false, false,
                    true, true, true, Priority.CRITICAL, null);

            assertThat(filter.evaluate(person(EmployeeLevel.SENIOR, Map.of()), hardWork, false)).isEmpty();
        }

        @Test
        @DisplayName("MID is barred from financial approval but not from production")
        void midLevelRestrictionsDifferFromJunior() {
            WorkItemContext budget = new WorkItemContext(
                    List.of(), List.of(), new BigDecimal("4"), false, false,
                    false, true, false, Priority.MEDIUM, null);
            WorkItemContext production = new WorkItemContext(
                    List.of(), List.of(), new BigDecimal("4"), false, false,
                    true, false, false, Priority.MEDIUM, null);

            CandidateFeatures mid = person(EmployeeLevel.MID, Map.of());
            assertThat(filter.evaluate(mid, budget, true))
                    .contains(ExclusionReason.LEVEL_RESTRICTION_FINANCIAL);
            assertThat(filter.evaluate(mid, production, true)).isEmpty();
        }
    }

    @Test
    @DisplayName("excluded candidates are kept with their reason, not silently dropped")
    void exclusionsArePreservedForExplainability() {
        CandidateFeatures intern = person(EmployeeLevel.INTERN, Map.of());
        CandidateFeatures senior = person(EmployeeLevel.SENIOR, Map.of());

        WorkItemContext production = new WorkItemContext(
                List.of(), List.of(), new BigDecimal("4"), false, false,
                true, false, false, Priority.MEDIUM, null);

        EligibilityFilter.Outcome outcome = filter.apply(List.of(intern, senior), production, true);

        assertThat(outcome.eligible()).containsExactly(senior);
        assertThat(outcome.excluded())
                .containsEntry(intern, ExclusionReason.LEVEL_RESTRICTION_PRODUCTION_ACCESS);
    }

    @Test
    void everyLevelRestrictionReasonIsIdentifiableAsOne() {
        assertThat(ExclusionReason.LEVEL_RESTRICTION_PRODUCTION_ACCESS.isLevelRestriction()).isTrue();
        assertThat(ExclusionReason.SEVERELY_OVERLOADED.isLevelRestriction()).isFalse();
    }
}
