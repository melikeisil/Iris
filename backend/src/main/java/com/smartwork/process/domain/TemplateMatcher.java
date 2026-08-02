package com.smartwork.process.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Picks the process template an incoming request matches, deterministically.
 *
 * <p>Two candidate designs were available: ask the LLM which template applies, or score
 * keyword overlap in Java. This is the Java one, for a reason worth stating — the AI
 * <em>also</em> returns a {@code matchedProcessTemplate} suggestion, and having a
 * deterministic matcher means that suggestion can be <em>checked</em> rather than trusted.
 * When the two agree, confidence is high; when they disagree, that disagreement is itself a
 * signal worth routing to a human.
 *
 * <p>Pure and unit-testable: no repository, no Spring.
 */
public final class TemplateMatcher {

    /**
     * Below this, "no template matched" is the more honest answer than a weak guess.
     *
     * <p>0.30 rather than something higher because coverage is measured against the
     * template's <em>whole</em> keyword list, and a real request rarely restates every term
     * that defines a process. "My laptop is broken and needs replacement" hits three of the
     * nine hardware keywords — unmistakable to a human, and 0.33 to this function.
     */
    public static final double MIN_CONFIDENCE = 0.30;

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "for", "to", "of", "in", "on", "at", "and", "or", "is", "are",
            "be", "please", "need", "needs", "want", "wants", "would", "like", "new", "all",
            "my", "our", "we", "i", "can", "could", "should", "with", "from", "this", "that");

    private TemplateMatcher() {
    }

    public record Match(ProcessTemplate template, double confidence, List<String> matchedKeywords) {
    }

    /**
     * Scores every active template against the request text and returns the best, or empty
     * when nothing clears {@link #MIN_CONFIDENCE}.
     */
    public static Optional<Match> bestMatch(String summary, String description, List<ProcessTemplate> templates) {
        Set<String> requestTokens = tokenize(summary + " " + (description == null ? "" : description));
        if (requestTokens.isEmpty()) {
            return Optional.empty();
        }

        Match best = null;
        for (ProcessTemplate template : templates) {
            if (!template.isActive()) {
                continue;
            }
            Match match = score(template, requestTokens);
            if (match.confidence() >= MIN_CONFIDENCE
                    && (best == null || match.confidence() > best.confidence())) {
                best = match;
            }
        }
        return Optional.ofNullable(best);
    }

    static Match score(ProcessTemplate template, Set<String> requestTokens) {
        Set<String> keywords = tokenize(
                (template.getMatchKeywords() == null ? "" : template.getMatchKeywords())
                        + " " + template.getName());

        if (keywords.isEmpty()) {
            return new Match(template, 0.0, List.of());
        }

        List<String> matched = new ArrayList<>();
        for (String keyword : keywords) {
            if (requestTokens.contains(keyword)) {
                matched.add(keyword);
            }
        }

        // Coverage of the template's vocabulary, not of the request's. A long request that
        // happens to contain "onboarding" should still match the onboarding template - the
        // question is "how much of what defines this template is present", not "how much of
        // what the user typed did we recognize".
        double confidence = (double) matched.size() / keywords.size();
        return new Match(template, confidence, matched);
    }

    static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String[] rawTokens = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : rawTokens) {
            if (token.length() > 2 && !STOP_WORDS.contains(token)) {
                tokens.add(stem(token));
            }
        }
        return tokens;
    }

    /**
     * Crude suffix stripping so "onboarding" matches "onboard" and "requests" matches
     * "request". Not a real stemmer, and deliberately not: a full stemmer would pull in a
     * dependency and a class of surprising matches, to solve a problem that four suffixes
     * cover for an eight-template catalogue.
     */
    static String stem(String token) {
        for (String suffix : Arrays.asList("ing", "ies", "es", "s")) {
            if (token.length() > suffix.length() + 3 && token.endsWith(suffix)) {
                return token.substring(0, token.length() - suffix.length());
            }
        }
        return token;
    }
}
