package com.smartwork.assignment.domain;

/**
 * Why a candidate was ruled out (docs/08 §3).
 *
 * <p>Every exclusion is persisted alongside the ranked candidates rather than silently
 * dropped, because "why was the obvious person skipped?" is the single most useful
 * explainability question the product answers. A recommendation that shows only who was
 * chosen invites the reviewer to assume the system did not consider the person they had
 * in mind.
 */
public enum ExclusionReason {

    INACTIVE_USER("the account is inactive or has been removed"),
    MISSING_MANDATORY_SKILL("they do not hold a required skill at the needed proficiency"),
    SEVERELY_OVERLOADED("their workload is already above 120% of capacity"),
    UNAVAILABLE("they are on leave for the whole window before the due date"),
    SELF_ASSIGNMENT_CONFLICT("they reported this item and it requires independent review"),

    LEVEL_RESTRICTION_PRODUCTION_ACCESS("their employee level may not take production-impacting work"),
    LEVEL_RESTRICTION_FINANCIAL("their employee level may not take budget-sensitive work"),
    LEVEL_RESTRICTION_SECURITY("their employee level may not take security-sensitive work"),
    LEVEL_RESTRICTION_PRIORITY("this item's priority is above their employee level's ceiling"),
    LEVEL_RESTRICTION_MAX_ACTIVE("they already hold the maximum active items for their level"),
    LEVEL_RESTRICTION_MAX_HOURS("this item is larger than their employee level's per-item limit"),
    LEVEL_RESTRICTION_MENTOR_REQUIRED("their level requires a senior reviewer and none is set"),
    LEVEL_RESTRICTION_CATEGORY("this work category is outside their level's allowlist");

    private final String explanation;

    ExclusionReason(String explanation) {
        this.explanation = explanation;
    }

    public String explanation() {
        return explanation;
    }

    public boolean isLevelRestriction() {
        return name().startsWith("LEVEL_RESTRICTION_");
    }
}
