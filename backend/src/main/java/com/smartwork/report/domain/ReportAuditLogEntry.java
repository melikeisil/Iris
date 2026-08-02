package com.smartwork.report.domain;

import com.smartwork.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

/**
 * An append-only audit record for one report action. Nothing here is ever updated or
 * deleted after insert — {@code BaseEntity.updatedAt} exists on the superclass but has no
 * meaning for a row that should never change; a row's {@code createdAt} is its whole story.
 */
@Entity
@Table(name = "report_audit_log")
public class ReportAuditLogEntry extends TenantEntity {

    public enum Action {
        REPORT_GENERATED, REPORT_PREVIEWED, REPORT_SEND_REQUESTED, REPORT_SENT, REPORT_SEND_FAILED
    }

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 32)
    private Action action;

    @Column(name = "report_id", updatable = false)
    private UUID reportId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail")
    private Map<String, Object> detail;

    protected ReportAuditLogEntry() {
    }

    public ReportAuditLogEntry(
            UUID organizationId, UUID actorUserId, Action action, UUID reportId, Map<String, Object> detail) {
        super(organizationId);
        this.actorUserId = actorUserId;
        this.action = action;
        this.reportId = reportId;
        this.detail = detail;
    }

    public UUID actorUserId() {
        return actorUserId;
    }

    public Action action() {
        return action;
    }

    public UUID reportId() {
        return reportId;
    }

    public Map<String, Object> detail() {
        return detail;
    }
}
