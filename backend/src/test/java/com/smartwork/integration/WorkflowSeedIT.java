package com.smartwork.integration;

import com.smartwork.support.IntegrationTest;
import com.smartwork.support.PostgresContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The six workflow profiles are data, so their correctness is a data assertion.
 *
 * <p>The last two tests are the load-bearing ones: they verify structurally that review
 * and testing cannot be skipped, which docs/03 promises no actor can do.
 */
@IntegrationTest
@Import(PostgresContainerConfig.class)
@DisplayName("Seeded workflow definitions")
class WorkflowSeedIT {

    private static final Map<String, Integer> EXPECTED_STATE_COUNTS = Map.of(
            "SERVICE", 11, "PROCESS", 9, "DEVELOPMENT", 10,
            "BUG", 9, "RESEARCH", 6, "INCIDENT", 8);

    private static final Map<String, Integer> EXPECTED_TRANSITION_COUNTS = Map.of(
            "SERVICE", 18, "PROCESS", 13, "DEVELOPMENT", 16,
            "BUG", 13, "RESEARCH", 7, "INCIDENT", 10);

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void allSixProfilesAreSeeded() {
        List<String> codes = jdbc.queryForList(
                "SELECT code FROM workflow_definitions WHERE organization_id IS NULL ORDER BY code",
                String.class);

        assertThat(codes).containsExactly("BUG", "DEVELOPMENT", "INCIDENT", "PROCESS", "RESEARCH", "SERVICE");
    }

    @Test
    void eachProfileHasTheExpectedStatesAndTransitions() {
        EXPECTED_STATE_COUNTS.forEach((code, expected) -> {
            Integer actual = jdbc.queryForObject("""
                    SELECT count(*) FROM workflow_states s
                      JOIN workflow_definitions d ON d.id = s.workflow_definition_id
                     WHERE d.code = ? AND d.organization_id IS NULL
                    """, Integer.class, code);
            assertThat(actual).as("states in %s", code).isEqualTo(expected);
        });

        EXPECTED_TRANSITION_COUNTS.forEach((code, expected) -> {
            Integer actual = jdbc.queryForObject("""
                    SELECT count(*) FROM workflow_transitions t
                      JOIN workflow_definitions d ON d.id = t.workflow_definition_id
                     WHERE d.code = ? AND d.organization_id IS NULL
                    """, Integer.class, code);
            assertThat(actual).as("transitions in %s", code).isEqualTo(expected);
        });
    }

    @Test
    void everyTransitionPointsAtRealStates() {
        Integer dangling = jdbc.queryForObject("""
                SELECT count(*)
                  FROM workflow_transitions t
                 WHERE NOT EXISTS (SELECT 1 FROM workflow_states s
                                    WHERE s.workflow_definition_id = t.workflow_definition_id
                                      AND s.code = t.from_state)
                    OR NOT EXISTS (SELECT 1 FROM workflow_states s
                                    WHERE s.workflow_definition_id = t.workflow_definition_id
                                      AND s.code = t.to_state)
                """, Integer.class);

        assertThat(dangling).as("transitions referencing a state that does not exist").isZero();
    }

    @Test
    void everyProfileHasExactlyOneInitialStateMatchingItsDeclaredEntryPoint() {
        Integer mismatched = jdbc.queryForObject("""
                SELECT count(*) FROM workflow_definitions d
                 WHERE NOT EXISTS (SELECT 1 FROM workflow_states s
                                    WHERE s.workflow_definition_id = d.id
                                      AND s.is_initial
                                      AND s.code = d.initial_state)
                """, Integer.class);

        assertThat(mismatched).isZero();
    }

    @Test
    void developmentWorkCannotSkipReviewAndTesting() {
        // The guarantee is structural: no edge exists, so no actor - not even a Manager -
        // can move work straight from IN_PROGRESS to DONE.
        Integer shortcut = jdbc.queryForObject("""
                SELECT count(*) FROM workflow_transitions t
                  JOIN workflow_definitions d ON d.id = t.workflow_definition_id
                 WHERE d.code = 'DEVELOPMENT' AND t.from_state = 'IN_PROGRESS' AND t.to_state = 'DONE'
                """, Integer.class);

        assertThat(shortcut).as("there must be no IN_PROGRESS -> DONE edge").isZero();
    }

    @Test
    void reviewApprovalIsGuardedAgainstSelfReview() {
        List<String> guards = jdbc.queryForList("""
                SELECT t.guards FROM workflow_transitions t
                  JOIN workflow_definitions d ON d.id = t.workflow_definition_id
                 WHERE t.transition_code IN ('APPROVE_REVIEW', 'PASS_QA', 'QA_PASSED',
                                             'ACCEPT_FINDINGS', 'CONFIRM_RESOLUTION')
                """, String.class);

        assertThat(guards).isNotEmpty();
        assertThat(guards).allSatisfy(guard ->
                assertThat(guard).contains("NOT_SELF_REVIEW"));
    }

    @Test
    void blockingAlwaysRequiresAReason() {
        List<String> guards = jdbc.queryForList(
                "SELECT guards FROM workflow_transitions WHERE transition_code = 'BLOCK'", String.class);

        assertThat(guards).isNotEmpty();
        assertThat(guards).allSatisfy(guard -> assertThat(guard).contains("REQUIRE_REASON"));
    }
}
