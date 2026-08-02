package com.smartwork.identity.infrastructure;

import com.smartwork.identity.domain.UserSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSkillRepository extends JpaRepository<UserSkill, UUID> {

    @Query("""
            SELECT us FROM UserSkill us
              JOIN FETCH us.skill s
             WHERE us.user.id = :userId AND s.deletedAt IS NULL
             ORDER BY us.proficiency DESC, s.code
            """)
    List<UserSkill> findByUser(@Param("userId") UUID userId);

    Optional<UserSkill> findByUserIdAndSkillId(UUID userId, UUID skillId);

    void deleteByUserIdAndSkillId(UUID userId, UUID skillId);
}
