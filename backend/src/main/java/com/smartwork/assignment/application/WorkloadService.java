package com.smartwork.assignment.application;

import com.smartwork.assignment.domain.WorkloadCalculator;
import com.smartwork.assignment.infrastructure.CandidateQueryRepository;
import com.smartwork.workitem.domain.WorkItemType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns the batch query results into a {@link WorkloadCalculator.Result} per user.
 *
 * <p>All the arithmetic lives in the pure calculator; this class only does the I/O and the
 * grouping. That split is what lets the workload rules be unit-tested at their boundary
 * values without a database, and it means there is exactly one implementation of the
 * formula rather than one in Java and a second in SQL (ADR-034).
 */
@Service
public class WorkloadService {

    private final CandidateQueryRepository candidateQueries;
    private final Clock clock;

    public WorkloadService(CandidateQueryRepository candidateQueries, Clock clock) {
        this.candidateQueries = candidateQueries;
        this.clock = clock;
    }

    /** Capacity inputs per user, supplied by the caller who already loaded the pool. */
    public record CapacitySnapshot(UUID userId, BigDecimal weeklyCapacityHours, int allocationPercent) {
    }

    /** Public application-facing shape so callers do not depend on assignment domain internals. */
    public record WorkloadSnapshot(
            BigDecimal capacityHours,
            BigDecimal openHours,
            BigDecimal ratio) {
    }

    @Transactional(readOnly = true)
    public Map<UUID, WorkloadSnapshot> calculateFor(List<CapacitySnapshot> pool) {
        if (pool.isEmpty()) {
            return Map.of();
        }
        Instant now = clock.instant();
        List<UUID> userIds = pool.stream().map(CapacitySnapshot::userId).toList();

        Map<UUID, List<WorkloadCalculator.OpenItem>> openItems = groupOpenItems(userIds);
        Map<UUID, Integer> reviewCounts = candidateQueries.countAwaitingReview(userIds);

        Map<UUID, WorkloadSnapshot> results = new HashMap<>();
        for (CapacitySnapshot snapshot : pool) {
            var calculated = com.smartwork.assignment.domain.WorkloadCalculator.calculate(
                    openItems.getOrDefault(snapshot.userId(), List.of()),
                    reviewCounts.getOrDefault(snapshot.userId(), 0),
                    // No on-call roster table exists (docs/05 §6 has no such table), so
                    // support duty is zero rather than invented. See ADR-035.
                    BigDecimal.ZERO,
                    com.smartwork.assignment.domain.WorkloadCalculator.CapacityInputs.standard(
                            snapshot.weeklyCapacityHours(), snapshot.allocationPercent()),
                    null,
                    now);
            results.put(snapshot.userId(), new WorkloadSnapshot(
                    calculated.capacityHours(),
                    calculated.openHours(),
                    calculated.ratio()));
        }
        return results;
    }

    private Map<UUID, List<WorkloadCalculator.OpenItem>> groupOpenItems(List<UUID> userIds) {
        Map<UUID, List<WorkloadCalculator.OpenItem>> grouped = new HashMap<>();
        for (Object[] row : candidateQueries.findOpenItems(userIds)) {
            UUID userId = (UUID) row[0];
            WorkloadCalculator.OpenItem item = new WorkloadCalculator.OpenItem(
                    WorkItemType.valueOf((String) row[1]),
                    (BigDecimal) row[2],
                    (BigDecimal) row[3],
                    row[4] == null ? null : ((Number) row[4]).intValue(),
                    Boolean.TRUE.equals(row[5]),
                    Boolean.TRUE.equals(row[6]),
                    row[7] == null ? null : ((java.sql.Timestamp) row[7]).toInstant());
            grouped.computeIfAbsent(userId, key -> new ArrayList<>()).add(item);
        }
        return grouped;
    }
}
