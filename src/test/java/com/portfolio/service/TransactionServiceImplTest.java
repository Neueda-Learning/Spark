package com.portfolio.service;

import com.portfolio.dto.TransactionRequest;
import com.portfolio.dto.TransactionResponse;
import com.portfolio.model.Holding;
import com.portfolio.model.Portfolio;
import com.portfolio.model.Stock;
import com.portfolio.model.Transaction;
import com.portfolio.repository.HoldingRepository;
import com.portfolio.repository.PortfolioRepository;
import com.portfolio.repository.StockRepository;
import com.portfolio.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PriceService priceService;

    @InjectMocks
    private TransactionServiceImpl service;

    private Stock aapl;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        aapl = new Stock(1L, "AAPL", "Apple Inc.", "STOCK", "Technology", "NASDAQ", "USD",
                new BigDecimal("0.0050"), LocalDate.of(2026, 5, 10));
        portfolio = new Portfolio(1L, "My Portfolio", new BigDecimal("100000.00"), LocalDateTime.now());
    }

    @Test
    @DisplayName("BUY creates new holding and deducts cash when no existing holding")
    void buy_noExistingHolding_createsHolding() {
        when(stockRepository.findById(1L)).thenReturn(Optional.of(aapl));
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("195.50"));
        when(holdingRepository.findByPortfolioIdAndStockId(1L, 1L)).thenReturn(Optional.empty());
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            return new Transaction(1L, tx.portfolioId(), tx.stockId(), tx.type(),
                    tx.quantity(), tx.unitPrice(), tx.totalAmount(), LocalDateTime.now());
        });

        TransactionRequest request = new TransactionRequest(1L, "BUY", new BigDecimal("10"));
        TransactionResponse result = service.executeTransaction(1L, request);

        assertThat(result.type()).isEqualTo("BUY");
        assertThat(result.symbol()).isEqualTo("AAPL");
        assertThat(result.quantity()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(result.unitPrice()).isEqualByComparingTo(new BigDecimal("195.50"));
        assertThat(result.totalAmount()).isEqualByComparingTo(new BigDecimal("1955.00"));

        verify(holdingRepository).save(any(Holding.class));
        verify(portfolioRepository).updateCashBalance(eq(1L), eq(new BigDecimal("98045.00")));
    }

    @Test
    @DisplayName("BUY updates existing holding with new average cost")
    void buy_existingHolding_updatesAverageCost() {
        Holding existingHolding = new Holding(1L, 1L, 1L, new BigDecimal("5"), new BigDecimal("180.00"));

        when(stockRepository.findById(1L)).thenReturn(Optional.of(aapl));
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("195.50"));
        when(holdingRepository.findByPortfolioIdAndStockId(1L, 1L)).thenReturn(Optional.of(existingHolding));
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            return new Transaction(2L, tx.portfolioId(), tx.stockId(), tx.type(),
                    tx.quantity(), tx.unitPrice(), tx.totalAmount(), LocalDateTime.now());
        });

        TransactionRequest request = new TransactionRequest(1L, "BUY", new BigDecimal("5"));
        TransactionResponse result = service.executeTransaction(1L, request);

        assertThat(result.type()).isEqualTo("BUY");
        // New avg cost = (5*180 + 5*195.50) / 10 = (900 + 977.50) / 10 = 187.75
        verify(holdingRepository).updateQuantityAndAverageCost(1L, new BigDecimal("10"), new BigDecimal("187.7500"));
    }

    @Test
    @DisplayName("BUY throws when insufficient cash")
    void buy_insufficientCash_throws() {
        Portfolio poorPortfolio = new Portfolio(1L, "Poor", new BigDecimal("100.00"), LocalDateTime.now());

        when(stockRepository.findById(1L)).thenReturn(Optional.of(aapl));
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(poorPortfolio));
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("195.50"));

        TransactionRequest request = new TransactionRequest(1L, "BUY", new BigDecimal("10"));

        assertThatThrownBy(() -> service.executeTransaction(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient cash");

        verify(holdingRepository, never()).save(any());
    }

    @Test
    @DisplayName("SELL reduces holding quantity and adds cash")
    void sell_reducesHolding() {
        Holding holding = new Holding(1L, 1L, 1L, new BigDecimal("10"), new BigDecimal("180.00"));

        when(stockRepository.findById(1L)).thenReturn(Optional.of(aapl));
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("195.50"));
        when(holdingRepository.findByPortfolioIdAndStockId(1L, 1L)).thenReturn(Optional.of(holding));
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            return new Transaction(1L, tx.portfolioId(), tx.stockId(), tx.type(),
                    tx.quantity(), tx.unitPrice(), tx.totalAmount(), LocalDateTime.now());
        });

        TransactionRequest request = new TransactionRequest(1L, "SELL", new BigDecimal("3"));
        TransactionResponse result = service.executeTransaction(1L, request);

        assertThat(result.type()).isEqualTo("SELL");
        assertThat(result.quantity()).isEqualByComparingTo(new BigDecimal("3"));
        verify(holdingRepository).updateQuantityAndAverageCost(1L, new BigDecimal("7"), new BigDecimal("180.00"));
        verify(portfolioRepository).updateCashBalance(eq(1L), eq(new BigDecimal("100586.50")));
    }

    @Test
    @DisplayName("SELL removes holding when quantity reaches zero")
    void sell_fullQuantity_removesHolding() {
        Holding holding = new Holding(1L, 1L, 1L, new BigDecimal("10"), new BigDecimal("180.00"));

        when(stockRepository.findById(1L)).thenReturn(Optional.of(aapl));
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("195.50"));
        when(holdingRepository.findByPortfolioIdAndStockId(1L, 1L)).thenReturn(Optional.of(holding));
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            return new Transaction(1L, tx.portfolioId(), tx.stockId(), tx.type(),
                    tx.quantity(), tx.unitPrice(), tx.totalAmount(), LocalDateTime.now());
        });

        TransactionRequest request = new TransactionRequest(1L, "SELL", new BigDecimal("10"));
        service.executeTransaction(1L, request);

        verify(holdingRepository).deleteById(1L);
    }

    @Test
    @DisplayName("SELL throws when no holding exists")
    void sell_noHolding_throws() {
        when(stockRepository.findById(1L)).thenReturn(Optional.of(aapl));
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("195.50"));
        when(holdingRepository.findByPortfolioIdAndStockId(1L, 1L)).thenReturn(Optional.empty());

        TransactionRequest request = new TransactionRequest(1L, "SELL", new BigDecimal("5"));

        assertThatThrownBy(() -> service.executeTransaction(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No holding found");
    }

    @Test
    @DisplayName("SELL throws when quantity exceeds held shares")
    void sell_tooManyShares_throws() {
        Holding holding = new Holding(1L, 1L, 1L, new BigDecimal("5"), new BigDecimal("180.00"));

        when(stockRepository.findById(1L)).thenReturn(Optional.of(aapl));
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("195.50"));
        when(holdingRepository.findByPortfolioIdAndStockId(1L, 1L)).thenReturn(Optional.of(holding));

        TransactionRequest request = new TransactionRequest(1L, "SELL", new BigDecimal("10"));

        assertThatThrownBy(() -> service.executeTransaction(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient shares");
    }
}
