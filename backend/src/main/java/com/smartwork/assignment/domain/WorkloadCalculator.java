package com.smartwork.assignment.domain;

import com.smartwork.workitem.domain.WorkItemType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Workload as hours, never task count (docs/08 §6).
 *
 * <p>The brief is explicit and it is right: five one-hour tasks and five forty-hour tasks
 * are not the same load. {@code WorkloadUsesHoursNotCountTest} is the regression test for
 * the day someone "simplifies" this into a {@code COUNT(*)}.
 *
 * <p>Pure arithmetic over a supplied snapshot - no repository, no clock lookup, no Spring.
 * Same reasoning as the workflow guards in Phase 3: the interesting rules are exactly the
 * ones worth unit-testing without a database.
 */
public final class WorkloadCalculator {

    /** Awaiting-review items are real attention, but not a full task's worth. */
    public static final BigDecimal REVIEW_LOAD_HOURS_PER_ITEM = new BigDecimal("0.5");

    /**
     * A long-blocked item counts at 30%, not 0%. It still occupies attention and it will
     * come back - discounting it to nothing is how a "free" person gets loaded up right
     * before three blocked items unblock at once.
     */
    public static final BigDecimal BLOCKED_WEIGHT = new BigDecimal("0.30");

    public static final Duration BLOCKED_DISCOUNT_AFTER = Duration.ofHours(24);

    /** Default meeting/ceremony overhead as a fraction of raw capacity. */
    public static final BigDecimal DEFAULT_MEETING_OVERHEAD = new BigDecimal("0.15");

    /** Working days in the standard two-week planning window. */
    public static final int WINDOW_WORKING_DAYS = 10;

    /**
     * Ratio returned when capacity is zero or negative. Not {@link BigDecimal#ZERO}: a
     * person with no remaining capacity is maximally unavailable, not maximally free.
     */
    public static final BigDecimal NO_CAPACITY_RATIO = new BigDecimal("9.99");

    private WorkloadCalculator() {
    }

    /** One open item held by the user, as the numerator sees it. */
    public record OpenItem(
            WorkItemType type,
            BigDecimal estimatedHours,
            BigDecimal remainingHours,
            Integer storyPoints,
            boolean activeWork,
            boolean todoCategory,
            Instant blockedSince) {
    }

    /** Everything the denominator needs. */
    public record CapacityInputs(
            BigDecimal weeklyCapacityHours,
            int allocationPercent,
            int remainingWorkingDays,
            BigDecimal leaveHours,
            BigDecimal meetingOverheadFraction) {

        public static CapacityInputs standard(BigDecimal weeklyCapacityHours, int allocationPercent) {
            return new CapacityInputs(
                    weeklyCapacityHours, allocationPercent, WINDOW_WORKING_DAYS,
                    BigDecimal.ZERO, DEFAULT_MEETING_OVERHEAD);
        }
    }

    public record Result(
            BigDecimal openHours,
            BigDecimal capacityHours,
            BigDecimal ratio,
            WorkloadBand band) {
    }

    public static Result calculate(
            List<OpenItem> openItems,
            int itemsAwaitingThisUsersReview,
            BigDecimal supportDutyHours,
            CapacityInputs capacity,
            BigDecimal hoursPerPoint,
            Instant now) {

        BigDecimal openHours = numerator(
                openItems, itemsAwaitingThisUsersReview, supportDutyHours, hoursPerPoint, now);
        BigDecimal capacityHours = denominator(capacity);

        BigDecimal ratio = capacityHours.signum() <= 0
                ? NO_CAPACITY_RATIO
                : openHours.divide(capacityHours, 2, RoundingMode.HALF_UP);

        return new Result(openHours, capacityHours, ratio, WorkloadBand.of(ratio));
    }

    static BigDecimal numerator(
            List<OpenItem> openItems,
            int itemsAwaitingThisUsersReview,
            BigDecimal supportDutyHours,
            BigDecimal hoursPerPoint,
            Instant now) {

        BigDecimal total = BigDecimal.ZERO;

        for (OpenItem item : openItems) {
            if (!item.activeWork() && !item.todoCategory()) {
                // Neither in flight nor queued (review, testing, waiting-external). Those
                // are counted through reviewLoad or not at all; adding them here as well
                // would double-count the same work.
                continue;
            }

            // Active work is measured by what is left; a queued item by its whole estimate.
            BigDecimal hours = item.activeWork()
                    ? remainingOrEstimated(item, hoursPerPoint)
                    : EffortEstimates.resolve(item.type(), item.estimatedHours(), item.storyPoints(), hoursPerPoint);

            if (isLongBlocked(item, now)) {
                hours = hours.multiply(BLOCKED_WEIGHT);
            }
            total = total.add(hours);
        }

        total = total.add(REVIEW_LOAD_HOURS_PER_ITEM.multiply(BigDecimal.valueOf(itemsAwaitingThisUsersReview)));
        if (supportDutyHours != null) {
            total = total.add(supportDutyHours);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal remainingOrEstimated(OpenItem item, BigDecimal hoursPerPoint) {
        if (item.remainingHours() != null && item.remainingHours().signum() >= 0) {
            return item.remainingHours();
        }
        return EffortEstimates.resolve(item.type(), item.estimatedHours(), item.storyPoints(), hoursPerPoint);
    }

    private static boolean isLongBlocked(OpenItem item, Instant now) {
        return item.blockedSince() != null
                && Duration.between(item.blockedSince(), now).compareTo(BLOCKED_DISCOUNT_AFTER) > 0;
    }

    static BigDecimal denominator(CapacityInputs capacity) {
        BigDecimal allocation = BigDecimal.valueOf(capacity.allocationPercent())
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        BigDecimal windowFraction = BigDecimal.valueOf(Math.max(0, capacity.remainingWorkingDays()))
                .divide(BigDecimal.valueOf(WINDOW_WORKING_DAYS), 4, RoundingMode.HALF_UP);

        BigDecimal raw = capacity.weeklyCapacityHours()
                .multiply(allocation)
                .multiply(windowFraction);

        BigDecimal overheadFraction = capacity.meetingOverheadFraction() != null
                ? capacity.meetingOverheadFraction()
                : DEFAULT_MEETING_OVERHEAD;

        BigDecimal afterOverhead = raw.multiply(BigDecimal.ONE.subtract(overheadFraction));

        BigDecimal leave = capacity.leaveHours() != null ? capacity.leaveHours() : BigDecimal.ZERO;
        return afterOverhead.subtract(leave).setScale(2, RoundingMode.HALF_UP);
    }
}
