package com.enviro.assessment.junior.alexmotaung;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import com.enviro.assessment.junior.alexmotaung.dto.WithdrawalRequest;
import com.enviro.assessment.junior.alexmotaung.exception.BusinessException;
import com.enviro.assessment.junior.alexmotaung.model.*;
import com.enviro.assessment.junior.alexmotaung.repository.*;
import com.enviro.assessment.junior.alexmotaung.service.WithdrawalService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.seed-data=false",
        "spring.datasource.url=jdbc:h2:mem:api-tests;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
        "logging.level.root=WARN"
})
@AutoConfigureMockMvc
class WithdrawalControllerTest {
    @Autowired MockMvc mvc;
    @Autowired InvestorRepository investors;
    @Autowired ProductRepository products;
    @Autowired WithdrawalRepository withdrawals;
    @Autowired WithdrawalService service;
    @Autowired JdbcTemplate jdbc;
    Investor investor;
    Product savings;
    Product retirement;

    @BeforeEach
    void resetDatabase() {
        withdrawals.deleteAllInBatch();
        products.deleteAllInBatch();
        investors.deleteAllInBatch();
        investor = investors.save(new Investor("Alex", "Motaung", "alex@example.com", 65));
        savings = products.save(new Product("Savings", ProductType.SAVINGS, new BigDecimal("100.00"), investor));
        retirement = products.save(new Product("Retirement", ProductType.RETIREMENT, new BigDecimal("100.00"), investor));
    }

    String base() {
        return "/api/investors/" + investor.getId();
    }

    String request(Long productId, String amount) {
        return "{\"productId\":" + productId + ",\"amount\":" + amount + "}";
    }

    @Test
    void portfolioContainsInvestorAndProducts() throws Exception {
        mvc.perform(get(base() + "/portfolio")).andExpect(status().isOk())
                .andExpect(jsonPath("$.investor.firstName").value("Alex"))
                .andExpect(jsonPath("$.products.length()").value(2))
                .andExpect(jsonPath("$.products[0].balance").value(100.00));
    }

    @Test
    void unknownInvestorReturnsConsistent404() throws Exception {
        mvc.perform(get("/api/investors/999999/portfolio")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Investor not found"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void successfulWithdrawalChangesBalanceAndHistory() throws Exception {
        mvc.perform(post(base() + "/withdrawals").contentType(MediaType.APPLICATION_JSON)
                .content(request(savings.getId(), "90.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(90.00));
        assertThat(products.findById(savings.getId()).orElseThrow().getBalance()).isEqualByComparingTo("10.00");
        mvc.perform(get(base() + "/withdrawals")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "1.001", "null", "100000000000000000.00"})
    void beanValidationRejectsInvalidAmounts(String amount) throws Exception {
        mvc.perform(post(base() + "/withdrawals").contentType(MediaType.APPLICATION_JSON)
                .content(request(savings.getId(), amount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.amount").exists());
        assertThat(withdrawals.count()).isZero();
        assertThat(products.findById(savings.getId()).orElseThrow().getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void missingFieldsAreReported() throws Exception {
        mvc.perform(post(base() + "/withdrawals").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.productId").exists())
                .andExpect(jsonPath("$.details.amount").exists());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post(base() + "/withdrawals").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void negativeProductIdReturns400() throws Exception {
        mvc.perform(post(base() + "/withdrawals").contentType(MediaType.APPLICATION_JSON)
                .content(request(-1L, "10")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.productId").exists());
    }

    @Test
    void age65RetirementWithdrawalIsRejected() throws Exception {
        mvc.perform(post(base() + "/withdrawals").contentType(MediaType.APPLICATION_JSON)
                .content(request(retirement.getId(), "10.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Retirement withdrawals require an investor older than 65"));
        assertThat(withdrawals.count()).isZero();
        assertThat(products.findById(retirement.getId()).orElseThrow().getBalance()).isEqualByComparingTo("100.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"90.01", "100.01"})
    void businessRuleRejectionsDoNotWriteAnything(String amount) throws Exception {
        mvc.perform(post(base() + "/withdrawals").contentType(MediaType.APPLICATION_JSON)
                .content(request(savings.getId(), amount))).andExpect(status().isBadRequest());
        assertThat(withdrawals.count()).isZero();
        assertThat(products.findById(savings.getId()).orElseThrow().getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void anotherInvestorsProductCannotBeWithdrawn() throws Exception {
        var other = investors.save(new Investor("Other", "Owner", "other@example.com", 70));
        var otherProduct = products.save(new Product("Other Fund", ProductType.SAVINGS, new BigDecimal("100.00"), other));
        mvc.perform(post(base() + "/withdrawals").contentType(MediaType.APPLICATION_JSON)
                .content(request(otherProduct.getId(), "10.00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Product not found for this investor"));
        assertThat(products.findById(otherProduct.getId()).orElseThrow().getBalance()).isEqualByComparingTo("100.00");
        assertThat(withdrawals.count()).isZero();
    }

    @Test
    void historyIsNewestFirstAndScopedToInvestor() throws Exception {
        var from = LocalDateTime.of(2026, 10, 1, 0, 0);
        var to = LocalDateTime.of(2026, 10, 14, 12, 0);
        withdrawals.save(new Withdrawal(investor, savings, new BigDecimal("1.00"), from));
        withdrawals.save(new Withdrawal(investor, savings, new BigDecimal("2.00"), to));
        var other = investors.save(new Investor("Other", "Owner", "other@example.com", 70));
        var otherProduct = products.save(new Product("Other", ProductType.SAVINGS, new BigDecimal("100.00"), other));
        withdrawals.save(new Withdrawal(other, otherProduct, new BigDecimal("3.00"), to));
        mvc.perform(get(base() + "/withdrawals")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].amount").value(2.00))
                .andExpect(jsonPath("$[1].amount").value(1.00));
    }

    @Test
    void emptyHistoryAndExportWork() throws Exception {
        mvc.perform(get(base() + "/withdrawals")).andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mvc.perform(get(base() + "/withdrawals/export")).andExpect(status().isOk())
                .andExpect(content().string("id,investorId,productId,productName,amount,createdAt,status\r\n"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"withdrawals-" + investor.getId() + ".csv\""))
                .andExpect(content().contentType("text/csv;charset=UTF-8"));
    }

    @Test
    void csvFiltersAreInclusiveAndOptional() throws Exception {
        var first = LocalDateTime.of(2026, 10, 1, 0, 0);
        var last = first.plusDays(1);
        withdrawals.save(new Withdrawal(investor, savings, new BigDecimal("1.00"), first.minusSeconds(1)));
        withdrawals.save(new Withdrawal(investor, savings, new BigDecimal("2.00"), first));
        withdrawals.save(new Withdrawal(investor, savings, new BigDecimal("3.00"), last));
        withdrawals.save(new Withdrawal(investor, savings, new BigDecimal("4.00"), last.plusSeconds(1)));
        var csv = mvc.perform(get(base() + "/withdrawals/export")
                .param("from", first.toString()).param("to", last.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv).contains(",2.00,", ",3.00,").doesNotContain(",1.00,", ",4.00,");
        var lowerOnly = mvc.perform(get(base() + "/withdrawals/export").param("from", last.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(lowerOnly).contains(",3.00,", ",4.00,").doesNotContain(",2.00,");
        var upperOnly = mvc.perform(get(base() + "/withdrawals/export").param("to", first.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(upperOnly).contains(",1.00,", ",2.00,").doesNotContain(",3.00,");
        var all = mvc.perform(get(base() + "/withdrawals/export")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(all).contains(",1.00,", ",2.00,", ",3.00,", ",4.00,");
    }

    @Test
    void invalidDatesReturnConsistent400() throws Exception {
        mvc.perform(get(base() + "/withdrawals/export").param("from", "not-a-date"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.from").exists());
        mvc.perform(get(base() + "/withdrawals/export")
                .param("from", "2026-10-14T12:00:00").param("to", "2026-10-01T00:00:00"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
    }

    @Test
    void missingInvestorHistoryAndExportReturn404() throws Exception {
        mvc.perform(get("/api/investors/999999/withdrawals")).andExpect(status().isNotFound());
        mvc.perform(get("/api/investors/999999/withdrawals/export")).andExpect(status().isNotFound());
    }

    @Test
    void allowedFrontendCanReadCsvHeaders() throws Exception {
        mvc.perform(get(base() + "/withdrawals/export").header("Origin", "http://localhost:4200"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Expose-Headers", "Content-Disposition"));
        mvc.perform(options(base() + "/withdrawals").header("Origin", "https://unapproved.example")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }

    @Test
    void databaseFailureRollsBackNoticeAndBalanceTogether() {
        jdbc.execute("ALTER TABLE withdrawals ADD CONSTRAINT test_failure CHECK (amount < 50)");
        try {
            assertThatThrownBy(() -> service.create(investor.getId(),
                    new WithdrawalRequest(savings.getId(), new BigDecimal("50.00"))))
                    .isInstanceOf(RuntimeException.class);
            assertThat(products.findById(savings.getId()).orElseThrow().getBalance()).isEqualByComparingTo("100.00");
            assertThat(withdrawals.count()).isZero();
        } finally {
            jdbc.execute("ALTER TABLE withdrawals DROP CONSTRAINT test_failure");
        }
    }

    @Test
    void concurrentWithdrawalsValidateAgainstLockedCurrentBalance() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start timed out");
            }
            try {
                service.create(investor.getId(), new WithdrawalRequest(savings.getId(), new BigDecimal("60.00")));
                return true;
            } catch (BusinessException rejected) {
                return false;
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(attempt);
            var two = executor.submit(attempt);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var outcomes = List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
        }
        assertThat(products.findById(savings.getId()).orElseThrow().getBalance()).isEqualByComparingTo("40.00");
        assertThat(withdrawals.count()).isEqualTo(1);
    }
}
