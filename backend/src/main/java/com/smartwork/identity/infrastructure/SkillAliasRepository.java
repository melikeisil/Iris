package com.smartwork.identity.infrastructure;

import com.smartwork.identity.domain.SkillAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SkillAliasRepository extends JpaRepository<SkillAlias, UUID> {

    @Query("""
            SELECT a FROM SkillAlias a
              JOIN FETCH a.skill s
             WHERE s.organizationId = :organizationId AND s.deletedAt IS NULL
            """)
    List<SkillAlias> findAllInOrganization(@Param("organizationId") UUID organizationId);
}
