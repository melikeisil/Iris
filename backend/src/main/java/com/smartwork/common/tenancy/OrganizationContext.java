package com.smartwork.common.tenancy;

import java.util.Optional;
import java.util.UUID;

/**
 * The organization the current request belongs to, resolved from the JWT by
 * {@code JwtAuthenticationFilter} and cleared when the request completes.
 *
 * <p>Every repository query that touches a tenant entity takes this value as an explicit
 * predicate. That is deliberate: a Hibernate {@code @Filter} would be less code, but a
 * filter that fails to activate silently returns everything, whereas a missing parameter
 * fails to compile.
 */
public final class OrganizationContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private OrganizationContext() {
    }

    public static void set(UUID organizationId) {
        CURRENT.set(organizationId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Optional<UUID> find() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * @throws IllegalStateException when called outside an authenticated request. This is
     *         a programming error, not a client error: it means a service reachable only
     *         with a tenant context was invoked without one.
     */
    public static UUID require() {
        UUID current = CURRENT.get();
        if (current == null) {
            throw new IllegalStateException(
                    "No organization in context. A tenant-scoped operation was invoked "
                            + "outside an authenticated request.");
        }
        return current;
    }
}
