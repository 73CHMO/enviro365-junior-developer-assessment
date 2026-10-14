package com.enviro.assessment.junior.alexmotaung.dto;

import java.util.List;

public record PortfolioDTO(InvestorDTO investor, List<ProductDTO> products) {
}
