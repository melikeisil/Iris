package com.smartwork.report.api;

import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.report.application.ReportService;
import com.smartwork.report.domain.WeeklyReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The Manager-triggered weekly report flow: generate, preview, send, history.
 *
 * <p>Every method delegates authorization and organization/department scoping to {@link
 * ReportService} — see its class comment. Nothing here trusts a department ID from the
 * request over what the caller's own {@link AuthPrincipal} says they may see.
 */
@RestController
@RequestMapping("/api/v1/manager/reports/weekly")
@Tag(name = "weekly-reports", description = "Manager-triggered weekly report generation, preview and delivery")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/generate")
    @Operation(summary = "Compute this week's report from persisted data and store it as a draft")
    public ReportDtos.ReportResponse generate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody(required = false) ReportDtos.GenerateReportRequest request) {
        UUID departmentId = request == null ? null : request.departmentId();
        WeeklyReport report = reportService.generate(principal, departmentId);
        return ReportDtos.ReportResponse.from(report, reportService.metricsOf(report));
    }

    @GetMapping("/{id}/preview")
    @Operation(summary = "The full report a Manager reviews before sending")
    public ReportDtos.ReportResponse preview(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        WeeklyReport report = reportService.preview(principal, id);
        return ReportDtos.ReportResponse.from(report, reportService.metricsOf(report));
    }

    @PostMapping("/{id}/send")
    @Operation(summary = "Send the report by email to the given recipients")
    public ReportDtos.ReportResponse send(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @RequestBody ReportDtos.SendReportRequest request) {
        WeeklyReport report = reportService.send(principal, id, request.recipients());
        return ReportDtos.ReportResponse.from(report, reportService.metricsOf(report));
    }

    @GetMapping("/history")
    @Operation(summary = "Previously generated reports and their delivery outcome")
    public ReportDtos.ReportHistoryResponse history(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<WeeklyReport> result = reportService.history(principal, PageRequest.of(page, size));
        return new ReportDtos.ReportHistoryResponse(
                result.getContent().stream().map(ReportDtos.ReportSummaryResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }
}
