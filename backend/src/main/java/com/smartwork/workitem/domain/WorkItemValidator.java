package com.smartwork.workitem.domain;

import com.smartwork.common.error.ApiException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Type-aware validation.
 *
 * <p>One table holds twelve types, so most columns are nullable and the real rules are
 * conditional: a story needs a project, a subtask needs a parent, a ticket must not carry
 * story points. A CHECK constraint cannot express "required for these types only", and
 * splitting the table to make the constraints expressible would cost far more than this
 * class does.
 *
 * <p>Errors are collected rather than thrown one at a time, so a client fixing a form
 * sees every problem at once.
 */
public final class WorkItemValidator {

    private static final Set<Integer> FIBONACCI = Set.of(1, 2, 3, 5, 8, 13, 21);
    private static final BigDecimal MAX_HOURS = new BigDecimal("200");
    private static final int SUMMARY_MIN = 5;
    private static final int SUMMARY_MAX = 255;

    private WorkItemValidator() {
    }

    /**
     * @param parentType type of the parent, or null when there is none
     * @throws ApiException VALIDATION_FAILED listing every offending field
     */
    public static void validateForCreation(Command command, WorkItemType parentType, LocalDate today) {
        List<ApiException.FieldError> errors = new ArrayList<>();

        validateSummary(command.summary(), errors);
        validateHierarchy(command, parentType, errors);
        validateStoryPoints(command, errors);
        validateProject(command, errors);
        validateEffort(command, errors);
        validateDueDate(command.dueDate(), today, errors);
        validateIncidentPriority(command, errors);

        if (!errors.isEmpty()) {
            throw ApiException.validation(
                    "This work item does not satisfy the rules for a " + command.type() + ".", errors);
        }
    }

    /** Update rules differ: hierarchy and type are immutable once the item exists. */
    public static void validateForUpdate(Command command, LocalDate today) {
        List<ApiException.FieldError> errors = new ArrayList<>();

        validateSummary(command.summary(), errors);
        validateStoryPoints(command, errors);
        validateEffort(command, errors);
        // Due dates in the past are allowed on update: an item can legitimately become
        // overdue, and refusing the edit would prevent recording that it did.

        if (!errors.isEmpty()) {
            throw ApiException.validation("This update is not valid.", errors);
        }
    }

    private static void validateSummary(String summary, List<ApiException.FieldError> errors) {
        if (summary == null || summary.isBlank()) {
            errors.add(field("summary", "REQUIRED", "A summary is required."));
            return;
        }
        String trimmed = summary.trim();
        if (trimmed.length() < SUMMARY_MIN || trimmed.length() > SUMMARY_MAX) {
            errors.add(field("summary", "LENGTH",
                    "Summary must be between %d and %d characters.".formatted(SUMMARY_MIN, SUMMARY_MAX)));
        }
    }

    private static void validateHierarchy(Command command, WorkItemType parentType,
                                          List<ApiException.FieldError> errors) {
        WorkItemType type = command.type();

        if (type.mustBeRoot() && command.parentId() != null) {
            errors.add(field("parentId", "MUST_BE_ROOT",
                    "A %s is always a root item and cannot have a parent.".formatted(type)));
        }
        if (type.mustHaveParent() && command.parentId() == null) {
            errors.add(field("parentId", "REQUIRED", "A SUBTASK must have a parent."));
        }
        if (command.parentId() != null && parentType != null && !parentType.canHaveChildren()) {
            errors.add(field("parentId", "INVALID_PARENT",
                    "A SUBTASK is a leaf and cannot be a parent."));
        }
    }

    private static void validateStoryPoints(Command command, List<ApiException.FieldError> errors) {
        Short points = command.storyPoints();
        if (points == null) {
            return;
        }
        if (!command.type().supportsStoryPoints()) {
            errors.add(field("storyPoints", "NOT_APPLICABLE",
                    "Story points do not apply to a %s. Use estimated hours.".formatted(command.type())));
        }
        if (!FIBONACCI.contains((int) points)) {
            errors.add(field("storyPoints", "NOT_FIBONACCI",
                    "Story points must be one of 1, 2, 3, 5, 8, 13, 21."));
        }
    }

    private static void validateProject(Command command, List<ApiException.FieldError> errors) {
        if (command.type().requiresProject() && command.projectId() == null) {
            errors.add(field("projectId", "REQUIRED",
                    "A %s must belong to a project.".formatted(command.type())));
        }
    }

    private static void validateEffort(Command command, List<ApiException.FieldError> errors) {
        BigDecimal hours = command.estimatedHours();
        if (hours == null) {
            return;
        }
        if (hours.compareTo(BigDecimal.ZERO) <= 0 || hours.compareTo(MAX_HOURS) > 0) {
            errors.add(field("estimatedHours", "RANGE",
                    "Estimated hours must be greater than 0 and at most %s. An item larger than "
                            + "that should be decomposed.".formatted(MAX_HOURS)));
        }
    }

    private static void validateDueDate(LocalDate dueDate, LocalDate today,
                                        List<ApiException.FieldError> errors) {
        if (dueDate != null && dueDate.isBefore(today)) {
            errors.add(field("dueDate", "IN_PAST", "The due date cannot be in the past."));
        }
    }

    private static void validateIncidentPriority(Command command, List<ApiException.FieldError> errors) {
        if (command.type() == WorkItemType.INCIDENT && command.priority() == Priority.LOW) {
            errors.add(field("priority", "TOO_LOW",
                    "An incident is at least HIGH priority. If it is genuinely low impact, "
                            + "raise it as a REQUEST instead."));
        }
    }

    private static ApiException.FieldError field(String name, String code, String message) {
        return new ApiException.FieldError(name, code, message);
    }

    /** The subset of a work item the rules actually read. */
    public record Command(WorkItemType type,
                          String summary,
                          java.util.UUID parentId,
                          java.util.UUID projectId,
                          Short storyPoints,
                          BigDecimal estimatedHours,
                          LocalDate dueDate,
                          Priority priority) {
    }
}
