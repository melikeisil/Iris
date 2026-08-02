package com.smartwork.workflow.domain.guards;

import com.smartwork.common.security.ResourceRole;
import com.smartwork.workflow.domain.GuardContext;
import com.smartwork.workflow.domain.GuardResult;
import com.smartwork.workflow.domain.TransitionGuard;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * The ten guards referenced by the seeded workflow profiles.
 *
 * <p>Each is a small, pure predicate over {@link GuardContext}. They are grouped in one
 * file because they are read together: the set of guards <em>is</em> the enforcement
 * story, and splitting ten five-line classes across ten files makes it harder to see that
 * the story is complete.
 */
public final class StandardGuards {

    private StandardGuards() {
    }

    public static List<TransitionGuard> all() {
        return List.of(
                new RequireReason(),
                new RequireAssignee(),
                new NotSelfReview(),
                new NoOpenDependencies(),
                new RequireAcceptanceCriteria(),
                new AllChildrenTerminal(),
                new RequireVerificationActor(),
                new RequirePostmortem(),
                new RequireSprint(),
                new LevelRestriction());
    }

    // ------------------------------------------------------------------ //

    /** BLOCK, REJECT_REVIEW, CANCEL and ESCALATE are useless without a reason. */
    public static final class RequireReason implements TransitionGuard {
        static final int MIN_LENGTH = 10;

        @Override
        public String code() {
            return "REQUIRE_REASON";
        }

        @Override
        public GuardResult evaluate(GuardContext context) {
            String reason = context.reason();
            if (reason == null || reason.trim().length() < MIN_LENGTH) {
                return GuardResult.fail(code(),
                        "A reason of at least %d characters is required for this transition."
                                .formatted(MIN_LENGTH));
            }
            return GuardResult.pass(code());
        }
    }

    /** Work cannot enter a state where hours count against capacity with nobody on it. */
    public static final class RequireAssignee implements TransitionGuard {
        @Override
        public String code() {
            return "REQUIRE_ASSIGNEE";
        }

        @Override
        public GuardResult evaluate(GuardContext context) {
            return context.assigneeId() == null
                    ? GuardResult.fail(code(), "This work item must have an assignee first.")
                    : GuardResult.pass(code());
        }
    }

    /**
     * The assignee may not approve their own review or pass their own QA.
     *
     * <p>In code rather than in configuration, so it cannot be misconfigured away by an
     * admin editing a workflow profile.
     */
    public static final class NotSelfReview implements TransitionGuard {
        @Override
        public String code() {
            return "NOT_SELF_REVIEW";
        }

        @Override
        public GuardResult evaluate(GuardContext context) {
            if (context.actorIsAssignee()) {
                return GuardResult.fail(code(),
                        "You cannot review or sign off your own work. Someone else must approve it.");
            }
            return GuardResult.pass(code());
        }
    }

    /** START_WORK is refused while a DEPENDS_ON target is unfinished. */
    public static final class NoOpenDependencies implements TransitionGuard {
        @Override
        public String code() {
            return "NO_OPEN_DEPENDENCIES";
        }

        @Override
        public GuardResult evaluate(GuardContext context) {
            Set<String> open = context.openDependencyKeys();
            if (open != null && !open.isEmpty()) {
                return GuardResult.fail(code(),
                        "Blocked by unfinished dependencies: " + String.join(", ", open) + ".");
            }
            return GuardResult.pass(code());
        }
    }

    /** A story or feature is not READY until someone has said what "done" means. */
    public static final class RequireAcceptanceCriteria implements TransitionGuard {
        private static final Set<String> APPLIES_TO = Set.of("STORY", "FEATURE");

        @Override
        public String code() {
            return "REQUIRE_ACCEPTANCE_CRITERIA";
        }

        @Override
        public GuardResult evaluate(GuardContext context) {
            if (!APPLIES_TO.contains(context.workItemType())) {
                return GuardResult.pass(code());
            }
            String criteria = context.acceptanceCriteria();
            if (criteria == null || criteria.isBlank()) {
                return GuardResult.fail(code(),
                        "Acceptance criteria are required before a %s can be marked ready."
                                .formatted(context.workItemType().toLowerCase()));
            }
            return GuardResult.pass(code());
        }
    }

    /** A parent cannot close over unfinished children. */
    public static final class AllChildrenTerminal implements TransitionGuard {
        @Override
        public String code() {
            return "ALL_CHILDREN_TERMINAL";
        }

        @Override
        public GuardResult evaluate(GuardContext context) {
            if (context.hasChildren() && context.hasUnfinishedChildren()) {
                return GuardResult.fail(code(),
                        "This item still has unfinished children and cannot be completed yet.");
            }
            return GuardResult.pass(code());
        }
    }

    /**
     * Whoever the closure policy names must be the one to verify.
     *
     * <p>A converted ticket is forced to REQUESTER_VERIFICATION: the person who reported
     * the problem confirms it is fixed, regardless of who fixed it.
     */
    public static final class RequireVerificationActor implements TransitionGuard {
        @Override
        public String code() {
            return "REQUIRE_VERIFICATION_ACTOR";
        }

        @Override
        public GuardResult evaluate(GuardContext context) {
            String closureType = context.closureType();
            if (closureType == null || "AUTO_CLOSE".equals(closureType)) {
                return GuardResult.pass(code());
            }
            boolean permitted = switch (closureType) {
                case "REQUESTER_VERIFICATION" -> context.actorIs(ResourceRole.REQUESTER)
                        || context.actorHasRole("MANAGER");
                case "PROCESS_OWNER_VERIFICATION" -> context.actorIs(ResourceRole.PROCESS_OWNER)
                        || context.actorHasRole("MANAGER");
                case "TEAM_LEAD_VERIFICATION" -> context.actorHasRole("TEAM_LEAD")
                        || context.actorHasRole("MANAGER");
                default -> true;
            };
            if (!permitted) {
                return GuardResult.fail(code(),
                        "This item's closure policy (%s) requires a different person to verify it."
                                .formatted(closureType));
            }
            return GuardResult.pass(code());
        }
    }

    /** A critical or production-impacting incident does not get closed without a postmortem. */
    public static final class RequirePostmortem implements TransitionGuard {
        @Override
        public String code() {
            return "REQUIRE_POSTMORTEM";
        }

        @Override
        public GuardResult evaluate(GuardContext context) {
            if ("CRITICAL".equals(context.priority()) || context.productionImpacting()) {
                return GuardResult.fail(code(),
                        "Critical and production-impacting incidents must go through a postmortem "
                                + "before they can be closed.");
            }
            return GuardResult.pass(code());
        }
    }

    /** ADD_TO_SPRINT needs a sprint, and it needs to be the one currently running. */
    public static final class RequireSprint implements TransitionGuard {
        @Override
        public String code() {
            return "REQUIRE_SPRINT";
        }

        @Override
        public GuardResult evaluate(GuardContext context) {
            if (context.sprintId() == null) {
                return GuardResult.fail(code(), "Select a sprint before moving this item into it.");
            }
            if (!context.sprintActive()) {
                return GuardResult.fail(code(), "That sprint is not active.");
            }
            return GuardResult.pass(code());
        }
    }

    /**
     * Employee-level restrictions, enforced at transition time as well as at candidate
     * ranking - so a Team Lead assigning manually is stopped too, not just the assignment
     * engine.
     *
     * <p>Phase 3 applies the documented defaults in code. Phase 6 moves the thresholds
     * into {@code employee_level_policies} so an Admin can configure them; the guard code
     * and its contract stay the same. It is deterministic backend logic either way -
     * never a prompt instruction, because a model told "do not assign interns to
     * production work" complies most of the time, and most of the time is not an
     * authorization model.
     */
    public static final class LevelRestriction implements TransitionGuard {
        static final BigDecimal INTERN_MAX_HOURS = new BigDecimal("8");
        static final BigDecimal JUNIOR_MAX_HOURS = new BigDecimal("16");

        @Override
        public String code() {
            return "LEVEL_RESTRICTION";
        }

        @Override
        public GuardResult evaluate(GuardContext context) {
            String level = context.assigneeLevel();
            if (level == null || context.assigneeId() == null) {
                return GuardResult.pass(code());
            }

            if ("INTERN".equals(level)) {
                if (context.productionImpacting()) {
                    return deny("production-impacting work");
                }
                if (context.budgetSensitive()) {
                    return deny("budget-sensitive work");
                }
                if (context.securitySensitive()) {
                    return deny("security-sensitive work");
                }
                if ("CRITICAL".equals(context.priority())) {
                    return deny("CRITICAL priority work");
                }
                if (exceeds(context.estimatedHours(), INTERN_MAX_HOURS)) {
                    return deny("work estimated above " + INTERN_MAX_HOURS + " hours");
                }
            }

            if ("JUNIOR".equals(level)) {
                if (context.productionImpacting()) {
                    return deny("production-impacting work");
                }
                if ("CRITICAL".equals(context.priority())) {
                    return deny("CRITICAL priority work");
                }
                if (exceeds(context.estimatedHours(), JUNIOR_MAX_HOURS)) {
                    return deny("work estimated above " + JUNIOR_MAX_HOURS + " hours");
                }
            }

            return GuardResult.pass(code());
        }

        private GuardResult deny(String what) {
            return GuardResult.fail(code(),
                    "The employee level policy does not permit this person to take " + what
                            + ". A manager may approve an exception.");
        }

        private static boolean exceeds(BigDecimal value, BigDecimal limit) {
            return value != null && value.compareTo(limit) > 0;
        }
    }
}
