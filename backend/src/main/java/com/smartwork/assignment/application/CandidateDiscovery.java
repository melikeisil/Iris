package com.smartwork.assignment.application;

import com.smartwork.assignment.infrastructure.CandidateQueryRepository;
import com.smartwork.assignment.infrastructure.CandidateQueryRepository.PoolMember;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Tiered candidate discovery (docs/08 §2). The first non-empty tier wins.
 *
 * <p>Tiering rather than one big union is deliberate: a person in the recommended team and
 * a person three departments away who happens to hold the skill are not comparable
 * candidates, and putting them in the same pool would let the scoring formula pretend they
 * are. If the right team has anyone at all, that is the pool.
 */
@Service
public class CandidateDiscovery {

    private final CandidateQueryRepository candidateQueries;

    public CandidateDiscovery(CandidateQueryRepository candidateQueries) {
        this.candidateQueries = candidateQueries;
    }

    public enum Tier {
        RECOMMENDED_TEAM,
        RECOMMENDED_DEPARTMENT,
        ORGANIZATION_BY_SKILL,
        EMPTY
    }

    public record Pool(Tier tier, List<PoolMember> members) {

        public boolean isEmpty() {
            return members.isEmpty();
        }

        /**
         * Tier 3 can never produce an AUTO_ASSIGN unless the policy explicitly allows
         * cross-department automation (docs/08 §2). Surfaced here so the Phase 6 policy
         * engine does not have to re-derive it.
         */
        public boolean isCrossDepartment() {
            return tier == Tier.ORGANIZATION_BY_SKILL;
        }
    }

    @Transactional(readOnly = true)
    public Pool discover(
            UUID organizationId, UUID recommendedTeamId, UUID recommendedDepartmentId,
            Collection<String> mandatorySkillCodes) {

        if (recommendedTeamId != null) {
            List<PoolMember> tier1 = candidateQueries.findTeamMembers(organizationId, recommendedTeamId);
            if (!tier1.isEmpty()) {
                return new Pool(Tier.RECOMMENDED_TEAM, tier1);
            }
        }
        if (recommendedDepartmentId != null) {
            List<PoolMember> tier2 = candidateQueries.findDepartmentMembers(
                    organizationId, recommendedDepartmentId, recommendedTeamId);
            if (!tier2.isEmpty()) {
                return new Pool(Tier.RECOMMENDED_DEPARTMENT, tier2);
            }
        }
        List<PoolMember> tier3 = candidateQueries.findBySkills(
                organizationId, mandatorySkillCodes, recommendedTeamId);
        if (!tier3.isEmpty()) {
            return new Pool(Tier.ORGANIZATION_BY_SKILL, tier3);
        }
        return new Pool(Tier.EMPTY, List.of());
    }
}
