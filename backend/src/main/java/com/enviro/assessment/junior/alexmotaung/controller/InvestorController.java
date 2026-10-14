package com.enviro.assessment.junior.alexmotaung.controller;

import com.enviro.assessment.junior.alexmotaung.dto.PortfolioDTO;
import com.enviro.assessment.junior.alexmotaung.service.PortfolioService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/investors")
public class InvestorController {
    private final PortfolioService portfolios;

    public InvestorController(PortfolioService portfolios) {
        this.portfolios = portfolios;
    }

    @GetMapping("/{investorId}/portfolio")
    public PortfolioDTO portfolio(@PathVariable Long investorId) {
        return portfolios.getPortfolio(investorId);
    }
}
