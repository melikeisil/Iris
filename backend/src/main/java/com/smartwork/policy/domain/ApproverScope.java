package com.smartwork.policy.domain;

import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;

import java.util.Optional;
import java.util.UUID;

/**
 * Who may decide an approval request.
 *
 * <p>docs/08 §5.5: addressed to a <em>scope</em> rather than a single person wherever
 * possible, "so work does not stall behind one individual's absence". That is the entire
 * point of this type. A request addressed to `TEAM:uuid` is answerable by whoever is
 * leading that team today, including a stand-in during a holiday; a request addressed to a
 * user id becomes invisible the moment that person is away.
 *
 * <p>Serialised as a short string into {@code approval_requests.assigned_to_scope}, whose
 * format the V11 check constraint enforces — an unparseable scope would produce a request
 * no queue can see, which is indistinguishable from work that quietly stopped.
 */
public sealed interface ApproverScope {

    String serialize();

    record Team(UUID teamId) implements ApproverScope {
        @Override
        public String serialize() {
            return "TEAM:" + teamId;
        }
    }

    record Department(UUID departmentId) implements ApproverScope {
        @Override
        public String serialize() {
            return "DEPT:" + departmentId;
        }
    }

    record ManagerRole() implements ApproverScope {
        @Override
        public String serialize() {
            return "ROLE:MANAGER";
        }
    }

    record AdminRole() implements ApproverScope {
        @Override
        public String serialize() {
            return "ROLE:ADMIN";
        }
    }

    /**
     * A named individual. Used only where the design requires it — the requester on
     * REQUEST_MORE_INFORMATION, and the specific Process Owner of an item — because those
     * are cases where "anyone with the role" would be the wrong answer.
     */
    record Person(UUID userId) implements ApproverScope {
        @Override
        public String serialize() {
            return "USER:" + userId;
        }
    }

    static ApproverScope parse(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Approver scope is required");
        }
        if (value.equals("ROLE:MANAGER")) {
            return new ManagerRole();
        }
        if (value.equals("ROLE:ADMIN")) {
            return new AdminRole();
        }
        int separator = value.indexOf(':');
        if (separator < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Malformed approver scope: " + value);
        }
        String prefix = value.substring(0, separator);
        String rest = value.substring(separator + 1);
        try {
            return switch (prefix) {
                case "TEAM" -> new Team(UUID.fromString(rest));
                case "DEPT" -> new Department(UUID.fromString(rest));
                case "USER" -> new Person(UUID.fromString(rest));
                default -> throw new ApiException(
                        ErrorCode.VALIDATION_FAILED, "Unknown approver scope prefix: " + prefix);
            };
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Malformed approver scope: " + value);
        }
    }

    // Named find* rather than teamId()/departmentId()/userId(): a record component
    // already generates an accessor under the bare name, and an interface method of the
    // same name returning Optional<UUID> cannot be implemented by one returning UUID.
    // Callers that know the variant use the record accessor; these are for the cases
    // where the variant is not known statically.

    default Optional<UUID> findTeamId() {
        return this instanceof Team team ? Optional.of(team.teamId()) : Optional.empty();
    }

    default Optional<UUID> findDepartmentId() {
        return this instanceof Department department
                ? Optional.of(department.departmentId()) : Optional.empty();
    }

    default Optional<UUID> findUserId() {
        return this instanceof Person person ? Optional.of(person.userId()) : Optional.empty();
    }
}
