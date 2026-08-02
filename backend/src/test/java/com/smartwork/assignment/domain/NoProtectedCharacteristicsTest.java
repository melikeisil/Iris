package com.smartwork.assignment.domain;

import com.smartwork.common.error.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * docs/08 §4.8, enforced.
 *
 * <p>The companion database-level test is {@code ProtectedCharacteristicsSchemaIT}, which
 * scans {@code information_schema} and fails the build if such a column is ever added to
 * {@code users}. That one is the meaningful guard — it fails regardless of anyone's intent.
 * This one closes the other half: the scorer's own feature vector.
 */
class NoProtectedCharacteristicsTest {

    private static final Set<String> FORBIDDEN = Set.of(
            "gender", "sex", "age", "birthdate", "birthday", "dateofbirth", "race",
            "ethnicity", "religion", "disability", "political", "maritalstatus",
            "familystatus", "nationality", "pregnancy", "sexualorientation");

    @Test
    @DisplayName("the feature vector contains exactly the seven permitted components")
    void candidateFeatureVectorContainsOnlyPermittedComponents() {
        assertThat(CandidateScore.componentNames()).containsExactlyInAnyOrder(
                "skill", "team", "capacity", "experience", "component", "deadline", "fairness");
    }

    @Test
    @DisplayName("no field of CandidateFeatures names a protected characteristic")
    void candidateFeaturesCarriesNoProtectedAttribute() {
        List<String> offending = Arrays.stream(CandidateFeatures.class.getRecordComponents())
                .map(RecordComponent::getName)
                .filter(NoProtectedCharacteristicsTest::looksProtected)
                .toList();

        assertThat(offending)
                .as("CandidateFeatures is the only thing the scorer can see; a protected "
                        + "attribute reaching it would be scoreable")
                .isEmpty();
    }

    @Test
    void addingAnEighthScoreComponentIsADeliberateAct() {
        // Not a tautology: this is the assertion that fails if someone adds a component to
        // the enum, forcing the change through review rather than letting it slip in as a
        // detail of a larger commit.
        assertThat(ScoreComponent.values()).hasSize(7);
    }

    @Test
    void defaultWeightsSumToOne() {
        BigDecimal sum = Arrays.stream(ScoreComponent.values())
                .map(ScoreComponent::defaultWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("a policy whose weights do not sum to 1.0 is rejected on save")
    void weightSumValidation() {
        assertThatThrownBy(() -> ScoringWeights.of(Map.of(
                "skill", new BigDecimal("0.50"),
                "capacity", new BigDecimal("0.10"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("must sum to 1.0");
    }

    @Test
    void weightsThatDoSumToOneAreAccepted() {
        ScoringWeights weights = ScoringWeights.of(Map.of(
                "skill", new BigDecimal("0.35"),
                "capacity", new BigDecimal("0.20"),
                "team", new BigDecimal("0.15"),
                "experience", new BigDecimal("0.10"),
                "component", new BigDecimal("0.10"),
                "deadline", new BigDecimal("0.08"),
                "fairness", new BigDecimal("0.02")));

        assertThat(weights.weightOf(ScoreComponent.SKILL)).isEqualByComparingTo("0.35");
    }

    @Test
    void anUnknownComponentNameIsRejectedRatherThanIgnored() {
        // Silently ignoring it would let a typo disable a weight override with no signal.
        assertThatThrownBy(() -> ScoringWeights.of(Map.of("seniority", new BigDecimal("1.0"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unknown score component");
    }

    private static boolean looksProtected(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT).replace("_", "");
        return FORBIDDEN.stream().anyMatch(normalized::contains);
    }
}
