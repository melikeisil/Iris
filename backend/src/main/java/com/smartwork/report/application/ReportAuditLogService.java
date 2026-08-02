package com.smartwork.report.application;

import com.smartwork.report.domain.ReportAuditLogEntry;
import com.smartwork.report.infrastructure.ReportAuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class ReportAuditLogService {

    private final ReportAuditLogRepository repository;

    public ReportAuditLogService(ReportAuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(
            UUID organizationId, UUID actorUserId, ReportAuditLogEntry.Action action,
            UUID reportId, Map<String, Object> detail) {
        repository.save(new ReportAuditLogEntry(organizationId, actorUserId, action, reportId, detail));
    }
}
