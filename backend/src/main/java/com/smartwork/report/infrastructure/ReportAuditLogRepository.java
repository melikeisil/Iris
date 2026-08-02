package com.smartwork.report.infrastructure;

import com.smartwork.report.domain.ReportAuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportAuditLogRepository extends JpaRepository<ReportAuditLogEntry, UUID> {

    List<ReportAuditLogEntry> findByOrganizationIdAndReportIdOrderByCreatedAtAsc(UUID organizationId, UUID reportId);
}
