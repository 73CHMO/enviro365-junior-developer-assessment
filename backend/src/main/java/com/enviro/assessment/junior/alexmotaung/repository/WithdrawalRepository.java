package com.enviro.assessment.junior.alexmotaung.repository;

import java.time.LocalDateTime;
import java.util.List;
import com.enviro.assessment.junior.alexmotaung.model.Withdrawal;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {
    @EntityGraph(attributePaths = {"product", "investor"})
    List<Withdrawal> findByInvestorIdOrderByCreatedAtDescIdDesc(Long investorId);

    @EntityGraph(attributePaths = {"product", "investor"})
    @Query("""
        select w from Withdrawal w where w.investor.id = :investorId
        and (:from is null or w.createdAt >= :from)
        and (:to is null or w.createdAt <= :to)
        order by w.createdAt desc, w.id desc
        """)
    List<Withdrawal> findStatement(@Param("investorId") Long investorId,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);
}
