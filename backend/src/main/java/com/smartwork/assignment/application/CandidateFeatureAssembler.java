package com.smartwork.assignment.application;

import com.smartwork.assignment.domain.CandidateFeatures;
import com.smartwork.assignment.domain.WorkloadCalculator;
import com.smartwork.assignment.infrastructure.AssignmentRepository;
import com.smartwork.assignment.infrastructure.CandidateQueryRepository;
import com.smartwork.assignment.infrastructure.CandidateQueryRepository.PoolMember;
import com.smartwork.identity.domain.EmployeeLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Assembles the pure {@link CandidateFeatures} records from the batch query results.
 *
 * <p>Every field this class can populate is one the scorer is permitted to see. It reads
 * from {@code users}, {@code user_skills}, {@code team_members}, {@code work_items} and
 * {@code assignments} — none of which carries a protected characteristic, because
 * {@code ProtectedCharacteristicsSchemaIT} fails the build if such a column is ever added.
 */
@Service
public class CandidateFeatureAssembler {

    private static final int EXPERIENCE_WINDOW_DAYS = 180;
    private static final int FAIRNESS_WINDOW_DAYS = 14;

    private final CandidateQueryRepository candidateQueries;
    private final AssignmentRepository assignmentRepository;
    private final WorkloadService workloadService;
    private final Clock clock;

    public CandidateFeatureAssembler(
            CandidateQueryRepository candidateQueries,
            AssignmentRepository assignmentRepository,
            WorkloadService workloadService,
            Clock clock) {
        this.candidateQueries = candidateQueries;
        this.assignmentRepository = assignmentRepository;
        this.workloadService = workloadService;
        this.clock = clock;
    }

    /** Item-side facts the assembler needs that are not on the {@code WorkItemContext}. */
    public record ItemFacts(
            UUID reporterId,
            UUID projectId,
            UUID recommendedTeamId,
            UUID recommendedDepartmentId,
            Collection<String> requiredSkillCodes,
            Collection<String> mandatorySkillCodes,
            Collection<String> affectedComponents,
            LocalDate dueDate) {
    }

    @Transactional(readOnly = true)
    public List<CandidateFeatures> assemble(List<PoolMember> pool, ItemFacts facts) {
        if (pool.isEmpty()) {
            return List.of();
        }
        Instant now = clock.instant();
        LocalDate experienceSince = LocalDate.ofInstant(now, ZoneOffset.UTC).minusDays(EXPERIENCE_WINDOW_DAYS);
        List<UUID> userIds = pool.stream().map(PoolMember::userId).toList();

        Map<UUID, Map<String, Integer>> proficiency = new HashMap<>();
        Map<UUID, Map<String, Integer>> recency = new HashMap<>();
        loadSkillProfiles(userIds, now, proficiency, recency);

        Map<UUID, WorkloadService.WorkloadSnapshot> workload = workloadService.calculateFor(
                pool.stream()
                        .map(m -> new WorkloadService.CapacitySnapshot(
                                m.userId(), m.weeklyCapacityHours(), m.allocationPercent()))
                        .toList());

        Map<UUID, Integer> activeCounts = candidateQueries.countActiveItems(userIds);
        Map<UUID, Integer> similar = toCountMap(
                candidateQueries.countSimilarCompleted(userIds, facts.requiredSkillCodes(), experienceSince));
        Map<UUID, Integer> componentExperience = toCountMap(
                candidateQueries.countComponentExperience(userIds, facts.affectedComponents(), experienceSince));
        Set<UUID> workedInProject = new HashSet<>(
                candidateQueries.findWorkedInProject(userIds, facts.projectId(), experienceSince));
        Map<UUID, Integer> assignedRecently = toCountMap(assignmentRepository.countAssignedSince(
                userIds, now.minus(Duration.ofDays(FAIRNESS_WINDOW_DAYS))));

        Set<String> mandatory = new HashSet<>(facts.mandatorySkillCodes());

        List<CandidateFeatures> features = new ArrayList<>(pool.size());
        for (PoolMember member : pool) {
            Map<String, Integer> proficiencies = proficiency.getOrDefault(member.userId(), Map.of());
            WorkloadService.WorkloadSnapshot load = workload.get(member.userId());

            features.add(new CandidateFeatures(
                    member.userId(),
                    member.fullName(),
                    proficiencies,
                    recency.getOrDefault(member.userId(), Map.of()),
                    member.inRecommendedTeam(),
                    facts.recommendedDepartmentId() != null
                            && facts.recommendedDepartmentId().equals(member.departmentId()),
                    holdsAll(proficiencies, mandatory),
                    load == null ? null : load.ratio(),
                    availableHoursBeforeDue(facts.dueDate(), load, now),
                    similar.getOrDefault(member.userId(), 0),
                    // No reopen tracking exists before Phase 9's reporting views, so the
                    // quality factor is neutral rather than guessed. See ADR-036.
                    0,
                    componentExperience.getOrDefault(member.userId(), 0),
                    workedInProject.contains(member.userId()),
                    assignedRecently.getOrDefault(member.userId(), 0),
                    member.active(),
                    EmployeeLevel.valueOf(member.employeeLevel()),
                    activeCounts.getOrDefault(member.userId(), 0),
                    member.userId().equals(facts.reporterId())));
        }
        return features;
    }

    private void loadSkillProfiles(
            List<UUID> userIds, Instant now,
            Map<UUID, Map<String, Integer>> proficiency,
            Map<UUID, Map<String, Integer>> recency) {

        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        for (Object[] row : candidateQueries.findSkillProfiles(userIds)) {
            UUID userId = (UUID) row[0];
            String skillCode = (String) row[1];
            int level = ((Number) row[2]).intValue();
            proficiency.computeIfAbsent(userId, key -> new HashMap<>()).put(skillCode, level);

            if (row[3] != null) {
                LocalDate lastUsed = ((java.sql.Date) row[3]).toLocalDate();
                int days = (int) java.time.temporal.ChronoUnit.DAYS.between(lastUsed, today);
                recency.computeIfAbsent(userId, key -> new HashMap<>()).put(skillCode, Math.max(0, days));
            }
        }
    }

    /**
     * Working hours the candidate has left before the due date, after existing commitments.
     * Approximated from their remaining capacity in the standard two-week window, scaled to
     * the days actually available — precise enough to separate "comfortable" from "no
     * chance", which is all the deadline score needs.
     */
    private BigDecimal availableHoursBeforeDue(
            LocalDate dueDate, WorkloadService.WorkloadSnapshot load, Instant now) {

        if (dueDate == null || load == null) {
            return null;
        }
        long daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.ofInstant(now, ZoneOffset.UTC), dueDate);
        if (daysUntilDue <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal free = load.capacityHours().subtract(load.openHours());
        if (free.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal windowFraction = BigDecimal.valueOf(Math.min(daysUntilDue, WorkloadCalculator.WINDOW_WORKING_DAYS))
                .divide(BigDecimal.valueOf(WorkloadCalculator.WINDOW_WORKING_DAYS), 4, java.math.RoundingMode.HALF_UP);
        return free.multiply(windowFraction).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static boolean holdsAll(Map<String, Integer> proficiencies, Set<String> mandatory) {
        return mandatory.stream().allMatch(code -> proficiencies.getOrDefault(code, 0) > 0);
    }

    private static Map<UUID, Integer> toCountMap(List<Object[]> rows) {
        Map<UUID, Integer> out = new HashMap<>();
        for (Object[] row : rows) {
            out.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return out;
    }
}
