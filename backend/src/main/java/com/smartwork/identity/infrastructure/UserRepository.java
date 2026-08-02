package com.smartwork.identity.infrastructure;

import com.smartwork.identity.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every query carries {@code organizationId} explicitly. That is the tenancy boundary:
 * a repository method that cannot be called without a tenant cannot leak across tenants.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = {"roles", "roles.role"})
    @Query("""
            SELECT u FROM User u
             WHERE lower(u.email) = lower(:email)
               AND u.deletedAt IS NULL
            """)
    Optional<User> findActiveByEmail(@Param("email") String email);

    @EntityGraph(attributePaths = {"roles", "roles.role"})
    @Query("""
            SELECT u FROM User u
             WHERE u.id = :id AND u.organizationId = :organizationId AND u.deletedAt IS NULL
            """)
    Optional<User> findByIdInOrganization(@Param("id") UUID id,
                                          @Param("organizationId") UUID organizationId);

    @EntityGraph(attributePaths = {"roles", "roles.role"})
    @Query("""
            SELECT u FROM User u
             WHERE u.organizationId = :organizationId
               AND u.deletedAt IS NULL
               AND (:departmentId IS NULL OR u.departmentId = :departmentId)
               AND (:activeOnly = FALSE OR u.active = TRUE)
               AND (:search IS NULL
                    OR lower(u.fullName) LIKE lower(CONCAT('%', :search, '%'))
                    OR lower(u.email)    LIKE lower(CONCAT('%', :search, '%')))
            """)
    Page<User> search(@Param("organizationId") UUID organizationId,
                      @Param("departmentId") UUID departmentId,
                      @Param("activeOnly") boolean activeOnly,
                      @Param("search") String search,
                      Pageable pageable);

    @Query("""
            SELECT u FROM User u
             WHERE u.organizationId = :organizationId
               AND u.id IN :ids
               AND u.deletedAt IS NULL
            """)
    List<User> findAllInOrganization(@Param("organizationId") UUID organizationId,
                                     @Param("ids") List<UUID> ids);

    boolean existsByOrganizationIdAndEmail(UUID organizationId, String email);
}
