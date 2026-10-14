package com.enviro.assessment.junior.alexmotaung.repository;

import java.util.Optional;
import com.enviro.assessment.junior.alexmotaung.model.UserAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    @EntityGraph(attributePaths = "investor")
    Optional<UserAccount> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByIdentityFingerprint(String fingerprint);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from UserAccount a where a.id = :id")
    Optional<UserAccount> findForUpdate(@Param("id") Long id);
}
