package com.smartwork.report.domain;

import com.smartwork.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The first concrete table for the outbox pattern ADR-006 already commits to: a row here
 * is a fact ("this report needs to be emailed") that survives the request that created it,
 * drained by {@code ReportOutboxRelay} with {@code SELECT ... FOR UPDATE SKIP LOCKED} so
 * that email delivery is retried on transient failure without ever depending on the
 * original HTTP request still being alive.
 *
 * <p>The unique constraint on {@code (aggregateType, aggregateId, eventType)} is what
 * makes a duplicate "Send Report" click safe at the database level: a second insert for
 * the same report and event type fails, rather than relying only on the UI disabling the
 * button or the service layer remembering it already enqueued one.
 */
@Entity
@Table(name = "report_outbox_events")
public class ReportOutboxEvent extends TenantEntity {

    public enum Status {
        PENDING, SENT, FAILED, DEAD_LETTER
    }

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 32)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 32)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    protected ReportOutboxEvent() {
    }

    public ReportOutboxEvent(
            UUID organizationId, String aggregateType, UUID aggregateId, String eventType,
            Map<String, Object> payload, Instant now) {
        super(organizationId);
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.availableAt = now;
    }

    /** Exponential backoff, capped: 1m, 2m, 4m, 8m, then dead-letter on the 5th failure. */
    public void recordFailure(String error, Instant now) {
        this.attempts += 1;
        this.lastError = error;
        if (this.attempts >= this.maxAttempts) {
            this.status = Status.DEAD_LETTER;
            return;
        }
        long backoffMinutes = 1L << (this.attempts - 1);
        this.availableAt = now.plusSeconds(backoffMinutes * 60);
    }

    public void recordSuccess() {
        this.status = Status.SENT;
        this.lastError = null;
    }

    public String aggregateType() {
        return aggregateType;
    }

    public UUID aggregateId() {
        return aggregateId;
    }

    public String eventType() {
        return eventType;
    }

    public Map<String, Object> payload() {
        return payload;
    }

    public Status status() {
        return status;
    }

    public int attempts() {
        return attempts;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public String lastError() {
        return lastError;
    }

    public Instant availableAt() {
        return availableAt;
    }
}
