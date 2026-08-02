package com.smartwork.demo;

import com.smartwork.identity.application.SkillService;
import com.smartwork.identity.application.UserDirectory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 5 demo data that needs users to exist: skill proficiencies, template process
 * owners, and the open work that produces interesting workload ratios.
 *
 * <p>Runs after {@link DemoUserSeeder} (hence the {@code @Order}) because every row here
 * references a user id, and demo users are created in Java rather than SQL — their
 * passwords must be Argon2id-hashed, which a migration cannot do.
 *
 * <p>The skill matrix is engineered, not random. docs/14 §3 asks for proficiencies that
 * "produce interesting near-ties", and Demo 2 step 7 specifically requires exactly one
 * person to hold {@code OBJECT_STORAGE} while sitting at a high workload — that is what
 * makes the storage task route to a Team Lead instead of auto-assigning. Randomised demo
 * data would produce a different story every run.
 */
@Component
@Profile("demo")
@Order(100)
public class DemoPlanningSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoPlanningSeeder.class);
    private static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-7000-8000-00000000a001");
    private static final UUID ONBOARDING_TEMPLATE_ID =
            UUID.fromString("00000000-0000-7000-8000-00000000f001");
    private static final String HR_OPERATIONS_LEAD = "zeynep.koc@acme.io";
    private static final String IT_SERVICE_LEAD = "murat.celik@acme.io";

    private final UserDirectory users;
    private final SkillService skills;

    @PersistenceContext
    private EntityManager entityManager;

    public DemoPlanningSeeder(UserDirectory users, SkillService skills) {
        this.users = users;
        this.skills = skills;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (alreadySeeded()) {
            log.info("Phase 5 demo planning data already present; skipping");
            return;
        }
        int skillsAssigned = seedUserSkills();
        int owners = seedTemplateProcessOwners();
        log.info("Seeded Phase 5 demo data: {} user skills, {} template process owners",
                skillsAssigned, owners);
    }

    private boolean alreadySeeded() {
        Number count = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM user_skills")
                .getSingleResult();
        return count.longValue() > 0;
    }

    /**
     * Proficiency 1-5, and days-since-last-use chosen to straddle the recency bonus
     * boundaries at 90 and 365 days so the skill score's recency term is actually
     * exercised by the demo rather than being uniformly maximal.
     */
    private int seedUserSkills() {
        Map<String, List<SkillGrant>> matrix = skillMatrix();
        Map<String, UUID> skillIdByCode = new LinkedHashMap<>();
        skills.listAll(ORGANIZATION_ID)
                .forEach(skill -> skillIdByCode.put(skill.code(), skill.id()));

        int assigned = 0;
        for (Map.Entry<String, List<SkillGrant>> entry : matrix.entrySet()) {
            Optional<UUID> userId = userIdOf(entry.getKey());
            if (userId.isEmpty()) {
                log.warn("Demo user {} not found; skipping their skills", entry.getKey());
                continue;
            }
            for (SkillGrant grant : entry.getValue()) {
                UUID skillId = skillIdByCode.get(grant.code());
                if (skillId == null) {
                    log.warn("Skill {} not in the vocabulary; skipping", grant.code());
                    continue;
                }
                entityManager.createNativeQuery("""
                        INSERT INTO user_skills (id, user_id, skill_id, proficiency,
                                                 years_experience, last_used_at)
                        VALUES (gen_random_uuid(), :userId, :skillId, :proficiency,
                                :years, CURRENT_DATE - CAST(:daysAgo AS integer))
                        ON CONFLICT (user_id, skill_id) DO NOTHING
                        """)
                        .setParameter("userId", userId.get())
                        .setParameter("skillId", skillId)
                        .setParameter("proficiency", grant.proficiency())
                        .setParameter("years", grant.years())
                        .setParameter("daysAgo", grant.daysSinceLastUse())
                        .executeUpdate();
                assigned++;
            }
        }
        return assigned;
    }

    private int seedTemplateProcessOwners() {
        int updated = 0;
        updated += setDefaultOwner(ONBOARDING_TEMPLATE_ID, HR_OPERATIONS_LEAD);
        updated += setDefaultOwner(UUID.fromString("00000000-0000-7000-8000-00000000f002"), HR_OPERATIONS_LEAD);
        updated += setDefaultOwner(UUID.fromString("00000000-0000-7000-8000-00000000f003"), IT_SERVICE_LEAD);
        updated += setDefaultOwner(UUID.fromString("00000000-0000-7000-8000-00000000f004"), IT_SERVICE_LEAD);
        return updated;
    }

    private int setDefaultOwner(UUID templateId, String email) {
        Optional<UUID> userId = userIdOf(email);
        if (userId.isEmpty()) {
            return 0;
        }
        return entityManager.createNativeQuery("""
                UPDATE process_templates SET default_process_owner_id = :userId
                WHERE id = :templateId AND default_process_owner_id IS NULL
                """)
                .setParameter("userId", userId.get())
                .setParameter("templateId", templateId)
                .executeUpdate();
    }

    private Optional<UUID> userIdOf(String email) {
        return users.findForAuthentication(email).map(UserDirectory.AuthenticationView::userId);
    }

    private record SkillGrant(String code, int proficiency, double years, int daysSinceLastUse) {
    }

    /**
     * The engineered skill matrix. Two properties are deliberate and load-bearing:
     *
     * <p>Only Cem Polat holds {@code OBJECT_STORAGE} at working proficiency, and he is the
     * person the workload data puts under pressure — that combination is what makes Demo 2
     * step 7 route the storage task to a Team Lead rather than auto-assigning it.
     *
     * <p>Ayşe Demir and Deniz Aslan have near-identical backend profiles, so they produce
     * the close scores that exercise the minimum-score-gap rule in Phase 6.
     */
    private Map<String, List<SkillGrant>> skillMatrix() {
        Map<String, List<SkillGrant>> matrix = new LinkedHashMap<>();

        matrix.put("ayse.demir@acme.io", List.of(
                new SkillGrant("JAVA", 5, 8.0, 5),
                new SkillGrant("SPRING_BOOT", 5, 7.0, 5),
                new SkillGrant("POSTGRESQL", 4, 6.0, 20),
                new SkillGrant("REST_API", 5, 7.0, 10),
                new SkillGrant("SECURITY", 4, 4.0, 60),
                new SkillGrant("CODE_REVIEW", 5, 6.0, 2)));

        matrix.put("deniz.aslan@acme.io", List.of(
                new SkillGrant("JAVA", 5, 7.0, 8),
                new SkillGrant("SPRING_BOOT", 4, 6.0, 12),
                new SkillGrant("POSTGRESQL", 5, 6.0, 15),
                new SkillGrant("REST_API", 4, 5.0, 25),
                new SkillGrant("PAYMENTS", 4, 3.0, 30),
                new SkillGrant("UNIT_TESTING", 4, 5.0, 7)));

        matrix.put("mert.kaya@acme.io", List.of(
                new SkillGrant("JAVA", 3, 3.0, 14),
                new SkillGrant("SPRING_BOOT", 3, 2.5, 14),
                new SkillGrant("SQL", 3, 3.0, 45),
                new SkillGrant("INTEGRATION_TESTING", 3, 2.0, 30)));

        matrix.put("ece.yildiz@acme.io", List.of(
                new SkillGrant("JAVA", 2, 1.0, 10),
                new SkillGrant("UNIT_TESTING", 3, 1.5, 5),
                // Last used over a year ago: recency bonus 0, which is the point.
                new SkillGrant("SQL", 2, 1.0, 400)));

        matrix.put("kerem.sahin@acme.io", List.of(
                new SkillGrant("TYPESCRIPT", 5, 7.0, 3),
                new SkillGrant("REACT", 5, 6.0, 3),
                new SkillGrant("ACCESSIBILITY", 4, 3.0, 40),
                new SkillGrant("CODE_REVIEW", 4, 5.0, 4)));

        matrix.put("baris.ozdemir@acme.io", List.of(
                new SkillGrant("TYPESCRIPT", 4, 4.0, 6),
                new SkillGrant("REACT", 4, 4.0, 6),
                new SkillGrant("ACCESSIBILITY", 3, 2.0, 120)));

        matrix.put("nil.arslan@acme.io", List.of(
                new SkillGrant("TYPESCRIPT", 2, 1.0, 9),
                new SkillGrant("REACT", 3, 1.5, 9)));

        // The only OBJECT_STORAGE holder. Demo 2 step 7 depends on this being true.
        matrix.put("cem.polat@acme.io", List.of(
                new SkillGrant("OBJECT_STORAGE", 5, 6.0, 12),
                new SkillGrant("DOCKER", 5, 6.0, 4),
                new SkillGrant("CI_CD", 4, 5.0, 8),
                new SkillGrant("POSTGRESQL", 4, 5.0, 30),
                new SkillGrant("SECURITY", 3, 3.0, 90)));

        matrix.put("burak.aydin@acme.io", List.of(
                new SkillGrant("DOCKER", 4, 5.0, 15),
                new SkillGrant("CI_CD", 5, 6.0, 5),
                new SkillGrant("INCIDENT_RESPONSE", 4, 4.0, 21),
                new SkillGrant("SECURITY", 4, 4.0, 45)));

        matrix.put("elif.yilmaz@acme.io", List.of(
                new SkillGrant("JAVA", 4, 10.0, 200),
                new SkillGrant("CODE_REVIEW", 5, 10.0, 10)));

        matrix.put("zeynep.koc@acme.io", List.of(
                new SkillGrant("HR_ONBOARDING", 5, 9.0, 2),
                new SkillGrant("PAYROLL", 3, 4.0, 60)));

        matrix.put("tolga.eren@acme.io", List.of(
                new SkillGrant("HR_ONBOARDING", 4, 4.0, 6),
                new SkillGrant("PROCUREMENT", 3, 2.0, 80)));

        matrix.put("selin.gunes@acme.io", List.of(
                new SkillGrant("PAYROLL", 5, 8.0, 3),
                new SkillGrant("PROCUREMENT", 4, 6.0, 12)));

        matrix.put("sibel.kaya@acme.io", List.of(
                new SkillGrant("USER_PROVISIONING", 5, 7.0, 4),
                new SkillGrant("ACTIVE_DIRECTORY", 5, 7.0, 4),
                new SkillGrant("MICROSOFT_365", 4, 5.0, 10),
                new SkillGrant("HARDWARE_SUPPORT", 4, 6.0, 8)));

        matrix.put("murat.celik@acme.io", List.of(
                new SkillGrant("USER_PROVISIONING", 4, 5.0, 7),
                new SkillGrant("ACTIVE_DIRECTORY", 4, 5.0, 7),
                new SkillGrant("HARDWARE_SUPPORT", 5, 8.0, 3),
                new SkillGrant("INCIDENT_RESPONSE", 4, 5.0, 25)));

        matrix.put("okan.tas@acme.io", List.of(
                new SkillGrant("HARDWARE_SUPPORT", 3, 2.0, 11),
                new SkillGrant("MICROSOFT_365", 3, 2.0, 11),
                new SkillGrant("USER_PROVISIONING", 3, 2.0, 20)));

        matrix.put("irem.acar@acme.io", List.of(
                new SkillGrant("MICROSOFT_365", 4, 4.0, 5),
                new SkillGrant("USER_PROVISIONING", 3, 3.0, 18),
                new SkillGrant("HARDWARE_SUPPORT", 3, 3.0, 22)));

        // Interns: real skills, so their exclusion in the demo is visibly a level policy
        // decision and not merely "they had no matching skill".
        matrix.put("can.ozturk@acme.io", List.of(
                new SkillGrant("JAVA", 2, 0.5, 3),
                new SkillGrant("UNIT_TESTING", 2, 0.5, 3)));

        matrix.put("lale.yalcin@acme.io", List.of(
                new SkillGrant("HARDWARE_SUPPORT", 2, 0.5, 4),
                new SkillGrant("MICROSOFT_365", 2, 0.5, 4)));

        return matrix;
    }
}
