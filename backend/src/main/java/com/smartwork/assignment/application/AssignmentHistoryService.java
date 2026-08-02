package com.smartwork.assignment.application;

import com.smartwork.assignment.domain.Assignment;
import com.smartwork.assignment.infrastructure.AssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AssignmentHistoryService implements AssignmentHistory {

    private final AssignmentRepository assignments;
    private final Clock clock;

    public AssignmentHistoryService(AssignmentRepository assignments, Clock clock) {
        this.assignments = assignments;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void record(Entry entry) {
        Instant now = clock.instant();

        // The partial unique index allows only one active row per item, so the previous
        // one must be closed in the same transaction or the insert fails.
        assignments.findByWorkItemIdAndActiveTrue(entry.workItemId())
                .ifPresent(previous -> previous.end(now));

        Assignment assignment = new Assignment(
                entry.organizationId(), entry.workItemId(), entry.assigneeId(),
                Assignment.Source.valueOf(entry.source().name()));
        assignment.setRecommendationId(entry.recommendationId());
        assignment.setAssignedBy(entry.assignedBy());
        assignment.setReason(entry.reason());

        assignments.save(assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public int countAssignedSince(UUID userId, Instant since) {
        List<Object[]> rows = assignments.countAssignedSince(List.of(userId), since);
        return rows.isEmpty() ? 0 : ((Number) rows.get(0)[1]).intValue();
    }
}
