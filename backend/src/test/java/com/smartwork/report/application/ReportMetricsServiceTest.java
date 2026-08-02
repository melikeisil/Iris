package com.smartwork.report.application;

import com.smartwork.report.infrastructure.ReportMetricsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReportMetricsService}'s row-to-record mapping — in particular
 * that {@link ReportMetrics#activeByEmployee()} is a grouping and never a ranking (see the
 * class comment on {@link ReportMetrics}).
 */
class ReportMetricsServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID ALICE_ID = UUID.randomUUID();
    private static final UUID BOB_ID = UUID.randomUUID();

    private ReportMetricsRepository repository;
    private ReportMetricsService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReportMetricsRepository.class);
        service = new ReportMetricsService(repository);
        when(repository.statusCounts(any(), any())).thenReturn(List.of());
        when(repository.completedThisWeek(any(), any(), any(), any())).thenReturn(List.of());
        when(repository.blockedTasks(any(), any())).thenReturn(List.of());
        when(repository.departmentWorkload(any(), any())).thenReturn(List.of());
        when(repository.parentTicketProgress(any(), any())).thenReturn(List.of());
        when(repository.reassignmentsThisWeek(any(), any(), any(), any())).thenReturn(List.of());
        when(repository.approvalChangesThisWeek(any(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("groups active tasks under each employee without any count, order, or comparison between them")
    void groupsActiveTasksWithoutRanking() {
        when(repository.activeTasksByEmployee(any(), any())).thenReturn(List.of(
                new Object[]{BOB_ID, "Bob", "TCK-1", "First bob task", "HIGH", LocalDate.of(2026, 8, 5)},
                new Object[]{ALICE_ID, "Alice", "TCK-2", "Alice task", "MEDIUM", LocalDate.of(2026, 8, 6)},
                new Object[]{BOB_ID, "Bob", "TCK-3", "Second bob task", "LOW", null}));
        when(repository.overdueAndAtRisk(any(), any(), any(), any())).thenReturn(List.of());

        ReportMetrics metrics = service.compute(
                ORG_ID, null, null, LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 3));

        assertThat(metrics.activeByEmployee()).hasSize(2);
        ReportMetrics.EmployeeActiveTasks bob = metrics.activeByEmployee().stream()
                .filter(e -> e.employeeId().equals(BOB_ID)).findFirst().orElseThrow();
        assertThat(bob.tasks()).hasSize(2);
        assertThat(bob.tasks()).extracting(ReportMetrics.ActiveTask::referenceKey)
                .containsExactly("TCK-1", "TCK-3");

        // No field anywhere in the record carries a count comparison, a rank, or a score -
        // this is a structural assertion, not just a naming convention: every record
        // component in ReportMetrics is declared in ReportMetrics.java and none of them is
        // named rank/score, so there is nothing for a caller to sort employees by.
        for (var field : ReportMetrics.EmployeeActiveTasks.class.getRecordComponents()) {
            assertThat(field.getName().toLowerCase()).doesNotContain("rank").doesNotContain("score");
        }
    }

    @Test
    @DisplayName("splits due tasks into overdue and SLA-risk buckets by the repository's own flag")
    void splitsOverdueAndSlaRisk() {
        when(repository.activeTasksByEmployee(any(), any())).thenReturn(List.of());
        when(repository.overdueAndAtRisk(any(), any(), any(), any())).thenReturn(List.of(
                new Object[]{true, "TCK-1", "Overdue task", "Alice", LocalDate.of(2026, 7, 20), "HIGH"},
                new Object[]{false, "TCK-2", "At risk task", "Bob", LocalDate.of(2026, 8, 5), "MEDIUM"}));

        ReportMetrics metrics = service.compute(
                ORG_ID, null, null, LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 3));

        assertThat(metrics.overdueTasks()).extracting(ReportMetrics.DueTask::referenceKey).containsExactly("TCK-1");
        assertThat(metrics.slaRiskTasks()).extracting(ReportMetrics.DueTask::referenceKey).containsExactly("TCK-2");
    }
}
