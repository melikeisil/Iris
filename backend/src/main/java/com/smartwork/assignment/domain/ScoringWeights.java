package com.smartwork.assignment.domain;

import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Component weights, resolved most-specific-first: team, department, organization,
 * built-in defaults (docs/08 §8).
 *
 * <p>The sum must be exactly 1.0. Weights that sum to 0.9 do not "scale down evenly" -
 * they silently compress every score toward zero and make the auto-assign threshold
 * unreachable, which presents as "auto-assign stopped working" with no error anywhere.
 * {@code WeightSumValidationTest} covers the rejection.
 */
public final class ScoringWeights {

    /** Tolerance for the sum check, since JSONB round-trips decimals. */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.0001");

    private final Map<ScoreComponent, BigDecimal> weights;

    private ScoringWeights(Map<ScoreComponent, BigDecimal> weights) {
        this.weights = weights;
    }

    public static ScoringWeights defaults() {
        Map<ScoreComponent, BigDecimal> map = new EnumMap<>(ScoreComponent.class);
        for (ScoreComponent component : ScoreComponent.values()) {
            map.put(component, component.defaultWeight());
        }
        return new ScoringWeights(map);
    }

    /**
     * Builds from a partial override map. Components absent from {@code overrides} keep
     * their default weight, so a policy that only wants to raise {@code fairness} does not
     * have to restate the other six - but the resulting total is still validated.
     */
    public static ScoringWeights of(Map<String, BigDecimal> overrides) {
        Map<ScoreComponent, BigDecimal> map = new EnumMap<>(ScoreComponent.class);
        for (ScoreComponent component : ScoreComponent.values()) {
            map.put(component, component.defaultWeight());
        }
        if (overrides != null) {
            for (Map.Entry<String, BigDecimal> entry : overrides.entrySet()) {
                ScoreComponent component = byKey(entry.getKey());
                if (entry.getValue() == null || entry.getValue().signum() < 0) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED,
                            "Weight for '" + entry.getKey() + "' must be zero or positive");
                }
                map.put(component, entry.getValue());
            }
        }
        validateSum(map);
        return new ScoringWeights(map);
    }

    private static ScoreComponent byKey(String key) {
        for (ScoreComponent component : ScoreComponent.values()) {
            if (component.key().equalsIgnoreCase(key)) {
                return component;
            }
        }
        throw new ApiException(ErrorCode.VALIDATION_FAILED,
                "Unknown score component '" + key + "'. Permitted: " + ScoreComponent.componentNames());
    }

    private static void validateSum(Map<ScoreComponent, BigDecimal> map) {
        BigDecimal sum = map.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.subtract(BigDecimal.ONE).abs().compareTo(TOLERANCE) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Score component weights must sum to 1.0 but sum to " + sum.setScale(4, RoundingMode.HALF_UP)
                            + ". Weights that do not sum to one silently compress every candidate's "
                            + "total score and make the auto-assign threshold unreachable.");
        }
    }

    public BigDecimal weightOf(ScoreComponent component) {
        return weights.get(component);
    }

    public Map<String, BigDecimal> asMap() {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        weights.forEach((component, weight) -> out.put(component.key(), weight));
        return out;
    }
}
