package com.enviro.assessment.junior.alexmotaung.config;

import java.math.BigDecimal;
import java.util.List;
import com.enviro.assessment.junior.alexmotaung.model.*;
import com.enviro.assessment.junior.alexmotaung.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;

@Component
@ConditionalOnProperty(name = "app.seed-data", havingValue = "true")
public class DataLoader implements CommandLineRunner {
    private final InvestorRepository investors;
    private final ProductRepository products;
    private final UserAccountRepository accounts;
    private final PasswordEncoder passwords;
    private final String demoPassword;

    public DataLoader(InvestorRepository investors, ProductRepository products, UserAccountRepository accounts,
                      PasswordEncoder passwords, @Value("${app.security.demo-password}") String demoPassword) {
        this.investors = investors;
        this.products = products;
        this.accounts = accounts;
        this.passwords = passwords;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (investors.count() == 0) {
        var alice = investors.save(new Investor("Alice", "Mokoena", "alice@example.com", 70));
        var thabo = investors.save(new Investor("Thabo", "Nkosi", "thabo@example.com", 65));
        products.saveAll(List.of(
                new Product("Retirement Annuity", ProductType.RETIREMENT, new BigDecimal("100000.00"), alice),
                new Product("Discretionary Portfolio", ProductType.DISCRETIONARY, new BigDecimal("50000.00"), alice),
                new Product("Retirement Fund", ProductType.RETIREMENT, new BigDecimal("80000.00"), thabo),
                new Product("Savings Account", ProductType.SAVINGS, new BigDecimal("20000.00"), thabo)));
        }
        for (String email : List.of("alice@example.com", "thabo@example.com")) {
            if (!accounts.existsByEmail(email)) {
                investors.findByEmail(email).ifPresent(investor -> accounts.save(
                        new UserAccount(email, passwords.encode(demoPassword), investor, null, null, null)));
            }
        }
    }
}
