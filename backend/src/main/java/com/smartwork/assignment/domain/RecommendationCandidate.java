package com.smartwork.assignment.domain;

import com.smartwork.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One considered candidate — ranked or excluded.
 *
 * <p>{@code recommendationId} is a plain UUID rather than a JPA association: the
 * recommendation aggregate lives in the {@code analysis} module and an entity graph across
 * that boundary is exactly what ADR-028 forbids.
 */
@Entity
@Table(name = "recommendation_candidates")
public class RecommendationCandidate extends BaseEntity {

    @Column(name = "recommendation_id", nullable = false, updatable = false)
    private UUID recommendationId;

    @Column(name = "candidate_user_id", nullable = false, updatable = false)
    private UUID candidateUserId;

    @Column(name = "rank")
    private Short rank;

    @Column(name = "total_score")
    private Short totalScore;

    @Column(name = "skill_score")
    private Short skillScore;

    @Column(name = "team_score")
    private Short teamScore;

    @Column(name = "capacity_score")
    private Short capacityScore;

    @Column(name = "experience_score")
    private Short experienceScore;

    @Column(name = "component_score")
    private Short componentScore;

    @Column(name = "deadline_score")
    private Short deadlineScore;

    @Column(name = "fairness_score")
    private Short fairnessScore;

    @Column(name = "workload_ratio", precision = 4, scale = 2)
    private BigDecimal workloadRatio;

    @Column(name = "excluded", nullable = false)
    private boolean excluded;

    @Enumerated(EnumType.STRING)
    @Column(name = "exclusion_reason", length = 64)
    private ExclusionReason exclusionReason;

    @Column(name = "explanation", columnDefinition = "text")
    private String explanation;

    protected RecommendationCandidate() {
    }

    public static RecommendationCandidate ranked(UUID recommendationId, CandidateScore score) {
        RecommendationCandidate entity = new RecommendationCandidate();
        entity.recommendationId = recommendationId;
        entity.candidateUserId = score.candidateUserId();
        entity.rank = (short) score.rank();
        entity.totalScore = (short) score.totalScore();
        entity.skillScore = (short) score.components().skill();
        entity.capacityScore = (short) score.components().capacity();
        entity.teamScore = (short) score.components().team();
        entity.experienceScore = (short) score.components().experience();
        entity.componentScore = (short) score.components().component();
        entity.deadlineScore = (short) score.components().deadline();
        entity.fairnessScore = (short) score.components().fairness();
        entity.workloadRatio = score.workloadRatio();
        entity.excluded = false;
        entity.explanation = score.explanation();
        return entity;
    }

    /**
     * A scored-but-unranked candidate: considered, below the top three. Stored so the
     * reviewer can see the whole pool, with {@code rank} left null because the DB check
     * constraint reserves ranks 1-3.
     */
    public static RecommendationCandidate considered(UUID recommendationId, CandidateScore score) {
        RecommendationCandidate entity = ranked(recommendationId, score);
        entity.rank = null;
        return entity;
    }

    public static RecommendationCandidate excluded(
            UUID recommendationId, UUID candidateUserId, ExclusionReason reason) {
        RecommendationCandidate entity = new RecommendationCandidate();
        entity.recommendationId = recommendationId;
        entity.candidateUserId = candidateUserId;
        entity.excluded = true;
        entity.exclusionReason = reason;
        entity.explanation = Explanations.forExclusion(reason);
        return entity;
    }

    public UUID getRecommendationId() {
        return recommendationId;
    }

    public UUID getCandidateUserId() {
        return candidateUserId;
    }

    public Short getRank() {
        return rank;
    }

    public Short getTotalScore() {
        return totalScore;
    }

    public boolean isExcluded() {
        return excluded;
    }

    public ExclusionReason getExclusionReason() {
        return exclusionReason;
    }

    public String getExplanation() {
        return explanation;
    }

    public BigDecimal getWorkloadRatio() {
        return workloadRatio;
    }
}
