package com.enviro.assessment.junior.alexmotaung.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products", indexes = @Index(name = "idx_product_investor", columnList = "investor_id"))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank @Column(nullable = false)
    private String name;
    @NotNull @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ProductType type;
    @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2)
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    public Product(String name, ProductType type, BigDecimal balance, Investor investor) {
        this.name = name;
        this.type = type;
        this.balance = balance.setScale(2, RoundingMode.UNNECESSARY);
        this.investor = investor;
    }

    public void setBalance(BigDecimal balance) {
        if (balance == null || balance.signum() < 0) {
            throw new IllegalArgumentException("Balance must be nonnegative");
        }
        this.balance = balance.setScale(2, RoundingMode.UNNECESSARY);
    }
}
