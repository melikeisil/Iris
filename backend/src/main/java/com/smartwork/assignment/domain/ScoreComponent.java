package com.smartwork.assignment.domain;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * The seven - and only seven - inputs to a candidate's score (docs/08 §4).
 *
 * <p>This enum is the closed list. {@code NoProtectedCharacteristicsTest} asserts its
 * membership exactly, so adding an eighth component is a deliberate act that fails the
 * build until the test is updated and someone has to justify it in review.
 *
 * <p>Gender, age, race, ethnicity, religion, disability, political affiliation and family
 * status are not "excluded from the formula" - they are absent from the schema entirely.
 * There is no column to accidentally join. See docs/08 §4.8 and ADR-013.
 */
public enum ScoreComponent {

    SKILL("skill", new BigDecimal("0.30"), "coverage and depth against required skills"),
    CAPACITY("capacity", new BigDecimal("0.20"), "how much room the person has"),
    TEAM("team", new BigDecimal("0.15"), "department and team alignment"),
    EXPERIENCE("experience", new BigDecimal("0.10"), "similar completed work"),
    COMPONENT("component", new BigDecimal("0.10"), "experience in the affected components"),
    DEADLINE("deadline", new BigDecimal("0.10"), "can they realistically finish in time"),
    FAIRNESS("fairness", new BigDecimal("0.05"), "recent assignment distribution");

    private final String key;
    private final BigDecimal defaultWeight;
    private final String measures;

    ScoreComponent(String key, BigDecimal defaultWeight, String measures) {
        this.key = key;
        this.defaultWeight = defaultWeight;
        this.measures = measures;
    }

    public String key() {
        return key;
    }

    public BigDecimal defaultWeight() {
        return defaultWeight;
    }

    /** Human-readable phrase used to build explanations without an LLM. */
    public String measures() {
        return measures;
    }

    public static List<String> componentNames() {
        return Arrays.stream(values()).map(ScoreComponent::key).toList();
    }
}
