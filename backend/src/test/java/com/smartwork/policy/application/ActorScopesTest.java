package com.smartwork.policy.application;

import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.policy.domain.ApproverScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActorScopesTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID DEPARTMENT = UUID.randomUUID();
    private static final UUID TEAM = UUID.randomUUID();

    private static AuthPrincipal principal(Set<String> roles, boolean organizationWide) {
        return new AuthPrincipal(
                UUID.randomUUID(), ORG, DEPARTMENT, Set.of(TEAM), roles, "MID", organizationWide);
    }

    @Test
    @DisplayName("everyone can be asked a question directly, regardless of role")
    void everyoneHasTheirOwnPersonScope() {
        AuthPrincipal employee = principal(Set.of("EMPLOYEE"), false);

        assertThat(ActorScopes.of(employee))
                .as("REQUEST_MORE_INFORMATION must reach a plain employee")
                .contains(new ApproverScope.Person(employee.userId()).serialize());
    }

    @Test
    void aManagerSeesTheManagerRoleScope() {
        assertThat(ActorScopes.of(principal(Set.of("EMPLOYEE", "MANAGER"), true)))
                .contains("ROLE:MANAGER");
    }

    @Test
    void anEmployeeDoesNotSeeTheManagerRoleScope() {
        assertThat(ActorScopes.of(principal(Set.of("EMPLOYEE"), false)))
                .doesNotContain("ROLE:MANAGER");
    }

    @Test
    void anAdminSeesBothAdminAndManagerScopes() {
        assertThat(ActorScopes.of(principal(Set.of("ADMIN"), false)))
                .contains("ROLE:ADMIN", "ROLE:MANAGER");
    }

    @Test
    void teamAndDepartmentScopesComeFromThePrincipal() {
        assertThat(ActorScopes.of(principal(Set.of("EMPLOYEE", "TEAM_LEAD"), false)))
                .contains("TEAM:" + TEAM, "DEPT:" + DEPARTMENT);
    }

    @Test
    @DisplayName("seeing a team request is not the same as being able to answer it")
    void decidingATeamRequestRequiresLeadingThatTeam() {
        AuthPrincipal member = principal(Set.of("EMPLOYEE"), false);
        ApproverScope teamScope = new ApproverScope.Team(TEAM);

        // Visible in the shared queue...
        assertThat(ActorScopes.of(member)).contains(teamScope.serialize());
        // ...but not answerable.
        assertThat(ActorScopes.mayDecide(member, teamScope, false)).isFalse();
        assertThat(ActorScopes.mayDecide(member, teamScope, true)).isTrue();
    }

    @Test
    void aManagerMayDecideATeamRequestWithoutLeadingTheTeam() {
        AuthPrincipal manager = principal(Set.of("EMPLOYEE", "MANAGER"), true);
        assertThat(ActorScopes.mayDecide(manager, new ApproverScope.Team(TEAM), false)).isTrue();
    }

    @Test
    void onlyTheAddresseeMayDecideAPersonScopedRequest() {
        AuthPrincipal employee = principal(Set.of("EMPLOYEE"), false);
        AuthPrincipal other = principal(Set.of("EMPLOYEE", "TEAM_LEAD"), false);

        ApproverScope addressed = new ApproverScope.Person(employee.userId());

        assertThat(ActorScopes.mayDecide(employee, addressed, false)).isTrue();
        assertThat(ActorScopes.mayDecide(other, addressed, true))
                .as("a team lead cannot answer a question asked of someone else")
                .isFalse();
    }

    @Test
    @DisplayName("a department-scoped request needs a manager for that department")
    void departmentScopeRespectsTheManagersOwnDepartment() {
        AuthPrincipal scopedManager = principal(Set.of("MANAGER"), false);
        AuthPrincipal orgWideManager = principal(Set.of("MANAGER"), true);

        assertThat(ActorScopes.mayDecide(
                scopedManager, new ApproverScope.Department(DEPARTMENT), false)).isTrue();
        assertThat(ActorScopes.mayDecide(
                scopedManager, new ApproverScope.Department(UUID.randomUUID()), false))
                .as("a department manager has no authority over another department")
                .isFalse();
        assertThat(ActorScopes.mayDecide(
                orgWideManager, new ApproverScope.Department(UUID.randomUUID()), false))
                .isTrue();
    }

    @Test
    void anAdminMayDecideAnything() {
        AuthPrincipal admin = principal(Set.of("ADMIN"), false);

        assertThat(ActorScopes.mayDecide(admin, new ApproverScope.ManagerRole(), false)).isTrue();
        assertThat(ActorScopes.mayDecide(admin, new ApproverScope.Team(UUID.randomUUID()), false)).isTrue();
        assertThat(ActorScopes.mayDecide(admin, new ApproverScope.Person(UUID.randomUUID()), false)).isTrue();
    }

    @Test
    void anEmployeeMayNotDecideAManagerScopedRequest() {
        assertThat(ActorScopes.mayDecide(
                principal(Set.of("EMPLOYEE"), false), new ApproverScope.ManagerRole(), false))
                .isFalse();
    }
}
