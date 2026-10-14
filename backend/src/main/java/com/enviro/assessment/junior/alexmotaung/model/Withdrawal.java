package com.enviro.assessment.junior.alexmotaung.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "withdrawals", indexes = @Index(name = "idx_withdrawal_investor_time", columnList = "investor_id,created_at"))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Withdrawal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investor_id", nullable = false, updatable = false)
    private Investor investor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;
    @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2)
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;
    @NotNull @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @NotNull @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false)
    private WithdrawalStatus status;

    public Withdrawal(Investor investor, Product product, BigDecimal amount, LocalDateTime createdAt) {
        this.investor = investor;
        this.product = product;
        this.amount = amount;
        this.createdAt = createdAt;
        this.status = WithdrawalStatus.COMPLETED;
    }
}
