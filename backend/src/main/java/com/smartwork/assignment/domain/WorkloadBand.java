package com.smartwork.assignment.domain;

import java.math.BigDecimal;

/**
 * Workload bands from docs/08 §6.
 *
 * <p>Surfaced per person to their Team Lead - a capacity signal they are expected to act
 * on - and only in aggregate above that level. A per-person workload number visible to
 * the whole organization stops being a planning input and starts being a scoreboard.
 */
public enum WorkloadBand {

    UNDERUTILIZED,
    HEALTHY,
    HIGH,
    OVERLOADED;

    private static final BigDecimal HEALTHY_FLOOR = new BigDecimal("0.50");
    private static final BigDecimal HIGH_FLOOR = new BigDecimal("0.85");
    private static final BigDecimal OVERLOADED_FLOOR = BigDecimal.ONE;

    public static WorkloadBand of(BigDecimal ratio) {
        if (ratio.compareTo(HEALTHY_FLOOR) < 0) {
            return UNDERUTILIZED;
        }
        if (ratio.compareTo(HIGH_FLOOR) < 0) {
            return HEALTHY;
        }
        if (ratio.compareTo(OVERLOADED_FLOOR) <= 0) {
            return HIGH;
        }
        return OVERLOADED;
    }
}
