package com.smartwork.support;

import com.smartwork.identity.application.UserService;
import com.smartwork.identity.domain.EmployeeLevel;
import com.smartwork.identity.domain.SystemRole;
import com.smartwork.organization.application.DepartmentDirectory;
import com.smartwork.organization.application.DepartmentService;
import com.smartwork.organization.domain.Organization;
import com.smartwork.organization.infrastructure.OrganizationRepository;
import com.smartwork.project.application.ProjectService;
import com.smartwork.team.application.TeamService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/** Builds the minimum organizational structure a test needs. */
@Component
public class TestFixtures {

    public static final String PASSWORD = "TestPassword123!";

    private final OrganizationRepository organizations;
    private final DepartmentService departments;
    private final UserService users;
    private final TeamService teams;
    private final ProjectService projects;

    public TestFixtures(OrganizationRepository organizations, DepartmentService departments,
                        UserService users, TeamService teams, ProjectService projects) {
        this.organizations = organizations;
        this.departments = departments;
        this.users = users;
        this.teams = teams;
        this.projects = projects;
    }

    @Transactional
    public UUID organization(String code) {
        return organizations.findByCode(code)
                .orElseGet(() -> organizations.save(new Organization(code, code + " Inc.", "UTC")))
                .getId();
    }

    @Transactional
    public DepartmentDirectory.DepartmentView department(UUID organizationId, String code) {
        return departments.findByCode(organizationId, code)
                .orElseGet(() -> departments.create(organizationId, code, code + " Department"));
    }

    @Transactional
    public UUID user(UUID organizationId, String email, Set<SystemRole> roles, UUID departmentId) {
        return user(organizationId, email, roles, departmentId, EmployeeLevel.MID, false);
    }

    @Transactional
    public UUID user(UUID organizationId, String email, Set<SystemRole> roles, UUID departmentId,
                     EmployeeLevel level, boolean organizationWide) {
        return users.create(organizationId, new UserService.CreateUserCommand(
                email, PASSWORD, "Test " + email, level, new BigDecimal("40.00"),
                departmentId, organizationWide, roles, null)).userId();
    }

    /** A team with a unique code, led by {@code leadUserId}. */
    @Transactional
    public UUID team(UUID organizationId, UUID departmentId, UUID leadUserId) {
        UUID teamId = teams.create(organizationId, departmentId, "T" + shortCode(),
                "Test Team", new BigDecimal("120.00")).id();
        teams.update(organizationId, teamId, "Test Team", leadUserId, new BigDecimal("120.00"), true);
        teams.addMember(organizationId, teamId, leadUserId, 100, true);
        return teamId;
    }

    @Transactional
    public void addToTeam(UUID organizationId, UUID teamId, UUID userId) {
        teams.addMember(organizationId, teamId, userId, 100, false);
    }

    /** A project whose key prefix is IDP, so reference keys are recognisable in assertions. */
    @Transactional
    public UUID project(UUID organizationId, UUID departmentId) {
        return projects.listAll(organizationId).stream()
                .filter(project -> project.keyPrefix().equals("IDP"))
                .findFirst()
                .map(project -> project.id())
                .orElseGet(() -> projects.create(organizationId, "IDENTITY_PLATFORM",
                        "Identity Platform", "IDP", departmentId, null).id());
    }

    private static String shortCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase().replace("-", "");
    }
}
