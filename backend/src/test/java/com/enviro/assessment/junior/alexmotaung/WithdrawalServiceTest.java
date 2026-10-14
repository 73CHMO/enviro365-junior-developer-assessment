package com.enviro.assessment.junior.alexmotaung;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.enviro.assessment.junior.alexmotaung.dto.WithdrawalRequest;
import com.enviro.assessment.junior.alexmotaung.exception.*;
import com.enviro.assessment.junior.alexmotaung.model.*;
import com.enviro.assessment.junior.alexmotaung.repository.*;
import com.enviro.assessment.junior.alexmotaung.service.WithdrawalService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {
    @Mock InvestorRepository investors;
    @Mock ProductRepository products;
    @Mock WithdrawalRepository withdrawals;
    WithdrawalService service;
    final Clock clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Africa/Johannesburg"));

    @BeforeEach
    void setup() {
        service = new WithdrawalService(investors, products, withdrawals, clock);
    }

    Product prepare(int age, ProductType type, String balance) {
        var investor = new Investor("A", "B", "a@example.com", age);
        var product = new Product("Fund", type, new BigDecimal(balance), investor);
        when(investors.findById(1L)).thenReturn(Optional.of(investor));
        when(products.findOwnedProductForUpdate(2L, 1L)).thenReturn(Optional.of(product));
        return product;
    }

    void saveReturnsEntity() {
        when(withdrawals.save(any(Withdrawal.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest
    @ValueSource(ints = {64, 65})
    void retirementRequiresStrictlyOlderThan65(int age) {
        var product = prepare(age, ProductType.RETIREMENT, "100.00");
        assertThatThrownBy(() -> service.create(1L, request("10.00")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("older than 65");
        assertThat(product.getBalance()).isEqualByComparingTo("100.00");
        verifyNoInteractions(withdrawals);
    }

    @Test
    void age66CanWithdrawRetirement() {
        var product = prepare(66, ProductType.RETIREMENT, "100.00");
        saveReturnsEntity();
        var result = service.create(1L, request("90.00"));
        assertThat(product.getBalance()).isEqualByComparingTo("10.00");
        assertThat(result.createdAt()).isEqualTo(LocalDateTime.of(2026, 10, 14, 12, 0));
        assertThat(result.status()).isEqualTo(WithdrawalStatus.COMPLETED);
    }

    @Test
    void youngerInvestorCanWithdrawNonRetirement() {
        var product = prepare(30, ProductType.DISCRETIONARY, "100.00");
        saveReturnsEntity();
        service.create(1L, request("90"));
        assertThat(product.getBalance()).isEqualByComparingTo("10.00");
    }

    @ParameterizedTest
    @CsvSource({"90.01,90%", "100.01,available balance"})
    void excessiveAmountsLeaveBalanceUntouched(String amount, String message) {
        var product = prepare(70, ProductType.SAVINGS, "100.00");
        assertThatThrownBy(() -> service.create(1L, request(amount)))
                .isInstanceOf(BusinessException.class).hasMessageContaining(message);
        assertThat(product.getBalance()).isEqualByComparingTo("100.00");
        verifyNoInteractions(withdrawals);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "1.001", "100000000000000000.00"})
    void invalidAmountRejectedBeforeDatabaseAccess(String amount) {
        assertThatThrownBy(() -> service.create(1L, request(amount))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(investors, products, withdrawals);
    }

    @Test
    void missingAmountRejectedBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.create(1L, new WithdrawalRequest(2L, null)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(investors, products, withdrawals);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void invalidProductIdRejected(long productId) {
        assertThatThrownBy(() -> service.create(1L, new WithdrawalRequest(productId, BigDecimal.ONE)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(investors, products, withdrawals);
    }

    @Test
    void missingInvestorRejected() {
        when(investors.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(1L, request("10"))).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(products, withdrawals);
    }

    @Test
    void missingOrUnownedProductRejected() {
        when(investors.findById(1L)).thenReturn(Optional.of(new Investor("A", "B", "a@example.com", 70)));
        when(products.findOwnedProductForUpdate(2L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(1L, request("10"))).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(withdrawals);
    }

    @Test
    void limitDoesNotRoundUpFractionalCents() {
        var product = prepare(70, ProductType.SAVINGS, "0.03");
        assertThatThrownBy(() -> service.create(1L, request("0.03"))).hasMessageContaining("90%");
        assertThat(product.getBalance()).isEqualByComparingTo("0.03");
    }

    @Test
    void zeroBalanceRejectsPositiveWithdrawal() {
        prepare(70, ProductType.SAVINGS, "0.00");
        assertThatThrownBy(() -> service.create(1L, request("0.01"))).hasMessageContaining("available balance");
    }

    @Test
    void emptyHistoryReturnsEmptyList() {
        when(investors.existsById(1L)).thenReturn(true);
        when(withdrawals.findByInvestorIdOrderByCreatedAtDescIdDesc(1L)).thenReturn(List.of());
        assertThat(service.history(1L)).isEmpty();
    }

    @Test
    void unknownInvestorHistoryFails() {
        assertThatThrownBy(() -> service.history(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reversedExportRangeRejected() {
        when(investors.existsById(1L)).thenReturn(true);
        var time = LocalDateTime.now(clock);
        assertThatThrownBy(() -> service.export(1L, time, time.minusDays(1)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(withdrawals);
    }

    @Test
    void emptyExportContainsHeader() {
        when(investors.existsById(1L)).thenReturn(true);
        when(withdrawals.findStatement(1L, null, null)).thenReturn(List.of());
        assertThat(service.export(1L, null, null))
                .isEqualTo("id,investorId,productId,productName,amount,createdAt,status\r\n");
    }

    @Test
    void csvEscapesCommasQuotesAndLineBreaks() {
        var investor = new Investor("A", "B", "a@example.com", 70);
        var product = new Product("Fund, \"A\"\nB", ProductType.SAVINGS, new BigDecimal("100.00"), investor);
        when(investors.existsById(1L)).thenReturn(true);
        when(withdrawals.findStatement(1L, null, null)).thenReturn(List.of(
                new Withdrawal(investor, product, new BigDecimal("5.00"), LocalDateTime.now(clock))));
        assertThat(service.export(1L, null, null)).contains("\"Fund, \"\"A\"\"\nB\"");
    }

    @Test
    void spreadsheetFormulaNamesAreNeutralized() {
        var investor = new Investor("A", "B", "a@example.com", 70);
        var product = new Product("=1+1", ProductType.SAVINGS, new BigDecimal("100.00"), investor);
        when(investors.existsById(1L)).thenReturn(true);
        when(withdrawals.findStatement(1L, null, null)).thenReturn(List.of(
                new Withdrawal(investor, product, new BigDecimal("5.00"), LocalDateTime.now(clock))));
        assertThat(service.export(1L, null, null)).contains("\"'=1+1\"");
    }

    private WithdrawalRequest request(String amount) {
        return new WithdrawalRequest(2L, new BigDecimal(amount));
    }
}
