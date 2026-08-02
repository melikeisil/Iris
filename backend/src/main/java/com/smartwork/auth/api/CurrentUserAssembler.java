package com.smartwork.auth.api;

import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.organization.application.DepartmentDirectory;
import com.smartwork.team.application.TeamDirectory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the {@code /auth/me} payload, including the coarse capability flags the frontend
 * uses to decide which controls to render.
 */
@Component
public class CurrentUserAssembler {

    private final DepartmentDirectory departments;
    private final TeamDirectory teams;

    public CurrentUserAssembler(DepartmentDirectory departments, TeamDirectory teams) {
        this.departments = departments;
        this.teams = teams;
    }

    public AuthDtos.CurrentUserResponse assemble(AuthPrincipal principal, String fullName, String email) {
        AuthDtos.DepartmentRef department = principal.departmentId() == null ? null
                : departments.find(principal.organizationId(), principal.departmentId())
                        .map(view -> new AuthDtos.DepartmentRef(view.id(), view.code(), view.name()))
                        .orElse(null);

        Set<UUID> ledTeamIds = teams.teamIdsLedBy(principal.organizationId(), principal.userId());

        List<AuthDtos.TeamRef> teamRefs = principal.teamIds().stream()
                .map(teamId -> teams.find(principal.organizationId(), teamId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(view -> new AuthDtos.TeamRef(view.id(), view.code(), view.name(),
                        ledTeamIds.contains(view.id())))
                .toList();

        return new AuthDtos.CurrentUserResponse(
                principal.userId(), email, fullName, principal.employeeLevel(),
                principal.organizationId(), department, teamRefs, principal.roles(),
                principal.organizationWide(), capabilities(principal, !ledTeamIds.isEmpty()));
    }

    /**
     * Coarse feature flags. Per-item permissions travel with each work item response, so
     * these only decide which sections of the navigation exist.
     */
    private List<String> capabilities(AuthPrincipal principal, boolean leadsATeam) {
        List<String> capabilities = new ArrayList<>();
        capabilities.add("work_item.create");
        capabilities.add("work_item.view_own");

        if (principal.hasRole("TEAM_LEAD") || leadsATeam) {
            capabilities.add("team.dashboard");
            capabilities.add("team.review_queue");
            capabilities.add("team.assign");
            capabilities.add("team.backlog");
            capabilities.add("sprint.manage");
        }
        if (principal.hasRole("MANAGER")) {
            capabilities.add("manager.dashboard");
            capabilities.add("manager.escalations");
            capabilities.add("manager.capacity");
            capabilities.add("manager.reports");
            capabilities.add("policy.thresholds");
        }
        if (principal.isAdmin()) {
            capabilities.add("admin.users");
            capabilities.add("admin.organization");
            capabilities.add("admin.skills");
            capabilities.add("admin.projects");
            capabilities.add("admin.jira");
            capabilities.add("admin.audit");
        }
        return List.copyOf(capabilities);
    }
}
