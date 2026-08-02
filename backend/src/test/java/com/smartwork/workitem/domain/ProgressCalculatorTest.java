package com.smartwork.workitem.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Parent progress rollup")
class ProgressCalculatorTest {

    @Test
    void aTicketWeightsChildrenByEstimatedHours() {
        // 0.5h account creation done, four 8h laptop builds outstanding. Counting children
        // would say 20% and mislead the requester; weighting says 1.5%.
        var children = List.of(
                done(hours("0.5")),
                open(hours("8")), open(hours("8")), open(hours("8")), open(hours("8")));

        var result = ProgressCalculator.calculate(WorkItemType.REQUEST, children);

        assertThat(result.strategy()).isEqualTo(ProgressCalculator.Strategy.ESTIMATE_WEIGHTED);
        assertThat(result.percent()).isEqualByComparingTo(new BigDecimal("1.54"));
    }

    @Test
    void anEpicWeightsChildrenByStoryPoints() {
        var children = List.of(points(8, true), points(5, true), points(13, false));

        var result = ProgressCalculator.calculate(WorkItemType.EPIC, children);

        assertThat(result.strategy()).isEqualTo(ProgressCalculator.Strategy.STORY_POINT_WEIGHTED);
        assertThat(result.percent()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void aStoryCountsItsChildren() {
        var children = List.of(done(null), done(null), open(null), open(null));

        var result = ProgressCalculator.calculate(WorkItemType.STORY, children);

        assertThat(result.strategy()).isEqualTo(ProgressCalculator.Strategy.COUNT_BASED);
        assertThat(result.percent()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void fallsBackToCountingWhenTooManyEstimatesAreMissing() {
        // A weighted number computed from three missing estimates out of four is
        // confidently wrong; counting is honest about knowing less.
        var children = List.of(done(hours("4")), open(null), open(null), open(null));

        var result = ProgressCalculator.calculate(WorkItemType.REQUEST, children);

        assertThat(result.strategy()).isEqualTo(ProgressCalculator.Strategy.COUNT_BASED);
        assertThat(result.percent()).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    void cancelledChildrenAreExcludedEntirely() {
        var children = List.of(done(hours("4")), cancelled(hours("100")));

        var result = ProgressCalculator.calculate(WorkItemType.REQUEST, children);

        // Cancelled work must not make the parent look less complete than it is.
        assertThat(result.percent()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void blockedChildrenCountAsIncompleteAndAreFlagged() {
        var children = List.of(done(hours("4")), blocked(hours("4")));

        var result = ProgressCalculator.calculate(WorkItemType.REQUEST, children);

        assertThat(result.percent()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(result.hasBlockedChildren()).isTrue();
    }

    @Test
    void aParentWithNoChildrenIsZeroPercent() {
        var result = ProgressCalculator.calculate(WorkItemType.REQUEST, List.of());

        assertThat(result.percent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.hasBlockedChildren()).isFalse();
    }

    @Test
    void allChildrenDoneIsExactlyOneHundred() {
        var children = List.of(done(hours("3")), done(hours("7")));

        assertThat(ProgressCalculator.calculate(WorkItemType.REQUEST, children).percent())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    // ------------------------------------------------------------- helpers

    private static BigDecimal hours(String value) {
        return new BigDecimal(value);
    }

    private static ProgressCalculator.Child done(BigDecimal estimatedHours) {
        return new ProgressCalculator.Child(true, false, false, estimatedHours, null);
    }

    private static ProgressCalculator.Child open(BigDecimal estimatedHours) {
        return new ProgressCalculator.Child(false, false, false, estimatedHours, null);
    }

    private static ProgressCalculator.Child blocked(BigDecimal estimatedHours) {
        return new ProgressCalculator.Child(false, false, true, estimatedHours, null);
    }

    private static ProgressCalculator.Child cancelled(BigDecimal estimatedHours) {
        return new ProgressCalculator.Child(false, true, false, estimatedHours, null);
    }

    private static ProgressCalculator.Child points(int storyPoints, boolean complete) {
        return new ProgressCalculator.Child(complete, false, false, null, (short) storyPoints);
    }
}
