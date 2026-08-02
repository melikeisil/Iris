package com.smartwork.process.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateMatcherTest {

    private static final UUID ORG = UUID.randomUUID();

    private static ProcessTemplate template(String code, String name, String keywords) {
        ProcessTemplate template = new ProcessTemplate(ORG, code, name);
        template.setMatchKeywords(keywords);
        return template;
    }

    private static List<ProcessTemplate> catalogue() {
        return List.of(
                template("EMPLOYEE_ONBOARDING", "Employee Onboarding",
                        "onboarding onboard new hire employee starting first day welcome"),
                template("HARDWARE_REQUEST", "Hardware Request",
                        "hardware laptop monitor keyboard device equipment replacement broken"),
                template("PRODUCTION_INCIDENT", "Production Incident",
                        "incident outage down production degraded failing errors emergency"));
    }

    @Test
    void matchesTheOnboardingTemplate() {
        Optional<TemplateMatcher.Match> match = TemplateMatcher.bestMatch(
                "Prepare all onboarding activities for a new employee starting Monday", null, catalogue());

        assertThat(match).isPresent();
        assertThat(match.get().template().getCode()).isEqualTo("EMPLOYEE_ONBOARDING");
    }

    @Test
    void matchesTheHardwareTemplate() {
        Optional<TemplateMatcher.Match> match = TemplateMatcher.bestMatch(
                "My laptop is broken and needs replacement", null, catalogue());

        assertThat(match).isPresent();
        assertThat(match.get().template().getCode()).isEqualTo("HARDWARE_REQUEST");
    }

    @Test
    @DisplayName("an unrelated request matches nothing rather than the least-bad option")
    void unrelatedRequestsDoNotMatch() {
        // "No template matched" is a routing signal in its own right (docs/08 §5.3).
        // Forcing a weak match would hide that signal behind a plausible-looking plan.
        Optional<TemplateMatcher.Match> match = TemplateMatcher.bestMatch(
                "Please review the quarterly architecture proposal document", null, catalogue());

        assertThat(match).isEmpty();
    }

    @Test
    void inactiveTemplatesAreNeverMatched() {
        ProcessTemplate retired = template("EMPLOYEE_ONBOARDING", "Employee Onboarding",
                "onboarding onboard new hire employee starting");
        retired.deactivate();

        assertThat(TemplateMatcher.bestMatch("onboarding for a new hire", null, List.of(retired)))
                .isEmpty();
    }

    @Test
    void descriptionContributesToTheMatch() {
        Optional<TemplateMatcher.Match> match = TemplateMatcher.bestMatch(
                "New starter next week",
                "Please arrange onboarding: welcome pack, first day logistics, hire paperwork.",
                catalogue());

        assertThat(match).isPresent();
        assertThat(match.get().template().getCode()).isEqualTo("EMPLOYEE_ONBOARDING");
    }

    @Test
    @DisplayName("stemming lets 'onboard' match 'onboarding'")
    void crudeStemmingWorksBothWays() {
        assertThat(TemplateMatcher.stem("onboarding")).isEqualTo("onboard");
        assertThat(TemplateMatcher.stem("requests")).isEqualTo("request");
        // Short tokens are left alone; stripping them would create spurious collisions.
        assertThat(TemplateMatcher.stem("das")).isEqualTo("das");
    }

    @Test
    void stopWordsAreIgnored() {
        assertThat(TemplateMatcher.tokenize("the a for to of and is are"))
                .as("common words carry no matching signal and would inflate every score")
                .isEmpty();
    }

    @Test
    void emptyInputMatchesNothing() {
        assertThat(TemplateMatcher.bestMatch("", null, catalogue())).isEmpty();
        assertThat(TemplateMatcher.bestMatch("   ", null, catalogue())).isEmpty();
    }

    @Test
    void confidenceMeasuresCoverageOfTheTemplateVocabulary() {
        // A long request containing one strong keyword should still match: the question is
        // "how much of what defines this template is present", not "how much of what the
        // user typed did we recognize".
        // The template's own name is folded into its keyword set, so "Test" contributes a
        // fifth token here: 2 of 5 matched, not 2 of 4.
        TemplateMatcher.Match match = TemplateMatcher.score(
                template("T", "Test", "alpha beta gamma delta"),
                TemplateMatcher.tokenize("alpha beta"));

        assertThat(match.confidence()).isEqualTo(0.4);
        assertThat(match.matchedKeywords()).containsExactlyInAnyOrder("alpha", "beta");
    }
}
