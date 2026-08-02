/**
 * Deterministic candidate ranking: workload, the seven-component score, eligibility
 * filters and the policies that configure them.
 *
 * <p>No LLM is in this module's loop, by design. The AI proposes what work is needed and
 * which skills it requires; who performs it is decided here, in Java, on stored data, with
 * a formula that can be replayed and explained six months later - see docs/08.
 *
 * <p>Stops at ranking. The routing decision (auto-assign versus which review queue) is the
 * {@code policy} module in Phase 6.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Assignment",
        allowedDependencies = {"common", "identity::application", "identity::domain",
                "organization::application", "team::application", "project::application",
                "workitem::domain"})
package com.smartwork.assignment;
