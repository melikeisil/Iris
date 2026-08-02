package com.smartwork.team.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read contract for teams. Used by auth to build a principal's team scope. */
public interface TeamDirectory {

    Set<UUID> teamIdsOf(UUID organizationId, UUID userId);

    Set<UUID> teamIdsLedBy(UUID organizationId, UUID userId);

    Optional<TeamView> find(UUID organizationId, UUID teamId);

    List<TeamView> listAll(UUID organizationId, UUID departmentId);

    List<MemberView> membersOf(UUID organizationId, UUID teamId);

    record TeamView(UUID id, UUID organizationId, UUID departmentId, String code, String name,
                    UUID leadUserId, BigDecimal weeklyCapacityHours, boolean active, int memberCount) {
    }

    record MemberView(UUID userId, int allocationPercent, boolean primaryTeam) {
    }
}
