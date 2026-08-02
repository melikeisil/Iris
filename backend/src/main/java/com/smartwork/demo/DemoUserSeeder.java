package com.smartwork.demo;

import com.smartwork.identity.application.UserDirectory;
import com.smartwork.identity.application.UserService;
import com.smartwork.identity.domain.EmployeeLevel;
import com.smartwork.identity.domain.SystemRole;
import com.smartwork.organization.application.DepartmentDirectory;
import com.smartwork.team.application.TeamDirectory;
import com.smartwork.team.application.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Seeds demo people.
 *
 * <p>Users are created here rather than in V900__demo_data.sql because passwords must be
 * Argon2id-hashed and SQL cannot do that. Structure (organization, departments, teams,
 * skills, projects) still comes from the migration.
 *
 * <p>Idempotent by <em>asking</em>, not by catching. An earlier version wrapped the whole
 * seed in one {@code @Transactional} method and treated a duplicate-user
 * {@code ApiException} as "already seeded". That fails in a way worth remembering:
 * {@code UserService.create} is itself transactional and joins the caller's transaction,
 * so when it threw, Spring marked the <em>shared</em> transaction rollback-only. Catching
 * the exception hid that, the method returned normally, and the commit then died with
 * {@code UnexpectedRollbackException} — an error naming neither the user nor the cause.
 *
 * <p>Two changes prevent the whole class of bug. Existence is checked with a query instead
 * of inferred from an exception, and each person is seeded in their own transaction, so a
 * failure on one account cannot poison the other nineteen.
 */
@Component
@Profile("demo")
// Must run before DemoPlanningSeeder (@Order(100)), which needs these users to exist
// before it can attach skills and template process owners to them. Without an explicit
// order this listener defaults to LOWEST_PRECEDENCE and runs second, which silently
// produces an organization of people with no skills.
@org.springframework.core.annotation.Order(10)
public class DemoUserSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);
    private static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-7000-8000-00000000a001");

    private final UserService users;
    private final UserDirectory userDirectory;
    private final TeamService teams;
    private final DepartmentDirectory departments;
    private final DemoProperties properties;

    public DemoUserSeeder(UserService users, UserDirectory userDirectory, TeamService teams,
                          DepartmentDirectory departments, DemoProperties properties) {
        this.users = users;
        this.userDirectory = userDirectory;
        this.teams = teams;
        this.departments = departments;
        this.properties = properties;
    }

    /**
     * Deliberately <em>not</em> {@code @Transactional}. Each call below opens its own
     * transaction, which is what makes the seeder both restartable and diagnosable: a
     * failure on one person is logged with that person's email and the remaining roster
     * still gets seeded, instead of every account vanishing on rollback.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        Map<String, UUID> departmentIds = departments.listAll(ORGANIZATION_ID).stream()
                .collect(java.util.stream.Collectors.toMap(
                        DepartmentDirectory.DepartmentView::code,
                        DepartmentDirectory.DepartmentView::id));

        if (departmentIds.isEmpty()) {
            log.warn("Demo profile is active but no departments exist. "
                    + "Is db/demo on the Flyway path? Skipping user seeding.");
            return;
        }

        Map<String, UUID> teamIds = teams.listAll(ORGANIZATION_ID, null).stream()
                .collect(java.util.stream.Collectors.toMap(
                        TeamDirectory.TeamView::code, TeamDirectory.TeamView::id));

        int created = 0;
        int failed = 0;
        for (DemoPerson person : roster()) {
            try {
                Optional<UUID> existingId = findByEmail(person.email());
                UUID userId;
                if (existingId.isPresent()) {
                    userId = existingId.get();
                } else {
                    userId = create(person, departmentIds);
                    created++;
                }
                ensureTeamMembership(person, userId, teamIds);
            } catch (RuntimeException e) {
                // Safe to catch here precisely because there is no enclosing transaction
                // to mark rollback-only. The failed person's own transaction has already
                // rolled back on its own.
                failed++;
                log.error("Could not seed demo user {}: {}", person.email(), e.toString());
            }
        }

        log.info("Demo users: {} created, {} already present, {} failed.",
                created, roster().size() - created - failed, failed);
    }

    private Optional<UUID> findByEmail(String email) {
        return userDirectory.findForAuthentication(email)
                .map(UserDirectory.AuthenticationView::userId);
    }

    private UUID create(DemoPerson person, Map<String, UUID> departmentIds) {
        return users.create(ORGANIZATION_ID, new UserService.CreateUserCommand(
                person.email(), properties.defaultPassword(), person.fullName(),
                person.level(), person.capacityHours(), departmentIds.get(person.departmentCode()),
                person.organizationWide(), person.roles(), null)).userId();
    }

    /**
     * Adds the person to their team only if they are not already in it.
     *
     * <p>Checked rather than attempted-and-caught for the same reason as the user itself,
     * and for one more: on a re-run where the user already exists, the old code skipped
     * team membership entirely. If a previous run had died between creating the user and
     * adding them to a team, that gap was permanent.
     */
    private void ensureTeamMembership(DemoPerson person, UUID userId, Map<String, UUID> teamIds) {
        UUID teamId = teamIds.get(person.teamCode());
        if (teamId == null) {
            log.warn("Demo team {} not found for {}", person.teamCode(), person.email());
            return;
        }
        boolean alreadyMember = teams.membersOf(ORGANIZATION_ID, teamId).stream()
                .anyMatch(member -> member.userId().equals(userId));
        if (!alreadyMember) {
            teams.addMember(ORGANIZATION_ID, teamId, userId, 100, true);
        }
    }

    /**
     * Note the HR Operations Lead: an EMPLOYEE, not a TEAM_LEAD. They are the Process
     * Owner of the onboarding scenario, which is how the demo shows that PROCESS_OWNER is
     * a per-resource relationship rather than a global role.
     */
    private List<DemoPerson> roster() {
        BigDecimal full = new BigDecimal("40.00");
        BigDecimal part = new BigDecimal("20.00");

        return List.of(
                new DemoPerson("elif.yilmaz@acme.io", "Elif Yılmaz", EmployeeLevel.LEAD, full,
                        "ENGINEERING", "PLATFORM", Set.of(SystemRole.EMPLOYEE, SystemRole.MANAGER), true),
                new DemoPerson("admin@acme.io", "System Administrator", EmployeeLevel.SENIOR, full,
                        "IT", "SUPPORT", Set.of(SystemRole.ADMIN), false),

                new DemoPerson("ayse.demir@acme.io", "Ayşe Demir", EmployeeLevel.SENIOR, full,
                        "ENGINEERING", "BACKEND", Set.of(SystemRole.EMPLOYEE, SystemRole.TEAM_LEAD), false),
                new DemoPerson("kerem.sahin@acme.io", "Kerem Şahin", EmployeeLevel.SENIOR, full,
                        "ENGINEERING", "FRONTEND", Set.of(SystemRole.EMPLOYEE, SystemRole.TEAM_LEAD), false),
                new DemoPerson("burak.aydin@acme.io", "Burak Aydın", EmployeeLevel.LEAD, full,
                        "ENGINEERING", "PLATFORM", Set.of(SystemRole.EMPLOYEE, SystemRole.TEAM_LEAD), false),
                new DemoPerson("sibel.kaya@acme.io", "Sibel Kaya", EmployeeLevel.SENIOR, full,
                        "IT", "SUPPORT", Set.of(SystemRole.EMPLOYEE, SystemRole.TEAM_LEAD), false),

                new DemoPerson("zeynep.koc@acme.io", "Zeynep Koç", EmployeeLevel.SENIOR, full,
                        "HR", "HR_OPS", Set.of(SystemRole.EMPLOYEE), false),
                new DemoPerson("okan.tas@acme.io", "Okan Taş", EmployeeLevel.MID, full,
                        "IT", "SUPPORT", Set.of(SystemRole.EMPLOYEE), false),

                new DemoPerson("mert.kaya@acme.io", "Mert Kaya", EmployeeLevel.MID, full,
                        "ENGINEERING", "BACKEND", Set.of(SystemRole.EMPLOYEE), false),
                new DemoPerson("deniz.aslan@acme.io", "Deniz Aslan", EmployeeLevel.SENIOR, full,
                        "ENGINEERING", "BACKEND", Set.of(SystemRole.EMPLOYEE), false),
                new DemoPerson("ece.yildiz@acme.io", "Ece Yıldız", EmployeeLevel.JUNIOR, full,
                        "ENGINEERING", "BACKEND", Set.of(SystemRole.EMPLOYEE), false),
                new DemoPerson("baris.ozdemir@acme.io", "Barış Özdemir", EmployeeLevel.MID, full,
                        "ENGINEERING", "FRONTEND", Set.of(SystemRole.EMPLOYEE), false),
                new DemoPerson("nil.arslan@acme.io", "Nil Arslan", EmployeeLevel.JUNIOR, full,
                        "ENGINEERING", "FRONTEND", Set.of(SystemRole.EMPLOYEE), false),
                new DemoPerson("cem.polat@acme.io", "Cem Polat", EmployeeLevel.SENIOR, full,
                        "ENGINEERING", "PLATFORM", Set.of(SystemRole.EMPLOYEE), false),
                new DemoPerson("selin.gunes@acme.io", "Selin Güneş", EmployeeLevel.MID, part,
                        "FINANCE", "FINANCE_OPS", Set.of(SystemRole.EMPLOYEE), false),
                new DemoPerson("tolga.eren@acme.io", "Tolga Eren", EmployeeLevel.MID, full,
                        "HR", "HR_OPS", Set.of(SystemRole.EMPLOYEE), false),
                new DemoPerson("irem.acar@acme.io", "İrem Acar", EmployeeLevel.MID, full,
                        "IT", "SUPPORT", Set.of(SystemRole.EMPLOYEE), false),
                new DemoPerson("murat.celik@acme.io", "Murat Çelik", EmployeeLevel.SENIOR, full,
                        "IT", "SUPPORT", Set.of(SystemRole.EMPLOYEE), false),

                new DemoPerson("can.ozturk@acme.io", "Can Öztürk", EmployeeLevel.INTERN, part,
                        "ENGINEERING", "BACKEND", Set.of(SystemRole.EMPLOYEE), false),
                new DemoPerson("lale.yalcin@acme.io", "Lale Yalçın", EmployeeLevel.INTERN, part,
                        "IT", "SUPPORT", Set.of(SystemRole.EMPLOYEE), false));
    }

    private record DemoPerson(String email, String fullName, EmployeeLevel level,
                              BigDecimal capacityHours, String departmentCode, String teamCode,
                              Set<SystemRole> roles, boolean organizationWide) {
    }
}
