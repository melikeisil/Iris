package com.smartwork.workitem.domain;

/**
 * How a work item <em>behaves</em>. Selects the default workflow profile, SLA policy,
 * process template pool and reporting bucket.
 *
 * <p>Set at creation by {@link WorkFamilyResolver} and then frozen unless a Team Lead
 * explicitly redirects the item - reclassification changes which team owns the work, so
 * it should be a deliberate act rather than a side effect of an edit.
 */
public enum WorkFamily {

    SERVICE_WORK("SERVICE", true),
    PROCESS_WORK("PROCESS", true),
    DEVELOPMENT_WORK("DEVELOPMENT", false),
    INCIDENT_WORK("INCIDENT", true),
    GENERAL_OPERATIONAL_WORK("SERVICE", false);

    private final String defaultWorkflowCode;
    private final boolean slaTracked;

    WorkFamily(String defaultWorkflowCode, boolean slaTracked) {
        this.defaultWorkflowCode = defaultWorkflowCode;
        this.slaTracked = slaTracked;
    }

    public String defaultWorkflowCode() {
        return defaultWorkflowCode;
    }

    public boolean isSlaTracked() {
        return slaTracked;
    }

    public ClosureType defaultClosureType() {
        return switch (this) {
            case SERVICE_WORK -> ClosureType.REQUESTER_VERIFICATION;
            case PROCESS_WORK -> ClosureType.PROCESS_OWNER_VERIFICATION;
            case INCIDENT_WORK -> ClosureType.TEAM_LEAD_VERIFICATION;
            case DEVELOPMENT_WORK, GENERAL_OPERATIONAL_WORK -> ClosureType.AUTO_CLOSE;
        };
    }
}
