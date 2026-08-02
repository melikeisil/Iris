package com.smartwork.integration;

import com.smartwork.identity.application.UserService;
import com.smartwork.identity.domain.SystemRole;
import com.smartwork.common.error.ApiException;
import com.smartwork.organization.application.DepartmentService;
import com.smartwork.support.IntegrationTest;
import com.smartwork.support.PostgresContainerConfig;
import com.smartwork.support.TestFixtures;
import com.smartwork.team.application.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tenancy is the boundary that must never leak. Every repository query takes
 * organizationId explicitly, so this verifies the predicate is actually applied rather
 * than trusting that it was remembered.
 */
@IntegrationTest
@Import(PostgresContainerConfig.class)
@DisplayName("Cross-organization isolation")
class CrossOrganizationIsolationIT {

    @Autowired private TestFixtures fixtures;
    @Autowired private UserService users;
    @Autowired private TeamService teams;
    @Autowired private DepartmentService departments;

    private UUID acmeId;
    private UUID rivalId;
    private UUID acmeUserId;
    private UUID acmeDepartmentId;
    private UUID acmeTeamId;

    @BeforeEach
    void seedTwoTenants() {
        acmeId = fixtures.organization("ACME-ISO");
        rivalId = fixtures.organization("RIVAL-ISO");

        acmeDepartmentId = fixtures.department(acmeId, "ENGINEERING").id();
        acmeUserId = fixtures.user(acmeId, "iso-" + UUID.randomUUID() + "@acme.io",
                Set.of(SystemRole.EMPLOYEE), acmeDepartmentId);
        acmeTeamId = teams.create(acmeId, acmeDepartmentId, "ISO" + shortId(), "Isolation Team",
                new BigDecimal("40.00")).id();

        fixtures.department(rivalId, "ENGINEERING");
    }

    @Test
    void aUserIsInvisibleFromAnotherOrganization() {
        assertThat(users.get(acmeId, acmeUserId)).isNotNull();

        // Not "forbidden" - not found. A 403 would confirm the record exists.
        assertThatThrownBy(() -> users.get(rivalId, acmeUserId))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).getCode().name()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void userSearchNeverReturnsForeignRecords() {
        var page = users.search(rivalId, null, false, null,
                PageRequest.of(0, 50, Sort.by("fullName")));

        assertThat(page.getContent()).noneMatch(summary -> summary.userId().equals(acmeUserId));
        // Totals must match the visible set too: a fetch-then-filter implementation would
        // leak existence through the pagination count.
        assertThat(page.getTotalElements()).isEqualTo(page.getContent().size());
    }

    @Test
    void aTeamIsInvisibleFromAnotherOrganization() {
        assertThat(teams.find(acmeId, acmeTeamId)).isPresent();
        assertThat(teams.find(rivalId, acmeTeamId)).isEmpty();
    }

    @Test
    void aDepartmentIsInvisibleFromAnotherOrganization() {
        assertThat(departments.find(acmeId, acmeDepartmentId)).isPresent();
        assertThat(departments.find(rivalId, acmeDepartmentId)).isEmpty();
    }

    @Test
    void aTeamCannotRecruitAMemberFromAnotherOrganization() {
        UUID rivalUserId = fixtures.user(rivalId, "rival-" + UUID.randomUUID() + "@rival.io",
                Set.of(SystemRole.EMPLOYEE), fixtures.department(rivalId, "ENGINEERING").id());

        assertThatThrownBy(() -> teams.addMember(acmeId, acmeTeamId, rivalUserId, 100, true))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).getCode().name()).isEqualTo("NOT_FOUND"));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase().replace("-", "");
    }
}
