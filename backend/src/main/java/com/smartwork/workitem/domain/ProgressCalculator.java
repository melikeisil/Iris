package com.smartwork.workitem.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Parent progress from child work items.
 *
 * <p>Three strategies, chosen by parent type. Weighting matters: a ticket whose five tasks
 * are a 30-minute account creation and four 8-hour laptop builds is not 20% done when the
 * account exists, and telling a requester it is destroys their trust in the number.
 */
public final class ProgressCalculator {

    /** Below this share of children carrying the weighting field, fall back to counting. */
    private static final double MIN_WEIGHTED_COVERAGE = 0.70;

    private ProgressCalculator() {
    }

    public static Result calculate(WorkItemType parentType, List<Child> children) {
        List<Child> counted = children.stream().filter(child -> !child.cancelled()).toList();

        if (counted.isEmpty()) {
            return new Result(BigDecimal.ZERO, Strategy.COUNT_BASED, false);
        }

        boolean hasBlockedChildren = counted.stream().anyMatch(Child::blocked);
        Strategy strategy = strategyFor(parentType, counted);

        BigDecimal percent = switch (strategy) {
            case ESTIMATE_WEIGHTED -> weighted(counted, Child::estimatedHours);
            case STORY_POINT_WEIGHTED -> weighted(counted,
                    child -> child.storyPoints() == null ? null : new BigDecimal(child.storyPoints()));
            case COUNT_BASED -> countBased(counted);
        };

        return new Result(percent, strategy, hasBlockedChildren);
    }

    private static Strategy strategyFor(WorkItemType parentType, List<Child> children) {
        Strategy preferred = switch (parentType) {
            case REQUEST, INCIDENT -> Strategy.ESTIMATE_WEIGHTED;
            case EPIC, FEATURE -> Strategy.STORY_POINT_WEIGHTED;
            default -> Strategy.COUNT_BASED;
        };

        // A weighted strategy on mostly-missing weights produces a confidently wrong
        // number; counting is honest about knowing less.
        return switch (preferred) {
            case ESTIMATE_WEIGHTED -> coverage(children, child -> child.estimatedHours() != null)
                    >= MIN_WEIGHTED_COVERAGE ? preferred : Strategy.COUNT_BASED;
            case STORY_POINT_WEIGHTED -> coverage(children, child -> child.storyPoints() != null)
                    >= MIN_WEIGHTED_COVERAGE ? preferred : Strategy.COUNT_BASED;
            case COUNT_BASED -> Strategy.COUNT_BASED;
        };
    }

    private static double coverage(List<Child> children, java.util.function.Predicate<Child> present) {
        long withValue = children.stream().filter(present).count();
        return (double) withValue / children.size();
    }

    private static BigDecimal weighted(List<Child> children,
                                       java.util.function.Function<Child, BigDecimal> weight) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal done = BigDecimal.ZERO;

        for (Child child : children) {
            BigDecimal value = weight.apply(child);
            if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            total = total.add(value);
            if (child.done()) {
                done = done.add(value);
            }
        }
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return countBased(children);
        }
        return percent(done, total);
    }

    private static BigDecimal countBased(List<Child> children) {
        long done = children.stream().filter(Child::done).count();
        return percent(BigDecimal.valueOf(done), BigDecimal.valueOf(children.size()));
    }

    private static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        return numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    public enum Strategy {
        COUNT_BASED,
        ESTIMATE_WEIGHTED,
        STORY_POINT_WEIGHTED
    }

    /**
     * @param done      the child is in a terminal-success state
     * @param cancelled excluded from both numerator and denominator - cancelled work
     *                  should not make a parent look less complete than it is
     * @param blocked   still incomplete, but surfaced separately on the parent card
     */
    public record Child(boolean done, boolean cancelled, boolean blocked,
                        BigDecimal estimatedHours, Short storyPoints) {
    }

    public record Result(BigDecimal percent, Strategy strategy, boolean hasBlockedChildren) {
    }
}
