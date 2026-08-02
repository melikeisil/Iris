package com.smartwork.report.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartwork.report.application.ReportMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Calls the AI service's report summarizer and falls back to a deterministic,
 * metrics-only summary when the model is unavailable or disabled.
 *
 * <p>The request body carries only the numbers and names {@link ReportMetrics} already
 * computed from persisted data — never raw work item descriptions, and nothing the AI
 * could use to invent a fact that is not already true in the database. The prompt on the
 * AI-service side (see {@code app/services/report_summarizer.py}) is explicit that it must
 * restate what it is given, not add to it, and must never rank, score, or compare
 * individual employees — this client does not even send per-employee task counts that a
 * prompt could be tempted to turn into a ranking; see {@link #summaryInput}.
 */
@Component
@EnableConfigurationProperties(ReportAiProperties.class)
public class ReportAiClient {

    private static final Logger log = LoggerFactory.getLogger(ReportAiClient.class);
    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final RestClient restClient;
    private final ReportAiProperties properties;

    public ReportAiClient(RestClient.Builder builder, ReportAiProperties properties) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(SERVICE_TOKEN_HEADER, properties.getServiceToken())
                .build();
    }

    public record Summary(String text, List<String> recommendedActions, String provider) {

        static Summary fallback(String text, List<String> recommendedActions) {
            return new Summary(text, recommendedActions, "deterministic-fallback");
        }
    }

    public Summary summarize(ReportMetrics metrics) {
        if (!properties.isEnabled()) {
            return deterministicFallback(metrics);
        }
        try {
            SummarizeReportResponse response = restClient.post()
                    .uri("/internal/v1/summarize-report")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(summaryInput(metrics))
                    .retrieve()
                    .body(SummarizeReportResponse.class);
            if (response == null || response.summary() == null || response.summary().isBlank()) {
                return deterministicFallback(metrics);
            }
            return new Summary(
                    response.summary(),
                    response.recommendedActions() == null ? List.of() : response.recommendedActions(),
                    response.meta() == null ? "unknown" : response.meta().provider());
        } catch (Exception e) {
            log.warn("AI report summary unavailable, using deterministic fallback: {}", e.getMessage());
            return deterministicFallback(metrics);
        }
    }

    /**
     * Only aggregate counts and names already destined for the report — no free text, no
     * per-employee counts (grouping employees' tasks is fine; comparing how many each has
     * is not something this client hands the model an opportunity to do).
     */
    private SummarizeReportRequest summaryInput(ReportMetrics metrics) {
        return new SummarizeReportRequest(
                metrics.periodStart().toString(),
                metrics.periodEnd().toString(),
                metrics.statusCounts(),
                metrics.completedThisWeek().size(),
                metrics.blockedTasks().size(),
                metrics.overdueTasks().size(),
                metrics.slaRiskTasks().size(),
                metrics.reassignments().size(),
                metrics.approvalChanges().size(),
                metrics.departmentWorkload().stream()
                        .map(d -> new SummarizeReportRequest.DepartmentLoad(
                                d.departmentName(), d.totalCapacityHours(), d.assignedHours()))
                        .toList(),
                metrics.parentTicketProgress().stream()
                        .map(p -> new SummarizeReportRequest.ParentProgress(p.referenceKey(), p.progressPercent()))
                        .toList());
    }

    private Summary deterministicFallback(ReportMetrics metrics) {
        String text = "Week of %s to %s: %d task(s) completed, %d blocked, %d overdue, %d at SLA risk, "
                .formatted(
                        metrics.periodStart(), metrics.periodEnd(),
                        metrics.completedThisWeek().size(), metrics.blockedTasks().size(),
                        metrics.overdueTasks().size(), metrics.slaRiskTasks().size())
                + "%d reassignment(s), %d approval decision(s). Generated from persisted metrics only; "
                        .formatted(metrics.reassignments().size(), metrics.approvalChanges().size())
                + "the AI summarizer was unavailable for this report.";
        List<String> actions = List.of(
                metrics.blockedTasks().isEmpty()
                        ? "No blocked work this period."
                        : "Review the %d blocked task(s) listed below and their blockers."
                                .formatted(metrics.blockedTasks().size()),
                metrics.overdueTasks().isEmpty()
                        ? "No overdue work this period."
                        : "Follow up on the %d overdue task(s) listed below.".formatted(metrics.overdueTasks().size()));
        return Summary.fallback(text, actions);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SummarizeReportRequest(
            @JsonProperty("period_start") String periodStart,
            @JsonProperty("period_end") String periodEnd,
            @JsonProperty("status_counts") Map<String, Long> statusCounts,
            @JsonProperty("completed_count") int completedCount,
            @JsonProperty("blocked_count") int blockedCount,
            @JsonProperty("overdue_count") int overdueCount,
            @JsonProperty("sla_risk_count") int slaRiskCount,
            @JsonProperty("reassignment_count") int reassignmentCount,
            @JsonProperty("approval_change_count") int approvalChangeCount,
            @JsonProperty("department_workload") List<DepartmentLoad> departmentWorkload,
            @JsonProperty("parent_ticket_progress") List<ParentProgress> parentTicketProgress) {

        record DepartmentLoad(
                @JsonProperty("department_name") String departmentName,
                @JsonProperty("total_capacity_hours") BigDecimal totalCapacityHours,
                @JsonProperty("assigned_hours") BigDecimal assignedHours) {
        }

        record ParentProgress(
                @JsonProperty("reference_key") String referenceKey,
                @JsonProperty("progress_percent") BigDecimal progressPercent) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SummarizeReportResponse(
            @JsonProperty("summary") String summary,
            @JsonProperty("recommended_actions") List<String> recommendedActions,
            @JsonProperty("meta") ProviderMeta meta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProviderMeta(@JsonProperty("provider") String provider, @JsonProperty("is_mock") boolean isMock) {
    }
}
