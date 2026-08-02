package com.smartwork.report.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwork.common.error.ApiException;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.report.domain.ReportAuditLogEntry;
import com.smartwork.report.domain.ReportOutboxEvent;
import com.smartwork.report.domain.WeeklyReport;
import com.smartwork.report.infrastructure.ReportAiClient;
import com.smartwork.report.infrastructure.ReportOutboxEventRepository;
import com.smartwork.report.infrastructure.WeeklyReportRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Orchestrates the whole Manager-triggered weekly report flow: generate, preview, send,
 * history. Every entry point starts the same way — {@link #requireManager} and {@link
 * #scopedDepartmentId} — so that "a Manager can only see their own organization's data" is
 * enforced once, in one place, rather than trusted to every call site.
 */
@Service
public class ReportService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ReportMetricsService metricsService;
    private final ReportAiClient aiClient;
    private final ReportEmailComposer emailComposer;
    private final ReportAuditLogService auditLog;
    private final WeeklyReportRepository reports;
    private final ReportOutboxEventRepository outboxEvents;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ReportService(
            ReportMetricsService metricsService,
            ReportAiClient aiClient,
            ReportEmailComposer emailComposer,
            ReportAuditLogService auditLog,
            WeeklyReportRepository reports,
            ReportOutboxEventRepository outboxEvents,
            ObjectMapper objectMapper,
            Clock clock) {
        this.metricsService = metricsService;
        this.aiClient = aiClient;
        this.emailComposer = emailComposer;
        this.auditLog = auditLog;
        this.reports = reports;
        this.outboxEvents = outboxEvents;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Monday through the following Monday (exclusive), in the server's clock. */
    public record Period(LocalDate start, LocalDate end) {
    }

    public Period currentWeek() {
        LocalDate today = LocalDate.now(clock);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new Period(monday, monday.plusDays(7));
    }

    @Transactional
    public WeeklyReport generate(AuthPrincipal principal, UUID requestedDepartmentId) {
        requireManager(principal);
        UUID departmentId = scopedDepartmentId(principal, requestedDepartmentId);
        Period period = currentWeek();

        ReportMetrics metrics = metricsService.compute(
                principal.organizationId(), departmentId, null, period.start(), period.end());
        ReportAiClient.Summary summary = aiClient.summarize(metrics);
        List<String> defaultRecipients = metricsService.defaultRecipients(principal.organizationId());

        WeeklyReport report = new WeeklyReport(
                principal.organizationId(), departmentId, period.start(), period.end(),
                principal.userId(), clock.instant(),
                objectMapper.convertValue(metrics, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                }),
                defaultRecipients);
        report.recordAiSummary(summary.text(), summary.recommendedActions(), summary.provider());
        WeeklyReport saved = reports.save(report);

        auditLog.record(principal.organizationId(), principal.userId(), ReportAuditLogEntry.Action.REPORT_GENERATED,
                saved.getId(), Map.of("periodStart", period.start().toString(), "periodEnd", period.end().toString()));
        return saved;
    }

    @Transactional(readOnly = true)
    public WeeklyReport preview(AuthPrincipal principal, UUID reportId) {
        requireManager(principal);
        WeeklyReport report = requireOwnedReport(principal, reportId);
        auditLog.record(principal.organizationId(), principal.userId(), ReportAuditLogEntry.Action.REPORT_PREVIEWED,
                report.getId(), null);
        return report;
    }

    @Transactional
    public WeeklyReport send(AuthPrincipal principal, UUID reportId, List<String> recipients) {
        requireManager(principal);
        WeeklyReport report = requireOwnedReport(principal, reportId);
        List<String> validated = validateRecipients(recipients);

        auditLog.record(principal.organizationId(), principal.userId(),
                ReportAuditLogEntry.Action.REPORT_SEND_REQUESTED, report.getId(),
                Map.of("recipients", validated));

        // The single compare-and-swap that prevents a duplicate click (or a duplicate
        // request from a retried network call) from enqueuing a second email: this only
        // succeeds once per report. A caller that loses the race gets a clear 409 rather
        // than silently sending twice.
        int claimed = reports.claimForSending(principal.organizationId(), reportId, principal.userId());
        if (claimed == 0) {
            WeeklyReport current = requireOwnedReport(principal, reportId);
            throw ApiException.duplicate(switch (current.status()) {
                case SENDING -> "This report is already being sent.";
                case SENT -> "This report has already been sent.";
                default -> "This report cannot be sent right now.";
            });
        }

        ReportOutboxEvent event = new ReportOutboxEvent(
                principal.organizationId(), "WEEKLY_REPORT", reportId, "REPORT_EMAIL",
                Map.of(
                        "recipients", validated,
                        "subject", emailComposer.subject(metricsOf(report)),
                        "body", emailComposer.htmlBody(metricsOf(report), report.aiSummary(), report.aiRecommendedActions())),
                clock.instant());
        ReportOutboxEvent savedEvent = outboxEvents.save(event);

        WeeklyReport reloaded = requireOwnedReport(principal, reportId);
        reloaded.attachDelivery(validated, savedEvent.getId());
        return reloaded;
    }

    @Transactional(readOnly = true)
    public Page<WeeklyReport> history(AuthPrincipal principal, Pageable pageable) {
        requireManager(principal);
        return reports.findByOrganizationIdOrderByGeneratedAtDesc(principal.organizationId(), pageable);
    }

    /** Reconstructs the typed metrics from the persisted JSONB — used for previews, the
     * send-time email body, and anywhere else a caller needs the structured shape back. */
    public ReportMetrics metricsOf(WeeklyReport report) {
        return objectMapper.convertValue(report.metrics(), ReportMetrics.class);
    }

    private WeeklyReport requireOwnedReport(AuthPrincipal principal, UUID reportId) {
        return reports.findByOrganizationIdAndId(principal.organizationId(), reportId)
                .orElseThrow(() -> ApiException.notFound("Report", reportId));
    }

    private void requireManager(AuthPrincipal principal) {
        if (!principal.isManager() && !principal.isAdmin()) {
            throw ApiException.forbidden("Only a Manager or Admin can generate or send weekly reports.");
        }
    }

    /**
     * A Manager scoped to one department (not {@code organizationWide}) always gets their
     * own department's report, regardless of what was requested — the same rule {@code
     * ReportingQueryService.departmentCapacity} applies. An organization-wide Manager may
     * request a specific department or {@code null} for the whole organization.
     */
    private UUID scopedDepartmentId(AuthPrincipal principal, UUID requestedDepartmentId) {
        if (!principal.organizationWide()) {
            return principal.departmentId();
        }
        return requestedDepartmentId;
    }

    private List<String> validateRecipients(List<String> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            throw ApiException.validation("At least one recipient is required.", List.of());
        }
        List<String> normalized = recipients.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .distinct()
                .toList();
        for (String email : normalized) {
            if (!EMAIL.matcher(email).matches()) {
                throw ApiException.validation("'" + email + "' is not a valid email address.", List.of());
            }
        }
        if (normalized.isEmpty()) {
            throw ApiException.validation("At least one valid recipient is required.", List.of());
        }
        return normalized;
    }
}
