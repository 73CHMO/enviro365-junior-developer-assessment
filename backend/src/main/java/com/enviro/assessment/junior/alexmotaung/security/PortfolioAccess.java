package com.enviro.assessment.junior.alexmotaung.security;

import com.enviro.assessment.junior.alexmotaung.repository.UserAccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("portfolioAccess")
public class PortfolioAccess {
    private final UserAccountRepository accounts;
    public PortfolioAccess(UserAccountRepository accounts) { this.accounts = accounts; }

    @Transactional(readOnly = true)
    public boolean owns(Authentication authentication, Long investorId) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) return false;
        try {
            return accounts.findById(Long.parseLong(jwt.getName()))
                    .map(account -> account.getInvestor().getId().equals(investorId)).orElse(false);
        } catch (NumberFormatException ignored) { return false; }
    }
}
