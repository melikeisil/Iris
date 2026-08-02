package com.smartwork.report.infrastructure;

import com.smartwork.report.domain.ReportOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReportOutboxEventRepository extends JpaRepository<ReportOutboxEvent, UUID> {

    /**
     * The relay's claim query — ADR-006's {@code SELECT ... FOR UPDATE SKIP LOCKED},
     * spelled out directly in the native query rather than through Spring Data's
     * {@code @Lock} (which appends a lock clause to a JPQL query but has no "skip locked"
     * of its own). If this process ever runs with more than one instance, two relays never
     * pick up and double-send the same event: each locked row is invisible to the other's
     * SELECT until the first transaction commits or rolls back, and SKIP LOCKED means the
     * second relay just moves on to the next row instead of blocking behind it.
     */
    @Query(value = """
            SELECT * FROM report_outbox_events
             WHERE status = 'PENDING' AND available_at <= :now
             ORDER BY available_at
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ReportOutboxEvent> claimBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
