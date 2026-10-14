package com.enviro.assessment.junior.alexmotaung.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public record WithdrawalRequest(
        @NotNull(message = "Product ID is required") @Positive(message = "Product ID must be positive") Long productId,
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most 17 integer digits and 2 decimal places")
        BigDecimal amount) {
}
