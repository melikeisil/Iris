package com.smartwork.report.application;

import org.springframework.stereotype.Component;

/** Turns a computed report and its AI summary into the email that actually gets sent. */
@Component
public class ReportEmailComposer {

    public String subject(ReportMetrics metrics) {
        String scope = metrics.departmentName() == null ? "Organization" : metrics.departmentName();
        return "Weekly Report - %s (%s to %s)".formatted(scope, metrics.periodStart(), metrics.periodEnd());
    }

    public String htmlBody(ReportMetrics metrics, String aiSummary, java.util.List<String> recommendedActions) {
        StringBuilder html = new StringBuilder();
        html.append("<h2>Weekly Report</h2>");
        html.append("<p><strong>Period:</strong> %s to %s</p>".formatted(metrics.periodStart(), metrics.periodEnd()));

        if (aiSummary != null && !aiSummary.isBlank()) {
            html.append("<h3>Summary</h3><p>").append(escape(aiSummary)).append("</p>");
        }
        if (recommendedActions != null && !recommendedActions.isEmpty()) {
            html.append("<h3>Recommended actions</h3><ul>");
            for (String action : recommendedActions) {
                html.append("<li>").append(escape(action)).append("</li>");
            }
            html.append("</ul>");
        }

        html.append("<h3>Status counts</h3><ul>");
        metrics.statusCounts().forEach((status, count) ->
                html.append("<li>").append(escape(status)).append(": ").append(count).append("</li>"));
        html.append("</ul>");

        html.append("<h3>Completed this week (").append(metrics.completedThisWeek().size()).append(")</h3><ul>");
        for (ReportMetrics.CompletedTask task : metrics.completedThisWeek()) {
            html.append("<li>").append(escape(task.referenceKey())).append(" - ")
                    .append(escape(task.summary())).append(" (completed by ")
                    .append(escape(task.completedByName() == null ? "unassigned" : task.completedByName()))
                    .append(")</li>");
        }
        html.append("</ul>");

        html.append("<h3>Blocked (").append(metrics.blockedTasks().size()).append(")</h3><ul>");
        for (ReportMetrics.BlockedTask task : metrics.blockedTasks()) {
            html.append("<li>").append(escape(task.referenceKey())).append(" - ")
                    .append(escape(task.summary())).append(": ").append(escape(task.blockReason()))
                    .append("</li>");
        }
        html.append("</ul>");

        html.append("<h3>Overdue (").append(metrics.overdueTasks().size())
                .append(") / SLA risk (").append(metrics.slaRiskTasks().size()).append(")</h3>");

        html.append("<h3>Department workload</h3><ul>");
        for (ReportMetrics.DepartmentWorkload workload : metrics.departmentWorkload()) {
            html.append("<li>").append(escape(workload.departmentName())).append(": ")
                    .append(workload.assignedHours()).append(" / ").append(workload.totalCapacityHours())
                    .append(" hrs (remaining ").append(workload.remainingCapacityHours()).append(")</li>");
        }
        html.append("</ul>");

        html.append("<p style=\"color:#888;font-size:12px\">Generated automatically by SmartWork AI "
                + "from persisted platform data.</p>");
        return html.toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
