package com.smartwork.workitem.infrastructure;

import com.smartwork.workitem.domain.WorkItemType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Generates human reference keys: TCK-102, INC-7, IDP-231.
 *
 * <p>Local and independent of Jira (ADR-020), so the platform is fully usable before,
 * during and after a Jira outage, and imported issues cannot collide with locally created
 * work.
 */
@Component
public class ReferenceKeyGenerator {

    public static final String TICKET_PREFIX = "TCK";
    public static final String INCIDENT_PREFIX = "INC";
    public static final String GENERIC_PREFIX = "WI";

    /**
     * Atomic claim-and-increment. An upsert rather than {@code SELECT ... FOR UPDATE}
     * because it also creates the counter on first use, in one round trip and with no
     * window for two concurrent creators to both see "no counter yet".
     */
    private static final String CLAIM_NEXT = """
            INSERT INTO reference_key_counters (organization_id, prefix, next_value)
            VALUES (CAST(:organizationId AS uuid), :prefix, 2)
            ON CONFLICT (organization_id, prefix)
            DO UPDATE SET next_value = reference_key_counters.next_value + 1
            RETURNING next_value - 1
            """;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Runs in its own transaction so a claimed number is never returned to the pool by a
     * later rollback. A gap in the sequence is harmless; two items sharing TCK-102 is not.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generate(UUID organizationId, WorkItemType type, String projectKeyPrefix) {
        String prefix = prefixFor(type, projectKeyPrefix);

        Number value = (Number) entityManager.createNativeQuery(CLAIM_NEXT)
                .setParameter("organizationId", organizationId.toString())
                .setParameter("prefix", prefix)
                .getSingleResult();

        return prefix + "-" + value.longValue();
    }

    /**
     * Tickets and incidents get their own sequences because they are the two things users
     * quote out loud; everything else takes its project's prefix.
     */
    public static String prefixFor(WorkItemType type, String projectKeyPrefix) {
        if (type == WorkItemType.REQUEST) {
            return TICKET_PREFIX;
        }
        if (type == WorkItemType.INCIDENT) {
            return INCIDENT_PREFIX;
        }
        if (projectKeyPrefix != null && !projectKeyPrefix.isBlank()) {
            return projectKeyPrefix.trim().toUpperCase();
        }
        return GENERIC_PREFIX;
    }
}
