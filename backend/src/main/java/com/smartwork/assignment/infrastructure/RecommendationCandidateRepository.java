package com.smartwork.assignment.infrastructure;

import com.smartwork.assignment.domain.RecommendationCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecommendationCandidateRepository extends JpaRepository<RecommendationCandidate, UUID> {

    List<RecommendationCandidate> findByRecommendationIdOrderByRankAsc(UUID recommendationId);

    void deleteByRecommendationId(UUID recommendationId);
}
