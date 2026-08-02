package com.smartwork.policy.domain;

import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalRequestTest {

    private static final Instant NOW = Instant.parse("2026-08-01T09:00:00Z");
    private static final UUID ORG = UUID.randomUUID();
    private static final UUID ITEM = UUID.randomUUID();
    private static final UUID REC = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();

    private static ApprovalRequest open(ApprovalRoute route, ApproverScope scope) {
        RoutingDecision decision = new RoutingDecision(
                route, List.of(ReasonCode.RISK_MEDIUM),
                "test", Duration.ofHours(12), null, null, 1);
        return ApprovalRequest.open(ORG, ITEM, REC, decision, scope, NOW);
    }

    @Test
    void aNewRequestIsOpenAndDueAfterTheSlaWindow() {
        ApprovalRequest request = open(
                ApprovalRoute.TEAM_LEAD_REVIEW, new ApproverScope.Team(UUID.randomUUID()));

        assertThat(request.isOpen()).isTrue();
        assertThat(request.getDueAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
        assertThat(request.isOverdue(NOW)).isFalse();
        assertThat(request.isOverdue(NOW.plus(Duration.ofHours(13)))).isTrue();
    }

    @Test
    @DisplayName("a second decision is a conflict, not a silent overwrite")
    void aRequestIsDecidedOnlyOnce() {
        ApprovalRequest request = open(
                ApprovalRoute.TEAM_LEAD_REVIEW, new ApproverScope.Team(UUID.randomUUID()));

        request.decide(ApprovalRequest.Status.APPROVED, ACTOR, "looks right", NOW);

        // Two people approving the same request is worth surfacing. Last-write-wins would
        // hide the fact that two reviewers were working the same item.
        assertThatThrownBy(() -> request.decide(
                ApprovalRequest.Status.REJECTED, UUID.randomUUID(), "actually no", NOW))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ErrorCode.ALREADY_DECIDED));

        assertThat(request.getStatus()).isEqualTo(ApprovalRequest.Status.APPROVED);
        assertThat(request.getDecisionNote()).isEqualTo("looks right");
    }

    @Test
    void anEscalationRequiresAReason() {
        ApprovalRequest request = open(
                ApprovalRoute.TEAM_LEAD_REVIEW, new ApproverScope.Team(UUID.randomUUID()));

        assertThatThrownBy(() -> request.escalate(ACTOR, "  ", NOW))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("must state why");

        assertThat(request.isOpen()).as("a refused escalation must not close the request").isTrue();
    }

    @Test
    void escalatingClosesTheRequestAsEscalated() {
        ApprovalRequest request = open(
                ApprovalRoute.TEAM_LEAD_REVIEW, new ApproverScope.Team(UUID.randomUUID()));

        request.escalate(ACTOR, "Vendor licence needs budget authority above mine.", NOW);

        assertThat(request.getStatus()).isEqualTo(ApprovalRequest.Status.ESCALATED);
        assertThat(request.getDecidedBy()).isEqualTo(ACTOR);
        assertThat(request.getDecisionNote()).contains("budget authority");
    }

    @Test
    void anAlreadyDecidedRequestCannotBeEscalated() {
        ApprovalRequest request = open(
                ApprovalRoute.TEAM_LEAD_REVIEW, new ApproverScope.Team(UUID.randomUUID()));
        request.decide(ApprovalRequest.Status.REJECTED, ACTOR, "out of scope", NOW);

        assertThatThrownBy(() -> request.escalate(ACTOR, "changed my mind", NOW))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void expiringOnlyAffectsOpenRequests() {
        ApprovalRequest decided = open(
                ApprovalRoute.TEAM_LEAD_REVIEW, new ApproverScope.Team(UUID.randomUUID()));
        decided.decide(ApprovalRequest.Status.APPROVED, ACTOR, "fine", NOW);
        decided.expire(NOW.plus(Duration.ofDays(30)));

        assertThat(decided.getStatus())
                .as("an approved request must not become EXPIRED just because time passed")
                .isEqualTo(ApprovalRequest.Status.APPROVED);
    }

    @Test
    @DisplayName("a person-scoped request also populates the indexed user column")
    void personScopeIsIndexable() {
        UUID person = UUID.randomUUID();
        ApprovalRequest request = open(
                ApprovalRoute.REQUEST_MORE_INFORMATION, new ApproverScope.Person(person));

        assertThat(request.getAssignedToUserId()).isEqualTo(person);
        assertThat(request.getAssignedToScope()).isEqualTo("USER:" + person);
    }

    @Test
    void scopeStringsRoundTrip() {
        UUID id = UUID.randomUUID();
        assertThat(ApproverScope.parse("TEAM:" + id)).isEqualTo(new ApproverScope.Team(id));
        assertThat(ApproverScope.parse("DEPT:" + id)).isEqualTo(new ApproverScope.Department(id));
        assertThat(ApproverScope.parse("USER:" + id)).isEqualTo(new ApproverScope.Person(id));
        assertThat(ApproverScope.parse("ROLE:MANAGER")).isEqualTo(new ApproverScope.ManagerRole());
        assertThat(ApproverScope.parse("ROLE:ADMIN")).isEqualTo(new ApproverScope.AdminRole());
    }

    @Test
    @DisplayName("a malformed scope is rejected rather than creating an unreachable request")
    void malformedScopesAreRejected() {
        // An unparseable scope produces a request that appears in no queue, which is
        // indistinguishable from work that quietly stopped.
        assertThatThrownBy(() -> ApproverScope.parse("TEAM:not-a-uuid"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ApproverScope.parse("SOMETHING:else"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ApproverScope.parse("no-separator"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ApproverScope.parse(null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void policyExceptionIsDerivedFromTheReasonCodes() {
        RoutingDecision decision = new RoutingDecision(
                ApprovalRoute.MANAGER_APPROVAL,
                List.of(ReasonCode.ACTIVE_POLICY_EXCEPTION),
                "test", Duration.ofHours(8), null, null, 1);

        ApprovalRequest request = ApprovalRequest.open(
                ORG, ITEM, REC, decision, new ApproverScope.ManagerRole(), NOW);

        assertThat(request.isPolicyException()).isTrue();
    }
}
