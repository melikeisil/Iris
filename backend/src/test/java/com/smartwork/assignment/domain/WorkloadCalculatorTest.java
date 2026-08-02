package com.smartwork.assignment.domain;

import com.smartwork.workitem.domain.WorkItemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Workload rules from docs/08 §6, at their boundaries. */
class WorkloadCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-08-01T09:00:00Z");

    private static WorkloadCalculator.OpenItem active(WorkItemType type, String remainingHours) {
        return new WorkloadCalculator.OpenItem(
                type, null, new BigDecimal(remainingHours), null, true, false, null);
    }

    private static WorkloadCalculator.OpenItem queued(WorkItemType type, String estimatedHours) {
        return new WorkloadCalculator.OpenItem(
                type, estimatedHours == null ? null : new BigDecimal(estimatedHours),
                null, null, false, true, null);
    }

    @Test
    @DisplayName("five 1-hour tasks and five 40-hour tasks are not the same load")
    void workloadUsesHoursNotCount() {
        List<WorkloadCalculator.OpenItem> small = Collections.nCopies(5, active(WorkItemType.TASK, "1"));
        List<WorkloadCalculator.OpenItem> large = Collections.nCopies(5, active(WorkItemType.TASK, "40"));

        BigDecimal smallHours = WorkloadCalculator.numerator(small, 0, BigDecimal.ZERO, null, NOW);
        BigDecimal largeHours = WorkloadCalculator.numerator(large, 0, BigDecimal.ZERO, null, NOW);

        assertThat(smallHours).isEqualByComparingTo("5.00");
        assertThat(largeHours).isEqualByComparingTo("200.00");
        assertThat(largeHours).isGreaterThan(smallHours);
    }

    @Test
    @DisplayName("missing estimates resolve by work item type")
    void workloadFallbackEstimates() {
        List<WorkloadCalculator.OpenItem> items = List.of(
                queued(WorkItemType.SUBTASK, null),        // 2
                queued(WorkItemType.TASK, null),           // 4
                queued(WorkItemType.BUG, null),            // 6
                queued(WorkItemType.TEST, null),           // 3
                queued(WorkItemType.DOCUMENTATION, null),  // 3
                queued(WorkItemType.RESEARCH, null),       // 8
                queued(WorkItemType.TECHNICAL_DEBT, null));// 6

        assertThat(WorkloadCalculator.numerator(items, 0, BigDecimal.ZERO, null, NOW))
                .isEqualByComparingTo("32.00");
    }

    @Test
    void storyPointsConvertWhenThereIsNoHourEstimate() {
        WorkloadCalculator.OpenItem pointed = new WorkloadCalculator.OpenItem(
                WorkItemType.STORY, null, null, 5, false, true, null);
        // 5 points x 2.5 default hours per point
        assertThat(WorkloadCalculator.numerator(List.of(pointed), 0, BigDecimal.ZERO, null, NOW))
                .isEqualByComparingTo("12.50");
    }

    @Test
    @DisplayName("containers contribute nothing — their children are already counted")
    void epicsAndFeaturesDoNotDoubleCount() {
        List<WorkloadCalculator.OpenItem> items = List.of(
                queued(WorkItemType.EPIC, null),
                queued(WorkItemType.FEATURE, null),
                queued(WorkItemType.TASK, "4"));

        assertThat(WorkloadCalculator.numerator(items, 0, BigDecimal.ZERO, null, NOW))
                .isEqualByComparingTo("4.00");
    }

    @Test
    @DisplayName("items blocked over 24h count at 30%, not 0%")
    void blockedWorkCountsPartially() {
        WorkloadCalculator.OpenItem longBlocked = new WorkloadCalculator.OpenItem(
                WorkItemType.TASK, null, new BigDecimal("10"), null, true, false,
                NOW.minus(Duration.ofHours(48)));

        assertThat(WorkloadCalculator.numerator(List.of(longBlocked), 0, BigDecimal.ZERO, null, NOW))
                .isEqualByComparingTo("3.00");
    }

    @Test
    void recentlyBlockedWorkStillCountsInFull() {
        // Blocked an hour ago is not yet a discount; the person is still holding it.
        WorkloadCalculator.OpenItem justBlocked = new WorkloadCalculator.OpenItem(
                WorkItemType.TASK, null, new BigDecimal("10"), null, true, false,
                NOW.minus(Duration.ofHours(1)));

        assertThat(WorkloadCalculator.numerator(List.of(justBlocked), 0, BigDecimal.ZERO, null, NOW))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void reviewLoadIsHalfAnHourPerItem() {
        assertThat(WorkloadCalculator.numerator(List.of(), 4, BigDecimal.ZERO, null, NOW))
                .isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("items in review or testing are not counted twice through the numerator")
    void nonActiveNonTodoItemsAreSkipped() {
        WorkloadCalculator.OpenItem inReview = new WorkloadCalculator.OpenItem(
                WorkItemType.TASK, new BigDecimal("8"), null, null, false, false, null);

        assertThat(WorkloadCalculator.numerator(List.of(inReview), 0, BigDecimal.ZERO, null, NOW))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void denominatorAppliesAllocationAndMeetingOverhead() {
        // 40h x 100% x (10/10) x (1 - 0.15) = 34.00
        WorkloadCalculator.CapacityInputs full =
                WorkloadCalculator.CapacityInputs.standard(new BigDecimal("40"), 100);
        assertThat(WorkloadCalculator.denominator(full)).isEqualByComparingTo("34.00");

        // A half-allocated person has half the capacity.
        WorkloadCalculator.CapacityInputs half =
                WorkloadCalculator.CapacityInputs.standard(new BigDecimal("40"), 50);
        assertThat(WorkloadCalculator.denominator(half)).isEqualByComparingTo("17.00");
    }

    @Test
    void leaveReducesCapacity() {
        WorkloadCalculator.CapacityInputs onLeave = new WorkloadCalculator.CapacityInputs(
                new BigDecimal("40"), 100, 10, new BigDecimal("14"),
                WorkloadCalculator.DEFAULT_MEETING_OVERHEAD);
        assertThat(WorkloadCalculator.denominator(onLeave)).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("no remaining capacity means maximally unavailable, not maximally free")
    void zeroCapacityDoesNotLookIdle() {
        WorkloadCalculator.CapacityInputs none = new WorkloadCalculator.CapacityInputs(
                new BigDecimal("40"), 100, 0, BigDecimal.ZERO,
                WorkloadCalculator.DEFAULT_MEETING_OVERHEAD);

        WorkloadCalculator.Result result = WorkloadCalculator.calculate(
                List.of(active(WorkItemType.TASK, "4")), 0, BigDecimal.ZERO, none, null, NOW);

        assertThat(result.ratio()).isEqualByComparingTo(WorkloadCalculator.NO_CAPACITY_RATIO);
        assertThat(result.band()).isEqualTo(WorkloadBand.OVERLOADED);
    }

    @Test
    void bandBoundaries() {
        assertThat(WorkloadBand.of(new BigDecimal("0.49"))).isEqualTo(WorkloadBand.UNDERUTILIZED);
        assertThat(WorkloadBand.of(new BigDecimal("0.50"))).isEqualTo(WorkloadBand.HEALTHY);
        assertThat(WorkloadBand.of(new BigDecimal("0.84"))).isEqualTo(WorkloadBand.HEALTHY);
        assertThat(WorkloadBand.of(new BigDecimal("0.85"))).isEqualTo(WorkloadBand.HIGH);
        assertThat(WorkloadBand.of(new BigDecimal("1.00"))).isEqualTo(WorkloadBand.HIGH);
        assertThat(WorkloadBand.of(new BigDecimal("1.01"))).isEqualTo(WorkloadBand.OVERLOADED);
    }

    @Test
    void endToEndRatio() {
        // 17h of open work against 34h of capacity = 0.50, the bottom of HEALTHY.
        WorkloadCalculator.Result result = WorkloadCalculator.calculate(
                List.of(active(WorkItemType.TASK, "17")), 0, BigDecimal.ZERO,
                WorkloadCalculator.CapacityInputs.standard(new BigDecimal("40"), 100), null, NOW);

        assertThat(result.openHours()).isEqualByComparingTo("17.00");
        assertThat(result.capacityHours()).isEqualByComparingTo("34.00");
        assertThat(result.ratio()).isEqualByComparingTo("0.50");
        assertThat(result.band()).isEqualTo(WorkloadBand.HEALTHY);
    }
}
