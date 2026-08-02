package com.smartwork.reporting.application;

import com.smartwork.assignment.application.WorkloadService;
import com.smartwork.common.error.ApiException;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.reporting.api.ReportingDtos;
import com.smartwork.reporting.infrastructure.ReportingQueryRepository;
import com.smartwork.team.application.TeamDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReportingQueryService {

    private final ReportingQueryRepository queries;
    private final WorkloadService workloads;
    private final TeamDirectory teams;
    private final Clock clock;

    public ReportingQueryService(
            ReportingQueryRepository queries,
            WorkloadService workloads,
            TeamDirectory teams,
            Clock clock) {
        this.queries = queries;
        this.workloads = workloads;
        this.teams = teams;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReportingDtos.TeamWorkloadResponse teamWorkload(AuthPrincipal principal, UUID requestedTeamId) {
        if (!principal.hasRole("TEAM_LEAD") && !principal.isManager() && !principal.isAdmin()) {
            throw ApiException.forbidden("Only a Team Lead, Manager or Admin can view team workload.");
        }

        UUID teamId = resolveTeamId(principal, requestedTeamId);
        List<Object[]> rows = queries.findTeamMembers(principal.organizationId(), teamId);
        if (rows.isEmpty()) {
            throw ApiException.notFound("Team", teamId);
        }

        List<WorkloadService.CapacitySnapshot> capacitySnapshots = rows.stream()
                .map(row -> new WorkloadService.CapacitySnapshot(
                        (UUID) row[2],
                        (BigDecimal) row[5],
                        ((Number) row[6]).intValue()))
                .toList();
        Map<UUID, WorkloadService.WorkloadSnapshot> workloadByUser = workloads.calculateFor(capacitySnapshots);

        String teamName = (String) rows.getFirst()[1];
        List<ReportingDtos.TeamMemberLoadResponse> members = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            UUID userId = (UUID) row[2];
            WorkloadService.WorkloadSnapshot workload = workloadByUser.get(userId);
            members.add(new ReportingDtos.TeamMemberLoadResponse(
                    userId,
                    (String) row[3],
                    humanizeLevel((String) row[4]),
                    workload == null ? BigDecimal.ZERO : workload.capacityHours(),
                    workload == null ? BigDecimal.ZERO : workload.openHours(),
                    ((Number) row[7]).intValue(),
                    ((Number) row[8]).intValue(),
                    splitCsv((String) row[9])));
        }

        Object[] split = queries.countPlannedVsUnplannedForTeam(principal.organizationId(), teamId);
        long planned = ((Number) split[0]).longValue();
        long unplanned = ((Number) split[1]).longValue();
        BigDecimal total = BigDecimal.valueOf(Math.max(1, planned + unplanned));

        return new ReportingDtos.TeamWorkloadResponse(
                teamId,
                teamName,
                members,
                BigDecimal.valueOf(planned).divide(total, 2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(unplanned).divide(total, 2, RoundingMode.HALF_UP));
    }

    @Transactional(readOnly = true)
    public ReportingDtos.DepartmentCapacityResponse departmentCapacity(
            AuthPrincipal principal, UUID requestedDepartmentId) {

        if (!principal.isManager() && !principal.isAdmin()) {
            throw ApiException.forbidden("Only a Manager or Admin can view department capacity.");
        }

        UUID departmentId = requestedDepartmentId;
        if (!principal.organizationWide()) {
            departmentId = principal.departmentId();
        }

        List<Object[]> rows = queries.findDepartmentPrimaryMembers(principal.organizationId(), departmentId);
        if (rows.isEmpty()) {
            throw ApiException.notFound("Department", String.valueOf(departmentId));
        }

        List<WorkloadService.CapacitySnapshot> capacitySnapshots = rows.stream()
                .map(row -> new WorkloadService.CapacitySnapshot(
                        (UUID) row[4],
                        (BigDecimal) row[6],
                        ((Number) row[7]).intValue()))
                .toList();
        Map<UUID, WorkloadService.WorkloadSnapshot> workloadByUser = workloads.calculateFor(capacitySnapshots);

        UUID resolvedDepartmentId = (UUID) rows.getFirst()[0];
        String departmentName = (String) rows.getFirst()[1];

        record TeamAccumulator(UUID teamId, String teamName, BigDecimal capacity, BigDecimal assigned, int blocked) {
        }
        Map<UUID, TeamAccumulator> accumulators = new LinkedHashMap<>();
        BigDecimal totalCapacity = BigDecimal.ZERO;
        BigDecimal totalAssigned = BigDecimal.ZERO;
        for (Object[] row : rows) {
            UUID teamId = (UUID) row[2];
            String teamName = (String) row[3];
            UUID userId = (UUID) row[4];
            WorkloadService.WorkloadSnapshot workload = workloadByUser.get(userId);
            BigDecimal capacity = workload == null ? BigDecimal.ZERO : workload.capacityHours();
            BigDecimal assigned = workload == null ? BigDecimal.ZERO : workload.openHours();
            int blocked = ((Number) row[8]).intValue();

            TeamAccumulator existing = accumulators.get(teamId);
            accumulators.put(teamId, new TeamAccumulator(
                    teamId,
                    teamName,
                    (existing == null ? BigDecimal.ZERO : existing.capacity()).add(capacity),
                    (existing == null ? BigDecimal.ZERO : existing.assigned()).add(assigned),
                    (existing == null ? 0 : existing.blocked()) + blocked));

            totalCapacity = totalCapacity.add(capacity);
            totalAssigned = totalAssigned.add(assigned);
        }

        List<ReportingDtos.DepartmentTeamCapacityResponse> teams = accumulators.values().stream()
                .map(team -> new ReportingDtos.DepartmentTeamCapacityResponse(
                        team.teamId(),
                        team.teamName(),
                        team.capacity().setScale(2, RoundingMode.HALF_UP),
                        team.assigned().setScale(2, RoundingMode.HALF_UP),
                        ratio(team.assigned(), team.capacity()),
                        team.blocked()))
                .toList();

        return new ReportingDtos.DepartmentCapacityResponse(
                resolvedDepartmentId,
                departmentName,
                totalCapacity.setScale(2, RoundingMode.HALF_UP),
                totalAssigned.setScale(2, RoundingMode.HALF_UP),
                ratio(totalAssigned, totalCapacity),
                teams);
    }

    @Transactional(readOnly = true)
    public ReportingDtos.AiAnalyticsResponse aiAnalytics(AuthPrincipal principal) {
        if (!principal.isManager() && !principal.isAdmin()) {
            throw ApiException.forbidden("Only a Manager or Admin can view AI analytics.");
        }

        UUID departmentId = principal.organizationWide() ? null : principal.departmentId();
        Instant since = clock.instant().minus(Duration.ofDays(30));

        Object[] rates = queries.countAssignmentDecisionRates(principal.organizationId(), departmentId, since);
        long accepted = ((Number) rates[0]).longValue();
        long overridden = ((Number) rates[1]).longValue();
        long decided = ((Number) rates[2]).longValue();
        long autoAssigned = ((Number) rates[3]).longValue();
        long routed = ((Number) rates[4]).longValue();

        Object[] plannedVsUnplanned = queries.countPlannedVsUnplannedForDepartment(
                principal.organizationId(), departmentId, since);

        return new ReportingDtos.AiAnalyticsResponse(
                fraction(accepted, decided),
                fraction(overridden, decided),
                fraction(autoAssigned, routed),
                queries.averageDecisionLatencyMinutes(principal.organizationId(), departmentId, since),
                new ReportingDtos.PlannedVsUnplannedResponse(
                        ((Number) plannedVsUnplanned[0]).longValue(),
                        ((Number) plannedVsUnplanned[1]).longValue()),
                queries.countPolicyExceptions(principal.organizationId(), departmentId, since));
    }

    private UUID resolveTeamId(AuthPrincipal principal, UUID requestedTeamId) {
        if (requestedTeamId != null) {
            if (principal.isManager() || principal.isAdmin() || principal.isMemberOf(requestedTeamId)) {
                return requestedTeamId;
            }
            throw ApiException.forbidden("You cannot view workload for a team outside your scope.");
        }

        if (!principal.teamIds().isEmpty()) {
            return principal.teamIds().iterator().next();
        }

        Set<UUID> led = teams.teamIdsLedBy(principal.organizationId(), principal.userId());
        if (!led.isEmpty()) {
            return led.iterator().next();
        }
        throw ApiException.validation("A teamId is required for this caller.", List.of());
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return List.of(csv.split(","));
    }

    private static String humanizeLevel(String level) {
        return switch (level) {
            case "LEAD" -> "Lead";
            case "SENIOR" -> "Senior";
            case "MID" -> "Mid";
            case "JUNIOR" -> "Junior";
            case "INTERN" -> "Intern";
            default -> level;
        };
    }

    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal fraction(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
