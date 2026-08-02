package com.smartwork.reporting.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class ReportingDtos {

    private ReportingDtos() {
    }

    public record TeamWorkloadResponse(
            UUID teamId,
            String teamName,
            List<TeamMemberLoadResponse> members,
            BigDecimal plannedRatio,
            BigDecimal unplannedRatio) {
    }

    public record TeamMemberLoadResponse(
            UUID userId,
            String fullName,
            String role,
            BigDecimal capacityHours,
            BigDecimal assignedHours,
            int openItems,
            int blockedItems,
            List<String> skills) {
    }

    public record DepartmentCapacityResponse(
            UUID departmentId,
            String departmentName,
            BigDecimal totalCapacityHours,
            BigDecimal totalAssignedHours,
            BigDecimal utilizationRatio,
            List<DepartmentTeamCapacityResponse> teams) {
    }

    public record DepartmentTeamCapacityResponse(
            UUID teamId,
            String teamName,
            BigDecimal capacityHours,
            BigDecimal assignedHours,
            BigDecimal utilizationRatio,
            int blockedItems) {
    }

    public record AiAnalyticsResponse(
            BigDecimal aiAcceptanceRate,
            BigDecimal aiOverrideRate,
            BigDecimal autoAssignedShare,
            long averageDecisionLatencyMinutes,
            PlannedVsUnplannedResponse plannedVsUnplanned,
            long policyExceptionsLast30Days) {
    }

    public record PlannedVsUnplannedResponse(long planned, long unplanned) {
    }
}
