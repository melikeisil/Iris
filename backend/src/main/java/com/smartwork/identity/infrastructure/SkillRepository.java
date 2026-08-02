package com.smartwork.identity.infrastructure;

import com.smartwork.identity.domain.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    @Query("""
            SELECT s FROM Skill s
             WHERE s.organizationId = :organizationId AND s.deletedAt IS NULL
             ORDER BY s.category, s.code
            """)
    List<Skill> findAllInOrganization(@Param("organizationId") UUID organizationId);

    @Query("""
            SELECT s FROM Skill s
             WHERE s.organizationId = :organizationId AND s.code = :code AND s.deletedAt IS NULL
            """)
    Optional<Skill> findByCode(@Param("organizationId") UUID organizationId, @Param("code") String code);

    @Query("""
            SELECT s FROM Skill s
             WHERE s.organizationId = :organizationId AND s.id = :id AND s.deletedAt IS NULL
            """)
    Optional<Skill> findByIdInOrganization(@Param("id") UUID id,
                                           @Param("organizationId") UUID organizationId);

    @Query("""
            SELECT s FROM Skill s
             WHERE s.organizationId = :organizationId AND s.code IN :codes AND s.deletedAt IS NULL
            """)
    List<Skill> findByCodes(@Param("organizationId") UUID organizationId,
                            @Param("codes") List<String> codes);
}
