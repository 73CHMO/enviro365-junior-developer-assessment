package com.enviro.assessment.junior.alexmotaung.repository;

import java.util.List;
import java.util.Optional;
import com.enviro.assessment.junior.alexmotaung.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByInvestorIdOrderByIdAsc(Long investorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :productId and p.investor.id = :investorId")
    Optional<Product> findOwnedProductForUpdate(@Param("productId") Long productId,
                                               @Param("investorId") Long investorId);
}
