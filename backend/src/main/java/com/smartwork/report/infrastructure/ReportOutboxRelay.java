package com.smartwork.report.infrastructure;

import com.smartwork.report.domain.ReportOutboxEvent;
import com.smartwork.report.domain.WeeklyReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Drains {@code report_outbox_events}: the ADR-006 relay, applied to report emails.
 *
 * <p>Runs every 5 seconds, claims up to 20 due events with {@code SELECT ... FOR UPDATE
 * SKIP LOCKED} (so this is safe to run on more than one instance), attempts delivery, and
 * on failure backs off exponentially (see {@link ReportOutboxEvent#recordFailure}) up to
 * five attempts before moving the event to {@code DEAD_LETTER} and marking the report
 * {@code SEND_FAILED} with the last error — visible in report history and retryable by the
 * Manager clicking "Send Report" again, which is a fresh, valid claim once the report is
 * back in a claimable status.
 */
@Component
public class ReportOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(ReportOutboxRelay.class);
    private static final int BATCH_SIZE = 20;

    private final ReportOutboxEventRepository outboxEvents;
    private final WeeklyReportRepository reports;
    private final EmailSender emailSender;
    private final Clock clock;

    public ReportOutboxRelay(
            ReportOutboxEventRepository outboxEvents,
            WeeklyReportRepository reports,
            EmailSender emailSender,
            Clock clock) {
        this.outboxEvents = outboxEvents;
        this.reports = reports;
        this.emailSender = emailSender;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT5S", initialDelayString = "PT5S")
    @Transactional
    public void drain() {
        Instant now = clock.instant();
        List<ReportOutboxEvent> claimed = outboxEvents.claimBatch(now, BATCH_SIZE);
        for (ReportOutboxEvent event : claimed) {
            process(event, now);
        }
    }

    @SuppressWarnings("unchecked")
    private void process(ReportOutboxEvent event, Instant now) {
        Map<String, Object> payload = event.payload();
        try {
            List<String> recipients = (List<String>) payload.get("recipients");
            String subject = String.valueOf(payload.get("subject"));
            String body = String.valueOf(payload.get("body"));
            emailSender.send(recipients, subject, body);
            event.recordSuccess();
            markReportOutcome(event.aggregateId(), true, null, now);
            log.info("report_email_sent report_id={} recipients={}", event.aggregateId(), recipients.size());
        } catch (Exception e) {
            event.recordFailure(e.getMessage(), now);
            log.warn("report_email_attempt_failed report_id={} attempt={} error={}",
                    event.aggregateId(), event.attempts(), e.getMessage());
            if (event.status() == ReportOutboxEvent.Status.DEAD_LETTER) {
                markReportOutcome(event.aggregateId(), false, e.getMessage(), now);
            }
        }
    }

    private void markReportOutcome(UUID reportId, boolean success, String error, Instant now) {
        Optional<WeeklyReport> report = reports.findById(reportId);
        report.ifPresent(r -> {
            if (success) {
                r.markSent(now);
            } else {
                r.markSendFailed(error == null ? "Email delivery failed after repeated retries" : error);
            }
        });
    }
}
