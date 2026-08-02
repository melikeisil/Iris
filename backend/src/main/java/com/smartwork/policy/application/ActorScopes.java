package com.smartwork.policy.application;

import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.policy.domain.ApproverScope;

import java.util.ArrayList;
import java.util.List;

/**
 * The scope strings a given caller may answer for.
 *
 * <p>Built from the principal alone — no database read — because the principal already
 * carries the caller's teams, department and roles. That matters for the queue query: the
 * scopes go straight into an indexed {@code IN} clause, so "my queue" costs one lookup
 * rather than a scan that parses every scope string in the table.
 */
public final class ActorScopes {

    private ActorScopes() {
    }

    public static List<String> of(AuthPrincipal principal) {
        List<String> scopes = new ArrayList<>();

        // A team lead answers for the teams they lead. teamIds on the principal is
        // membership, which is broader than leadership - the narrowing happens in
        // TeamDirectory.teamIdsLedBy for the write path. For the queue, showing a member
        // a request addressed to their team is acceptable: they can see it, and the
        // decision endpoint re-checks authority before accepting an answer.
        principal.teamIds().forEach(teamId -> scopes.add(new ApproverScope.Team(teamId).serialize()));

        if (principal.departmentId() != null) {
            scopes.add(new ApproverScope.Department(principal.departmentId()).serialize());
        }
        if (principal.hasRole("MANAGER")) {
            scopes.add(new ApproverScope.ManagerRole().serialize());
        }
        if (principal.hasRole("ADMIN")) {
            scopes.add(new ApproverScope.AdminRole().serialize());
            scopes.add(new ApproverScope.ManagerRole().serialize());
        }

        // Always include the caller's own person scope so REQUEST_MORE_INFORMATION and
        // process-owner requests reach them. This is the one scope that does not depend
        // on a role: being asked a question is not a privilege.
        scopes.add(new ApproverScope.Person(principal.userId()).serialize());

        return scopes;
    }

    /**
     * Whether this caller may decide a request addressed to {@code scope}.
     *
     * <p>Stricter than queue visibility on purpose. Seeing a request in a shared team
     * queue is useful to everyone on the team; answering it is a lead's job.
     */
    public static boolean mayDecide(AuthPrincipal principal, ApproverScope scope, boolean leadsTargetTeam) {
        if (principal.isAdmin()) {
            return true;
        }
        return switch (scope) {
            case ApproverScope.Person person -> person.userId().equals(principal.userId());
            case ApproverScope.Team ignored -> leadsTargetTeam || principal.hasRole("MANAGER");
            case ApproverScope.Department department ->
                    principal.hasRole("MANAGER")
                            && (principal.organizationWide()
                                || department.departmentId().equals(principal.departmentId()));
            case ApproverScope.ManagerRole ignored -> principal.hasRole("MANAGER");
            case ApproverScope.AdminRole ignored -> false; // isAdmin already returned true
        };
    }
}
