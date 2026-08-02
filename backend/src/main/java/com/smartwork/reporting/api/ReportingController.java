package com.smartwork.reporting.api;

import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.reporting.application.ReportingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "reports", description = "Capacity and AI analytics read models")
public class ReportingController {

    private final ReportingQueryService reporting;

    public ReportingController(ReportingQueryService reporting) {
        this.reporting = reporting;
    }

    @GetMapping("/team-lead/workload")
    @Operation(summary = "Team workload and capacity bands")
    public ReportingDtos.TeamWorkloadResponse teamWorkload(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) UUID teamId) {
        return reporting.teamWorkload(principal, teamId);
    }

    @GetMapping("/manager/capacity")
    @Operation(summary = "Department capacity rollup and cross-team pressure")
    public ReportingDtos.DepartmentCapacityResponse departmentCapacity(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) UUID departmentId) {
        return reporting.departmentCapacity(principal, departmentId);
    }

    @GetMapping("/reports/ai")
    @Operation(summary = "AI acceptance and routing analytics")
    public ReportingDtos.AiAnalyticsResponse aiAnalytics(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return reporting.aiAnalytics(principal);
    }
}
