package com.enviro.assessment.junior.alexmotaung.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import com.enviro.assessment.junior.alexmotaung.dto.*;
import com.enviro.assessment.junior.alexmotaung.exception.*;
import com.enviro.assessment.junior.alexmotaung.model.*;
import com.enviro.assessment.junior.alexmotaung.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawalService {
    private static final BigDecimal MAXIMUM_FRACTION = new BigDecimal("0.90");
    private final InvestorRepository investors;
    private final ProductRepository products;
    private final WithdrawalRepository withdrawals;
    private final Clock clock;

    public WithdrawalService(InvestorRepository investors, ProductRepository products,
                             WithdrawalRepository withdrawals, Clock clock) {
        this.investors = investors;
        this.products = products;
        this.withdrawals = withdrawals;
        this.clock = clock;
    }

    @Transactional
    public WithdrawalResponse create(Long investorId, WithdrawalRequest request) {
        validateRequest(request);
        var investor = investors.findById(investorId)
                .orElseThrow(() -> new ResourceNotFoundException("Investor not found"));
        var product = products.findOwnedProductForUpdate(request.productId(), investorId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found for this investor"));
        if (product.getType() == ProductType.RETIREMENT && investor.getAge() <= 65) {
            throw new BusinessException("Retirement withdrawals require an investor older than 65");
        }
        var amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        var balance = product.getBalance();
        if (amount.compareTo(balance) > 0) {
            throw new BusinessException("Withdrawal exceeds available balance");
        }
        // Compare against the exact limit; never round it upward to allow excess withdrawals.
        if (amount.compareTo(balance.multiply(MAXIMUM_FRACTION)) > 0) {
            throw new BusinessException("Withdrawal exceeds 90% of balance");
        }
        product.setBalance(balance.subtract(amount));
        var withdrawal = withdrawals.save(new Withdrawal(investor, product, amount, LocalDateTime.now(clock)));
        // Product is managed: JPA writes the balance and notice in the same transaction.
        return WithdrawalResponse.from(withdrawal);
    }

    @Transactional(readOnly = true)
    public List<WithdrawalResponse> history(Long investorId) {
        requireInvestor(investorId);
        return withdrawals.findByInvestorIdOrderByCreatedAtDescIdDesc(investorId).stream()
                .map(WithdrawalResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public String export(Long investorId, LocalDateTime from, LocalDateTime to) {
        requireInvestor(investorId);
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException("From date must be before or equal to to date");
        }
        var csv = new StringBuilder("id,investorId,productId,productName,amount,createdAt,status\r\n");
        for (var withdrawal : withdrawals.findStatement(investorId, from, to)) {
            csv.append(withdrawal.getId()).append(',')
                    .append(investorId).append(',')
                    .append(withdrawal.getProduct().getId()).append(',')
                    .append(csvCell(withdrawal.getProduct().getName())).append(',')
                    .append(withdrawal.getAmount().setScale(2).toPlainString()).append(',')
                    .append(withdrawal.getCreatedAt()).append(',')
                    .append(withdrawal.getStatus()).append("\r\n");
        }
        return csv.toString();
    }

    private void requireInvestor(Long investorId) {
        if (!investors.existsById(investorId)) {
            throw new ResourceNotFoundException("Investor not found");
        }
    }

    private static void validateRequest(WithdrawalRequest request) {
        if (request == null || request.productId() == null || request.productId() <= 0) {
            throw new BusinessException("A positive product ID is required");
        }
        var amount = request.amount();
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2
                || amount.precision() - amount.scale() > 17) {
            throw new BusinessException("Amount must be positive, with at most 17 integer digits and 2 decimal places");
        }
    }

    private static String csvCell(String value) {
        // Quoting alone does not stop spreadsheet formula interpretation.
        if (!value.isEmpty() && "=+-@\t\r\n".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
