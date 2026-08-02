package com.smartwork.identity.domain;

/**
 * Seniority band. Drives the deterministic level restrictions in the policy engine
 * (Phase 6) - interns are excluded from production, financial and critical work by
 * backend rule, never by a prompt instruction.
 */
public enum EmployeeLevel {
    INTERN,
    JUNIOR,
    MID,
    SENIOR,
    LEAD;

    public boolean isAtLeast(EmployeeLevel other) {
        return ordinal() >= other.ordinal();
    }
}
