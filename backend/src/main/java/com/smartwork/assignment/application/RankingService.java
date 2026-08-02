package com.smartwork.assignment.application;

import com.smartwork.assignment.domain.CandidateFeatures;
import com.smartwork.assignment.domain.CandidateFeatures.WorkItemContext;
import com.smartwork.assignment.domain.CandidateScore;
import com.smartwork.assignment.domain.EligibilityFilter;
import com.smartwork.assignment.domain.ExclusionReason;
import com.smartwork.assignment.domain.LevelRestrictions;
import com.smartwork.assignment.domain.RecommendationCandidate;
import com.smartwork.assignment.domain.ScoringEngine;
import com.smartwork.assignment.domain.ScoringWeights;
import com.smartwork.assignment.infrastructure.CandidateQueryRepository.PoolMember;
import com.smartwork.assignment.infrastructure.RecommendationCandidateRepository;
import com.smartwork.identity.domain.EmployeeLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Phase 5 half of the pipeline in docs/08 §1: discover, filter, score, rank.
 *
 * <p>It stops deliberately at step 4. Step 5 — the routing decision — is Phase 6, and
 * keeping the boundary sharp matters: this service answers "who fits best" and has no
 * opinion on whether a human needs to approve the answer. That separation is what lets a
 * routing decision be replayed against a different policy version without re-ranking.
 */
@Service
public class RankingService {

    private final CandidateDiscovery discovery;
    private final CandidateFeatureAssembler assembler;
    private final PolicyResolver policyResolver;
    private final RecommendationCandidateRepository candidateRepository;

    public RankingService(
            CandidateDiscovery discovery,
            CandidateFeatureAssembler assembler,
            PolicyResolver policyResolver,
            RecommendationCandidateRepository candidateRepository) {
        this.discovery = discovery;
        this.assembler = assembler;
        this.policyResolver = policyResolver;
        this.candidateRepository = candidateRepository;
    }

    /**
     * The outcome of a ranking run.
     *
     * <p>{@code noEligibleCandidate} is surfaced explicitly rather than left as an empty
     * list: docs/08 §3 requires the item to route to {@code TEAM_LEAD_REVIEW} in that case,
     * never to sit silently unassigned, and Phase 6 needs to be able to tell "nobody
     * qualified" apart from "we did not look".
     */
    public record Ranking(
            List<CandidateScore> ranked,
            Map<UUID, ExclusionReason> excluded,
            CandidateDiscovery.Tier tier,
            boolean noEligibleCandidate,
            ScoringWeights weights) {

        public java.util.Optional<CandidateScore> top() {
            return ranked.stream().findFirst();
        }

        /** Gap between first and second — an input to the Phase 6 auto-assign gate. */
        public int scoreGap() {
            if (ranked.size() < 2) {
                return Integer.MAX_VALUE;
            }
            return ranked.get(0).totalScore() - ranked.get(1).totalScore();
        }
    }

    @Transactional(readOnly = true)
    public Ranking rank(
            UUID organizationId,
            WorkItemContext item,
            CandidateFeatureAssembler.ItemFacts facts,
            boolean hasSeniorReviewer) {

        CandidateDiscovery.Pool pool = discovery.discover(
                organizationId, facts.recommendedTeamId(), facts.recommendedDepartmentId(),
                facts.mandatorySkillCodes());

        ScoringWeights weights = policyResolver.resolveWeights(
                organizationId, facts.recommendedDepartmentId(), facts.recommendedTeamId());

        if (pool.isEmpty()) {
            return new Ranking(List.of(), Map.of(), pool.tier(), true, weights);
        }

        List<CandidateFeatures> features = assembler.assemble(pool.members(), facts);

        Map<EmployeeLevel, LevelRestrictions> restrictions =
                policyResolver.resolveLevelRestrictions(organizationId);
        EligibilityFilter filter = new EligibilityFilter(restrictions::get);
        EligibilityFilter.Outcome outcome = filter.apply(features, item, hasSeniorReviewer);

        Map<UUID, ExclusionReason> excluded = new java.util.LinkedHashMap<>();
        outcome.excluded().forEach((candidate, reason) -> excluded.put(candidate.userId(), reason));

        if (outcome.isEmpty()) {
            return new Ranking(List.of(), excluded, pool.tier(), true, weights);
        }

        List<CandidateScore> ranked = new ScoringEngine(weights).rank(outcome.eligible(), item);
        return new Ranking(ranked, excluded, pool.tier(), false, weights);
    }

    /**
     * Persists a ranking against a recommendation. The top three carry a rank; everyone
     * else is stored without one, so the reviewer can see the full pool that was considered
     * rather than only the shortlist.
     */
    @Transactional
    public void persist(UUID recommendationId, Ranking ranking) {
        candidateRepository.deleteByRecommendationId(recommendationId);

        List<RecommendationCandidate> rows = new ArrayList<>();
        for (CandidateScore score : ranking.ranked()) {
            rows.add(score.rank() <= ScoringEngine.MAX_RANKED_CANDIDATES
                    ? RecommendationCandidate.ranked(recommendationId, score)
                    : RecommendationCandidate.considered(recommendationId, score));
        }
        ranking.excluded().forEach((userId, reason) ->
                rows.add(RecommendationCandidate.excluded(recommendationId, userId, reason)));

        candidateRepository.saveAll(rows);
    }

    /** Convenience for callers that only need the members, not the tier. */
    public List<PoolMember> poolFor(
            UUID organizationId, UUID teamId, UUID departmentId, List<String> mandatorySkillCodes) {
        return discovery.discover(organizationId, teamId, departmentId, mandatorySkillCodes).members();
    }
}
