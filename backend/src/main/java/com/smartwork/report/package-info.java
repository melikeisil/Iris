/**
 * Manager-triggered weekly report email flow: compute a week's metrics from persisted
 * data, preview them, send them by email through the outbox, and keep a history.
 *
 * <p>Deliberately dependency-free beyond {@code common}. Every metric is read through
 * {@code ReportMetricsRepository}'s own native SQL against the tables other modules own
 * (work_items, assignments, approval_requests, workflow_states, teams, departments,
 * users) rather than through those modules' entities or services — the same choice
 * {@code reporting} makes for its own read models, and for the same reason: a report is a
 * read-only projection, not a place that should ever be tempted to reach for a JPA
 * association across a module boundary (ADR-028).
 *
 * <p>The AI summary is the exception worth calling out: it is generated from the computed
 * metrics only (never from raw work item text), by a prompt that explicitly forbids
 * inventing figures or ranking/scoring individual employees. See {@code ReportAiClient}
 * and {@code ai-service/app/services/report_summarizer.py}.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Report",
        allowedDependencies = {"common"})
package com.smartwork.report;
