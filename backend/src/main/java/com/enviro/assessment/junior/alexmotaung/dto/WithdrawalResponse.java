package com.enviro.assessment.junior.alexmotaung.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.enviro.assessment.junior.alexmotaung.model.Withdrawal;
import com.enviro.assessment.junior.alexmotaung.model.WithdrawalStatus;

public record WithdrawalResponse(Long id, Long productId, String productName,
                                 BigDecimal amount, LocalDateTime createdAt, WithdrawalStatus status) {
    public static WithdrawalResponse from(Withdrawal withdrawal) {
        return new WithdrawalResponse(withdrawal.getId(), withdrawal.getProduct().getId(),
                withdrawal.getProduct().getName(), withdrawal.getAmount(),
                withdrawal.getCreatedAt(), withdrawal.getStatus());
    }
}
