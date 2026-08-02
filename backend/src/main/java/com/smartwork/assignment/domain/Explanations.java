package com.smartwork.assignment.domain;

import com.smartwork.assignment.domain.ScoreFunctions.ComponentScores;

import java.util.Comparator;
import java.util.List;

/**
 * Explanations are generated from a template, not by an LLM (docs/08 §4.9).
 *
 * <p>Three reasons, all of which matter more than the prose quality: it is deterministic,
 * it is instant, and it is <em>always consistent with the numbers printed beside it</em>.
 * An LLM-written explanation can drift from the score it is explaining, and a candidate
 * ranking that explains itself incorrectly is worse than one that does not explain itself
 * at all.
 */
public final class Explanations {

    private static final int STRONG = 80;
    private static final int WEAK = 40;

    private Explanations() {
    }

    /** Names the two strongest and the single weakest contributing component. */
    public static String forCandidate(ComponentScores components) {
        List<ScoreComponent> byStrength = java.util.Arrays.stream(ScoreComponent.values())
                .sorted(Comparator.comparingInt(components::valueOf).reversed())
                .toList();

        ScoreComponent best = byStrength.get(0);
        ScoreComponent second = byStrength.get(1);
        ScoreComponent worst = byStrength.get(byStrength.size() - 1);

        StringBuilder sb = new StringBuilder();
        sb.append(strengthWord(components.valueOf(best)))
                .append(' ').append(best.key())
                .append(" and ").append(second.key())
                .append(" match");

        if (components.valueOf(worst) < WEAK) {
            sb.append("; limited by ").append(worst.key())
                    .append(" (").append(components.valueOf(worst)).append(")");
        }
        return sb.append('.').toString();
    }

    /** Why a candidate was ruled out entirely, in the reviewer's words rather than a code. */
    public static String forExclusion(ExclusionReason reason) {
        return reason.explanation();
    }

    private static String strengthWord(int score) {
        if (score >= STRONG) {
            return "Strong";
        }
        if (score >= WEAK) {
            return "Moderate";
        }
        return "Weak";
    }
}
