package com.enviro.assessment.junior.alexmotaung.service;

import com.enviro.assessment.junior.alexmotaung.dto.*;
import com.enviro.assessment.junior.alexmotaung.exception.ResourceNotFoundException;
import com.enviro.assessment.junior.alexmotaung.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioService {
    private final InvestorRepository investors;
    private final ProductRepository products;

    public PortfolioService(InvestorRepository investors, ProductRepository products) {
        this.investors = investors;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public PortfolioDTO getPortfolio(Long investorId) {
        var investor = investors.findById(investorId)
                .orElseThrow(() -> new ResourceNotFoundException("Investor not found"));
        var investorDTO = new InvestorDTO(investor.getId(), investor.getFirstName(),
                investor.getLastName(), investor.getEmail().substring(0, 1) + "***"
                + investor.getEmail().substring(investor.getEmail().indexOf('@')), investor.getAge());
        var productDTOs = products.findByInvestorIdOrderByIdAsc(investorId).stream()
                .map(p -> new ProductDTO(p.getId(), p.getName(), p.getType(), p.getBalance()))
                .toList();
        return new PortfolioDTO(investorDTO, productDTOs);
    }
}
