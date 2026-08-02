package com.smartwork.report.infrastructure;

import com.smartwork.report.domain.WeeklyReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, UUID> {

    Optional<WeeklyReport> findByOrganizationIdAndId(UUID organizationId, UUID id);

    Page<WeeklyReport> findByOrganizationIdOrderByGeneratedAtDesc(UUID organizationId, Pageable pageable);

    /**
     * The compare-and-swap that makes "Send Report" safe against a duplicate click or a
     * duplicate request: it only flips a row out of {@code DRAFT}/{@code SEND_FAILED} into
     * {@code SENDING} once. A second, near-simultaneous call — whether a genuine
     * double-click that got past the disabled button, a retried request, or two browser
     * tabs — updates zero rows and the service layer treats that as "already in flight",
     * never enqueuing a second outbox event or sending a second email.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE WeeklyReport r
               SET r.status = :sending,
                   r.emailStatus = :pending,
                   r.senderUserId = :senderUserId,
                   r.emailFailureReason = null
             WHERE r.id = :id
               AND r.organizationId = :organizationId
               AND r.status IN :claimableFrom
            """)
    int claimForSending(
            @Param("organizationId") UUID organizationId,
            @Param("id") UUID id,
            @Param("senderUserId") UUID senderUserId,
            @Param("claimableFrom") java.util.Collection<WeeklyReport.Status> claimableFrom,
            @Param("sending") WeeklyReport.Status sending,
            @Param("pending") WeeklyReport.EmailStatus pending);

    default int claimForSending(UUID organizationId, UUID id, UUID senderUserId) {
        return claimForSending(
                organizationId, id, senderUserId,
                java.util.EnumSet.of(WeeklyReport.Status.DRAFT, WeeklyReport.Status.SEND_FAILED),
                WeeklyReport.Status.SENDING,
                WeeklyReport.EmailStatus.PENDING);
    }
}
