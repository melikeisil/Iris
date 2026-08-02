package com.smartwork.auth.infrastructure;

import com.smartwork.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :now, t.revokedReason = :reason
             WHERE t.familyId = :familyId AND t.revokedAt IS NULL
            """)
    int revokeFamily(@Param("familyId") UUID familyId,
                     @Param("now") Instant now,
                     @Param("reason") String reason);

    @Modifying
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :now, t.revokedReason = :reason
             WHERE t.userId = :userId AND t.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") UUID userId,
                         @Param("now") Instant now,
                         @Param("reason") String reason);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
