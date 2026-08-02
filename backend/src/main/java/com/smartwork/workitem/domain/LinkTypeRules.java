package com.smartwork.workitem.domain;

import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;

import java.util.Set;

/**
 * Which link types make sense between which work item types.
 *
 * <p>The rules are permissive by default and restrictive only where a link would be
 * meaningless. Cross-family links in particular must stay legal: {@code REQUEST
 * RESOLVED_BY BUG} is the ticket-to-development conversion, and a rule that blocked it
 * would break the central scenario.
 */
public final class LinkTypeRules {

    private static final Set<WorkFamily> RESOLVABLE =
            Set.of(WorkFamily.SERVICE_WORK, WorkFamily.INCIDENT_WORK, WorkFamily.PROCESS_WORK);

    private LinkTypeRules() {
    }

    /**
     * @param type   the requested type, before canonicalization
     * @throws ApiException INVALID_LINK_TYPE when the pairing is not meaningful
     */
    public static void validate(LinkType type, WorkItemType sourceType, WorkFamily sourceFamily,
                                WorkItemType targetType, WorkFamily targetFamily) {

        switch (type) {
            case PARENT_OF -> {
                if (!sourceType.canHaveChildren()) {
                    reject("A SUBTASK is a leaf and cannot be a parent.");
                }
                if (targetType.mustBeRoot()) {
                    reject("A %s is always a root item and cannot be given a parent."
                            .formatted(targetType));
                }
            }
            case CHILD_OF -> {
                if (!targetType.canHaveChildren()) {
                    reject("A SUBTASK is a leaf and cannot be a parent.");
                }
                if (sourceType.mustBeRoot()) {
                    reject("A %s is always a root item and cannot be given a parent."
                            .formatted(sourceType));
                }
            }
            case TESTS -> {
                if (sourceType != WorkItemType.TEST) {
                    reject("Only a TEST item can declare that it tests another item.");
                }
            }
            case RESOLVES -> {
                if (!RESOLVABLE.contains(targetFamily)) {
                    reject("Only a ticket, incident or process item can be resolved by other work.");
                }
            }
            case RESOLVED_BY -> {
                if (!RESOLVABLE.contains(sourceFamily)) {
                    reject("Only a ticket, incident or process item can be resolved by other work.");
                }
            }
            case IMPLEMENTS -> {
                if (!targetType.isDevelopment()) {
                    reject("An IMPLEMENTS link must point at a development work item.");
                }
            }
            default -> {
                // BLOCKS, DEPENDS_ON, DUPLICATES, RELATES_TO, CAUSED_BY and GENERATED_FROM
                // are meaningful between any pair; over-constraining them would block
                // legitimate cross-family modelling.
            }
        }
    }

    private static void reject(String message) {
        throw new ApiException(ErrorCode.INVALID_LINK_TYPE, message);
    }
}
