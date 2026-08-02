package com.smartwork.assignment.domain;

import com.smartwork.assignment.domain.CandidateFeatures.WorkItemContext;
import com.smartwork.assignment.domain.ScoreFunctions.ComponentScores;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Combines the seven components into a total and ranks the pool (docs/08 §4).
 *
 * <p>Pure: takes eligible candidates and weights, returns ranked scores. Eligibility
 * filtering happens before this ({@link EligibilityFilter}) and routing happens after it
 * (Phase 6), so this class only ever answers "who fits best", never "who gets it".
 */
public final class ScoringEngine {

    /** docs/08 §4.9: the top three are stored, the rest are recorded but not ranked. */
    public static final int MAX_RANKED_CANDIDATES = 3;

    private final ScoringWeights weights;

    public ScoringEngine(ScoringWeights weights) {
        this.weights = weights;
    }

    public List<CandidateScore> rank(List<CandidateFeatures> eligible, WorkItemContext item) {
        if (eligible.isEmpty()) {
            return List.of();
        }

        int poolMedian = ScoreFunctions.medianAssigned(
                eligible.stream().map(CandidateFeatures::assignedLast14Days).toList());

        List<CandidateScore> scored = new ArrayList<>();
        for (CandidateFeatures candidate : eligible) {
            ComponentScores components = ScoreFunctions.allComponents(candidate, item, poolMedian);
            int total = weightedTotal(components);
            scored.add(new CandidateScore(
                    candidate.userId(),
                    candidate.fullName(),
                    0, // assigned below
                    total,
                    components,
                    candidate.workloadRatio(),
                    Explanations.forCandidate(components)));
        }

        // Deterministic ordering: score first, then userId. Without the tiebreaker, two
        // candidates on identical scores could swap places between runs, which would make
        // the "same inputs reproduce the same decision" guarantee (docs/08 §9) false.
        scored.sort(Comparator
                .comparingInt(CandidateScore::totalScore).reversed()
                .thenComparing(c -> c.candidateUserId().toString()));

        List<CandidateScore> ranked = new ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            CandidateScore c = scored.get(i);
            ranked.add(new CandidateScore(
                    c.candidateUserId(), c.fullName(), i + 1, c.totalScore(),
                    c.components(), c.workloadRatio(), c.explanation()));
        }
        return ranked;
    }

    /** The top three, which is what gets persisted with a rank. */
    public List<CandidateScore> topThree(List<CandidateScore> ranked) {
        return ranked.stream().limit(MAX_RANKED_CANDIDATES).toList();
    }

    int weightedTotal(ComponentScores components) {
        BigDecimal total = BigDecimal.ZERO;
        for (ScoreComponent component : ScoreComponent.values()) {
            total = total.add(weights.weightOf(component)
                    .multiply(BigDecimal.valueOf(components.valueOf(component))));
        }
        return ScoreFunctions.clamp(total.setScale(0, RoundingMode.HALF_UP).intValue());
    }

    public ScoringWeights weights() {
        return weights;
    }
}
