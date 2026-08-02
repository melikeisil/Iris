package com.smartwork.report.api;

import com.smartwork.report.application.ReportMetrics;
import com.smartwork.report.domain.WeeklyReport;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record GenerateReportRequest(UUID departmentId) {
    }

    public record SendReportRequest(List<String> recipients) {
    }

    /** Returned by generate/preview and embedded in each history row. */
    public record ReportResponse(
            UUID id,
            UUID departmentId,
            LocalDate periodStart,
            LocalDate periodEnd,
            UUID generatedBy,
            Instant generatedAt,
            ReportMetrics metrics,
            String aiSummary,
            List<String> aiRecommendedActions,
            String aiProvider,
            List<String> defaultRecipients,
            List<String> recipients,
            String status,
            String emailStatus,
            String emailFailureReason,
            Instant sentAt,
            long version) {

        public static ReportResponse from(WeeklyReport report, ReportMetrics metrics) {
            return new ReportResponse(
                    report.getId(),
                    report.departmentId(),
                    report.periodStart(),
                    report.periodEnd(),
                    report.generatedBy(),
                    report.generatedAt(),
                    metrics,
                    report.aiSummary(),
                    report.aiRecommendedActions(),
                    report.aiProvider(),
                    report.defaultRecipients(),
                    report.recipients(),
                    report.status().name(),
                    report.emailStatus().name(),
                    report.emailFailureReason(),
                    report.sentAt(),
                    report.version());
        }
    }

    /** History rows omit the full metrics payload — the list view does not need it. */
    public record ReportSummaryResponse(
            UUID id,
            UUID departmentId,
            LocalDate periodStart,
            LocalDate periodEnd,
            Instant generatedAt,
            String status,
            String emailStatus,
            String emailFailureReason,
            Instant sentAt) {

        public static ReportSummaryResponse from(WeeklyReport report) {
            return new ReportSummaryResponse(
                    report.getId(), report.departmentId(), report.periodStart(), report.periodEnd(),
                    report.generatedAt(), report.status().name(), report.emailStatus().name(),
                    report.emailFailureReason(), report.sentAt());
        }
    }

    public record ReportHistoryResponse(List<ReportSummaryResponse> items, int page, int size, long totalElements) {
    }
}
