package com.smartwork.team.infrastructure;

import com.smartwork.team.domain.Team;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    @Query("""
            SELECT t FROM Team t
             WHERE t.organizationId = :organizationId
               AND t.deletedAt IS NULL
               AND (:departmentId IS NULL OR t.departmentId = :departmentId)
             ORDER BY t.code
            """)
    List<Team> findAllInOrganization(@Param("organizationId") UUID organizationId,
                                     @Param("departmentId") UUID departmentId);

    @EntityGraph(attributePaths = "members")
    @Query("""
            SELECT t FROM Team t
             WHERE t.id = :id AND t.organizationId = :organizationId AND t.deletedAt IS NULL
            """)
    Optional<Team> findByIdInOrganization(@Param("id") UUID id,
                                          @Param("organizationId") UUID organizationId);

    @Query("""
            SELECT t FROM Team t
             WHERE t.code = :code AND t.organizationId = :organizationId AND t.deletedAt IS NULL
            """)
    Optional<Team> findByCode(@Param("code") String code, @Param("organizationId") UUID organizationId);

    /** Team IDs a person belongs to. Used to build the JWT principal's team scope. */
    @Query("""
            SELECT t.id FROM Team t JOIN t.members m
             WHERE m.userId = :userId AND t.organizationId = :organizationId AND t.deletedAt IS NULL
            """)
    List<UUID> findTeamIdsOfUser(@Param("userId") UUID userId,
                                 @Param("organizationId") UUID organizationId);

    /** Teams this person leads - the scope of every Team Lead capability. */
    @Query("""
            SELECT t.id FROM Team t
             WHERE t.leadUserId = :userId AND t.organizationId = :organizationId AND t.deletedAt IS NULL
            """)
    List<UUID> findTeamIdsLedBy(@Param("userId") UUID userId,
                                @Param("organizationId") UUID organizationId);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);
}
