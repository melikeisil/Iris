package com.smartwork.workitem.domain;

import java.util.Optional;
import java.util.Set;

/**
 * Typed relationships between work items.
 *
 * <p>Six inverse pairs are stored once, in the canonical direction, and rendered in both
 * directions by the read model. That is what makes {@code UNIQUE (source, type, target)}
 * enforceable - storing both directions would let the two halves drift apart.
 */
public enum LinkType {

    PARENT_OF,
    CHILD_OF,
    BLOCKS,
    DEPENDS_ON,
    DUPLICATES,
    RELATES_TO,
    RESOLVES,
    RESOLVED_BY,
    CAUSED_BY,
    IMPLEMENTS,
    GENERATED_FROM,
    TESTS;

    /** Types where a cycle would be a genuine contradiction rather than merely odd. */
    private static final Set<LinkType> ACYCLIC = Set.of(PARENT_OF, CHILD_OF, BLOCKS, DEPENDS_ON);

    public boolean requiresCycleCheck() {
        return ACYCLIC.contains(this);
    }

    /** The direction this type is stored in. */
    public boolean isCanonical() {
        return switch (this) {
            case CHILD_OF, BLOCKS, RESOLVES -> false;
            default -> true;
        };
    }

    /**
     * Rewrites a non-canonical request into the stored form.
     * {@code A CHILD_OF B} is persisted as {@code B PARENT_OF A}.
     */
    public LinkType canonicalType() {
        return switch (this) {
            case CHILD_OF -> PARENT_OF;
            case BLOCKS -> DEPENDS_ON;
            case RESOLVES -> RESOLVED_BY;
            default -> this;
        };
    }

    public Optional<LinkType> inverse() {
        return switch (this) {
            case PARENT_OF -> Optional.of(CHILD_OF);
            case CHILD_OF -> Optional.of(PARENT_OF);
            case BLOCKS -> Optional.of(DEPENDS_ON);
            case DEPENDS_ON -> Optional.of(BLOCKS);
            case RESOLVES -> Optional.of(RESOLVED_BY);
            case RESOLVED_BY -> Optional.of(RESOLVES);
            case DUPLICATES, RELATES_TO -> Optional.of(this);
            default -> Optional.empty();
        };
    }

    /** A DEPENDS_ON target must finish before the source can start. */
    public boolean isBlockingDependency() {
        return this == DEPENDS_ON;
    }
}
