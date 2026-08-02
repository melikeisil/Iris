package com.smartwork.team.application;

import com.smartwork.common.error.ApiException;
import com.smartwork.identity.application.UserDirectory;
import com.smartwork.organization.application.DepartmentDirectory;
import com.smartwork.team.domain.Team;
import com.smartwork.team.domain.TeamMember;
import com.smartwork.team.infrastructure.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TeamService implements TeamDirectory {

    private final TeamRepository teams;
    private final DepartmentDirectory departments;
    private final UserDirectory users;
    private final Clock clock;

    public TeamService(TeamRepository teams, DepartmentDirectory departments,
                       UserDirectory users, Clock clock) {
        this.teams = teams;
        this.departments = departments;
        this.users = users;
        this.clock = clock;
    }

    @Override
    public Set<UUID> teamIdsOf(UUID organizationId, UUID userId) {
        return new LinkedHashSet<>(teams.findTeamIdsOfUser(userId, organizationId));
    }

    @Override
    public Set<UUID> teamIdsLedBy(UUID organizationId, UUID userId) {
        return new LinkedHashSet<>(teams.findTeamIdsLedBy(userId, organizationId));
    }

    @Override
    public Optional<TeamView> find(UUID organizationId, UUID teamId) {
        return teams.findByIdInOrganization(teamId, organizationId).map(TeamService::toView);
    }

    @Override
    public List<TeamView> listAll(UUID organizationId, UUID departmentId) {
        return teams.findAllInOrganization(organizationId, departmentId).stream()
                .map(TeamService::toView)
                .toList();
    }

    @Override
    public List<MemberView> membersOf(UUID organizationId, UUID teamId) {
        Team team = requireTeam(organizationId, teamId);
        return team.getMembers().stream()
                .map(member -> new MemberView(member.getUserId(),
                        member.getAllocationPercent(), member.isPrimaryTeam()))
                .toList();
    }

    @Transactional
    public TeamView create(UUID organizationId, UUID departmentId, String code, String name,
                           BigDecimal weeklyCapacityHours) {
        departments.require(organizationId, departmentId);

        String normalized = code.trim().toUpperCase();
        if (teams.existsByOrganizationIdAndCode(organizationId, normalized)) {
            throw ApiException.duplicate("A team with code '" + normalized + "' already exists.");
        }

        Team team = new Team(organizationId, departmentId, normalized, name);
        team.update(name, null, weeklyCapacityHours, true);
        return toView(teams.save(team));
    }

    @Transactional
    public TeamView update(UUID organizationId, UUID teamId, String name, UUID leadUserId,
                           BigDecimal weeklyCapacityHours, boolean active) {
        Team team = requireTeam(organizationId, teamId);
        if (leadUserId != null && users.findSummary(organizationId, leadUserId).isEmpty()) {
            throw ApiException.notFound("User", leadUserId);
        }
        team.update(name, leadUserId, weeklyCapacityHours, active);
        return toView(team);
    }

    @Transactional
    public MemberView addMember(UUID organizationId, UUID teamId, UUID userId,
                                int allocationPercent, boolean primaryTeam) {
        Team team = requireTeam(organizationId, teamId);
        if (users.findSummary(organizationId, userId).isEmpty()) {
            throw ApiException.notFound("User", userId);
        }
        try {
            TeamMember member = team.addMember(userId, allocationPercent, primaryTeam);
            return new MemberView(member.getUserId(), member.getAllocationPercent(), member.isPrimaryTeam());
        } catch (IllegalStateException alreadyMember) {
            throw ApiException.duplicate(alreadyMember.getMessage());
        }
    }

    @Transactional
    public void removeMember(UUID organizationId, UUID teamId, UUID userId) {
        Team team = requireTeam(organizationId, teamId);
        if (!team.removeMember(userId)) {
            throw ApiException.notFound("Team membership for user", userId);
        }
    }

    @Transactional
    public void delete(UUID organizationId, UUID teamId) {
        requireTeam(organizationId, teamId).softDelete(clock.instant());
    }

    private Team requireTeam(UUID organizationId, UUID teamId) {
        return teams.findByIdInOrganization(teamId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Team", teamId));
    }

    private static TeamView toView(Team team) {
        return new TeamView(team.getId(), team.getOrganizationId(), team.getDepartmentId(),
                team.getCode(), team.getName(), team.getLeadUserId(),
                team.getWeeklyCapacityHours(), team.isActive(), team.getMembers().size());
    }
}
