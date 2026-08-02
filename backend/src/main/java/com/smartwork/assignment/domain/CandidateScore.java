package com.smartwork.assignment.domain;

import java.util.List;
import java.util.UUID;

/** A scored, ranked candidate — the shape persisted into {@code recommendation_candidates}. */
public record CandidateScore(
        UUID candidateUserId,
        String fullName,
        int rank,
        int totalScore,
        ScoreFunctions.ComponentScores components,
        java.math.BigDecimal workloadRatio,
        String explanation) {

    /**
     * The closed list of scoring inputs, asserted by
     * {@code NoProtectedCharacteristicsTest}. Delegates to {@link ScoreComponent} so there
     * is exactly one place to change - and changing it fails that test on purpose.
     */
    public static List<String> componentNames() {
        return ScoreComponent.componentNames();
    }
}
