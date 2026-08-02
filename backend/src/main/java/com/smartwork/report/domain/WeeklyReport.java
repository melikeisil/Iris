package com.smartwork.report.domain;

import com.smartwork.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A generated weekly report: the computed metrics, the AI's management summary, who
 * generated and sent it, the recipient list, and the delivery outcome.
 *
 * <p>One row is both the artifact a Manager previews and the history entry the report
 * history endpoint reads later — there is deliberately no separate "delivery result"
 * table. A report and its delivery outcome are one fact, not two that could disagree.
 *
 * <p>{@code status} tracks the report's own lifecycle (has it been generated, is a send
 * in flight, did it complete); {@code emailStatus} tracks the outbox delivery specifically
 * and can lag behind — {@code SENDING} + {@code emailStatus=PENDING} is the normal state
 * between clicking Send and the outbox relay actually dispatching the email.
 */
@Entity
@Table(name = "weekly_reports")
public class WeeklyReport extends TenantEntity {

    public enum Status {
        DRAFT, SENDING, SENT, SEND_FAILED
    }

    public enum EmailStatus {
        NOT_SENT, PENDING, SENT, FAILED
    }

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "period_start", nullable = false, updatable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false, updatable = false)
    private LocalDate periodEnd;

    @Column(name = "generated_by", nullable = false, updatable = false)
    private UUID generatedBy;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics", nullable = false)
    private Map<String, Object> metrics;

    @Column(name = "ai_summary")
    private String aiSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_recommended_actions")
    private List<String> aiRecommendedActions;

    @Column(name = "ai_provider", length = 32)
    private String aiProvider;

    /** The recipient list proposed at generation time (org's managers/admins). Never re-sent to. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_recipients", nullable = false)
    private List<String> defaultRecipients;

    /** What the Manager actually sent to, captured at send time — may differ from the default. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recipients")
    private List<String> recipients;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.DRAFT;

    @Column(name = "sender_user_id")
    private UUID senderUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", nullable = false, length = 16)
    private EmailStatus emailStatus = EmailStatus.NOT_SENT;

    @Column(name = "email_failure_reason")
    private String emailFailureReason;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "outbox_event_id")
    private UUID outboxEventId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected WeeklyReport() {
    }

    public WeeklyReport(
            UUID organizationId,
            UUID departmentId,
            LocalDate periodStart,
            LocalDate periodEnd,
            UUID generatedBy,
            Instant generatedAt,
            Map<String, Object> metrics,
            List<String> defaultRecipients) {
        super(organizationId);
        this.departmentId = departmentId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.generatedBy = generatedBy;
        this.generatedAt = generatedAt;
        this.metrics = metrics;
        this.defaultRecipients = defaultRecipients;
    }

    public void recordAiSummary(String summary, List<String> recommendedActions, String provider) {
        this.aiSummary = summary;
        this.aiRecommendedActions = recommendedActions;
        this.aiProvider = provider;
    }

    /**
     * Attaches the Manager's edited recipient list and the outbox event that will deliver
     * them, once this report has already been claimed for sending.
     *
     * <p>Split from the status transition on purpose: {@code
     * WeeklyReportRepository.claimForSending} is a bulk {@code UPDATE ... WHERE status IN
     * (...)} that does the actual compare-and-swap (the thing that makes a duplicate click
     * a no-op), because JPQL bulk updates cannot safely carry a JSON-mapped list parameter.
     * This method only ever runs after that claim already succeeded for this report, so it
     * never needs to re-check or re-set the status transition itself.
     */
    public void attachDelivery(List<String> recipients, UUID outboxEventId) {
        this.recipients = recipients;
        this.outboxEventId = outboxEventId;
    }

    public void markSent(Instant sentAt) {
        this.status = Status.SENT;
        this.emailStatus = EmailStatus.SENT;
        this.sentAt = sentAt;
        this.emailFailureReason = null;
    }

    public void markSendFailed(String reason) {
        this.status = Status.SEND_FAILED;
        this.emailStatus = EmailStatus.FAILED;
        this.emailFailureReason = reason;
    }

    public UUID departmentId() {
        return departmentId;
    }

    public LocalDate periodStart() {
        return periodStart;
    }

    public LocalDate periodEnd() {
        return periodEnd;
    }

    public UUID generatedBy() {
        return generatedBy;
    }

    public Instant generatedAt() {
        return generatedAt;
    }

    public Map<String, Object> metrics() {
        return metrics;
    }

    public String aiSummary() {
        return aiSummary;
    }

    public List<String> aiRecommendedActions() {
        return aiRecommendedActions;
    }

    public String aiProvider() {
        return aiProvider;
    }

    public List<String> defaultRecipients() {
        return defaultRecipients;
    }

    public List<String> recipients() {
        return recipients;
    }

    public Status status() {
        return status;
    }

    public UUID senderUserId() {
        return senderUserId;
    }

    public EmailStatus emailStatus() {
        return emailStatus;
    }

    public String emailFailureReason() {
        return emailFailureReason;
    }

    public Instant sentAt() {
        return sentAt;
    }

    public UUID outboxEventId() {
        return outboxEventId;
    }

    public long version() {
        return version;
    }
}
