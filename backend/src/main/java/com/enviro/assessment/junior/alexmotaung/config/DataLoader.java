package com.enviro.assessment.junior.alexmotaung.config;

import java.math.BigDecimal;
import java.util.List;
import com.enviro.assessment.junior.alexmotaung.model.*;
import com.enviro.assessment.junior.alexmotaung.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.seed-data", havingValue = "true")
public class DataLoader implements CommandLineRunner {
    private final InvestorRepository investors;
    private final ProductRepository products;

    public DataLoader(InvestorRepository investors, ProductRepository products) {
        this.investors = investors;
        this.products = products;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (investors.count() != 0) {
            return;
        }
        var alice = investors.save(new Investor("Alice", "Mokoena", "alice@example.com", 70));
        var thabo = investors.save(new Investor("Thabo", "Nkosi", "thabo@example.com", 65));
        products.saveAll(List.of(
                new Product("Retirement Annuity", ProductType.RETIREMENT, new BigDecimal("100000.00"), alice),
                new Product("Discretionary Portfolio", ProductType.DISCRETIONARY, new BigDecimal("50000.00"), alice),
                new Product("Retirement Fund", ProductType.RETIREMENT, new BigDecimal("80000.00"), thabo),
                new Product("Savings Account", ProductType.SAVINGS, new BigDecimal("20000.00"), thabo)));
    }
}
