package com.enviro.assessment.junior.alexmotaung.dto;

import java.math.BigDecimal;
import com.enviro.assessment.junior.alexmotaung.model.ProductType;

public record ProductDTO(Long id, String name, ProductType type, BigDecimal balance) {
}
