package com.smartwork.policy.domain;

import java.util.Arrays;
import java.util.List;

/**
 * Why a work item routed where it did — a closed enum, deliberately.
 *
 * <p>docs/08 §5.6: "Reason codes are a closed enum. Every route decision is fully
 * explainable from stored data and replayable against a different policy version." Free
 * text would make the *explanation* readable and the *analytics* impossible; a closed set
 * means "how often does confidence block auto-assign?" is a group-by rather than a
 * substring search over prose that changed wording three releases ago.
 *
 * <p>Each carries the human phrasing used to build the explanation, so the two can never
 * disagree — the template is generated from the codes, not written alongside them.
 */
public enum ReasonCode {

    // ---- MANUAL_EXCEPTION_REVIEW ----
    ANALYSIS_FAILED("the AI analysis failed or could not be completed"),
    INJECTION_SUSPECTED("the request contains text that looks like an attempt to manipulate the analysis"),

    // ---- REQUEST_MORE_INFORMATION ----
    MORE_INFORMATION_REQUIRED("the analysis needs answers before this work can be planned"),

    // ---- MANAGER_APPROVAL ----
    RISK_CRITICAL("the risk level is CRITICAL"),
    SECURITY_IMPACT("the work is security-sensitive"),
    PRODUCTION_IMPACT("the work affects production"),
    BUDGET_IMPACT("the work commits budget"),
    COMPLIANCE_IMPACT("the work has compliance implications"),
    MAJOR_RESOURCE_COMMITMENT("the estimated effort is a major resource commitment"),
    CROSS_TEAM_PRIORITY_CONFLICT("this competes for the same people as other in-flight priorities"),
    ACTIVE_POLICY_EXCEPTION("an active policy exception applies to this item"),
    TEAM_LEAD_ESCALATION("a Team Lead escalated this beyond their authority"),
    PROCESS_OWNER_ESCALATION("a Process Owner escalated this beyond their authority"),

    // ---- PROCESS_OWNER_REVIEW ----
    MULTI_DEPARTMENT("the plan spans more than one department"),
    MULTI_TEAM("the plan spans more than one team"),
    NO_TEMPLATE_MATCH("no known process template matched this request"),
    SCOPE_UNCLEAR("the scope is not defined clearly enough to plan against"),
    ACCEPTANCE_CRITERIA_INCOMPLETE("the acceptance criteria are incomplete"),
    CROSS_TEAM_DEPENDENCIES("the plan contains dependencies across team boundaries"),
    EPIC_SCOPE_CHANGED("the scope changed after this was approved"),
    NON_STANDARD_PLAN("a new, non-standard plan was generated rather than a known process"),

    // ---- TEAM_LEAD_REVIEW ----
    RISK_MEDIUM("the risk level is MEDIUM"),
    CONFIDENCE_BELOW_AUTO_THRESHOLD("AI confidence is below the auto-assign threshold"),
    CANDIDATE_SCORES_CLOSE("the top two candidates are too close to separate confidently"),
    TOP_CANDIDATE_WORKLOAD_HIGH("the best-matched person is already heavily loaded"),
    DECOMPOSITION_UNCERTAIN("the proposed breakdown is not confident enough to act on"),
    MULTI_COMPONENT("the work touches several components"),
    SENIOR_SKILL_REQUIRED("this needs a senior-level skill"),
    OVERSIZED_FOR_AUTO_ASSIGN("the item is larger than auto-assignment allows"),
    CLASSIFICATION_UNCERTAIN("the department or team classification is uncertain"),
    NO_ELIGIBLE_CANDIDATE("nobody in the pool is eligible for this work"),
    TOP_SCORE_BELOW_THRESHOLD("the best candidate still scores below the auto-assign floor"),
    CROSS_DEPARTMENT_POOL("the only candidates are outside the recommended department"),
    UNRESOLVED_DEPENDENCY("this is blocked by an unresolved dependency"),

    // ---- AUTO_ASSIGN ----
    ALL_AUTO_ASSIGN_CONDITIONS_MET("every automatic assignment condition is satisfied");

    private final String phrase;

    ReasonCode(String phrase) {
        this.phrase = phrase;
    }

    public String phrase() {
        return phrase;
    }

    public static List<String> allCodes() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
