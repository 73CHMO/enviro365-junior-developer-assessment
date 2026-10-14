package com.enviro.assessment.junior.alexmotaung.repository;

import java.util.Optional;
import com.enviro.assessment.junior.alexmotaung.model.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Query("select t.account.id from RefreshToken t where t.tokenHash = :hash")
    Optional<Long> findAccountIdByTokenHash(@Param("hash") String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :hash")
    Optional<RefreshToken> findForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("update RefreshToken t set t.revoked = true where t.account.id = :accountId")
    void revokeForAccount(@Param("accountId") Long accountId);
}
