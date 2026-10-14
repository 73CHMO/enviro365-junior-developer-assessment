package com.enviro.assessment.junior.alexmotaung;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import com.enviro.assessment.junior.alexmotaung.exception.ResourceNotFoundException;
import com.enviro.assessment.junior.alexmotaung.model.*;
import com.enviro.assessment.junior.alexmotaung.repository.*;
import com.enviro.assessment.junior.alexmotaung.service.PortfolioService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {
    @Mock InvestorRepository investors;
    @Mock ProductRepository products;
    @InjectMocks PortfolioService service;

    @Test
    void mapsInvestorAndProductsWithoutExposingEntities() {
        var investor = new Investor("Alex", "Motaung", "alex@example.com", 66);
        when(investors.findById(1L)).thenReturn(Optional.of(investor));
        when(products.findByInvestorIdOrderByIdAsc(1L)).thenReturn(List.of(
                new Product("Fund", ProductType.SAVINGS, new BigDecimal("100.00"), investor)));
        var result = service.getPortfolio(1L);
        assertThat(result.investor().firstName()).isEqualTo("Alex");
        assertThat(result.products()).hasSize(1);
        assertThat(result.products().getFirst().balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void missingInvestorFailsBeforeProductQuery() {
        when(investors.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPortfolio(99L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(products);
    }

    @Test
    void investorWithoutProductsHasEmptyPortfolio() {
        when(investors.findById(1L)).thenReturn(Optional.of(new Investor("A", "B", "a@example.com", 40)));
        when(products.findByInvestorIdOrderByIdAsc(1L)).thenReturn(List.of());
        assertThat(service.getPortfolio(1L).products()).isEmpty();
    }
}
