package com.portfolio.service;

import com.portfolio.dto.WeeklyPerformanceResponse;
import com.portfolio.model.PortfolioPerformanceCache;
import com.portfolio.model.Transaction;
import com.portfolio.repository.MarketPriceDailyRepository;
import com.portfolio.repository.HoldingRepository;
import com.portfolio.repository.PortfolioRepository;
import com.portfolio.repository.PortfolioPerformanceCacheRepository;
import com.portfolio.repository.StockRepository;
import com.portfolio.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceImplTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private PortfolioPerformanceCacheRepository performanceCacheRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private MarketPriceDailyRepository marketPriceDailyRepository;

    @Mock
    private PriceService priceService;

    @InjectMocks
    private PortfolioServiceImpl service;

    @Test
    @DisplayName("Weekly performance returns empty list when there are no market trade dates")
    void getWeeklyPerformance_noTradeDates_returnsEmptyList() {
        when(marketPriceDailyRepository.findLatestTradeDates(7)).thenReturn(List.of());

        List<WeeklyPerformanceResponse> result = service.getWeeklyPerformance(1L);

        assertThat(result).isEmpty();
        verify(performanceCacheRepository, never()).findByPortfolioIdAndPerformanceDates(eq(1L), anyList());
        verify(transactionRepository, never()).findByPortfolioId(1L);
    }

    @Test
    @DisplayName("Weekly performance returns fresh cached cumulative profit data")
    void getWeeklyPerformance_returnsCachedCumulativePerformance() {
        List<LocalDate> tradeDates = List.of(
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2026, 7, 26),
                LocalDate.of(2026, 7, 25)
        );
        List<LocalDate> orderedDates = List.of(
                LocalDate.of(2026, 7, 25),
                LocalDate.of(2026, 7, 26),
                LocalDate.of(2026, 7, 27)
        );
        when(marketPriceDailyRepository.findLatestTradeDates(7)).thenReturn(tradeDates);
        when(marketPriceDailyRepository.findLatestFetchedAtForTradeDates(orderedDates))
                .thenReturn(LocalDate.of(2026, 7, 27).atTime(16, 0));
        when(performanceCacheRepository.findByPortfolioIdAndPerformanceDates(1L, orderedDates)).thenReturn(List.of(
                new PortfolioPerformanceCache(1L, 1L, LocalDate.of(2026, 7, 25), new BigDecimal("700.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("700.00"), LocalDate.of(2026, 7, 27).atTime(16, 0)),
                new PortfolioPerformanceCache(2L, 1L, LocalDate.of(2026, 7, 26), new BigDecimal("700.00"), new BigDecimal("-10.00"), new BigDecimal("-1.43"), new BigDecimal("690.00"), LocalDate.of(2026, 7, 27).atTime(16, 0)),
                new PortfolioPerformanceCache(3L, 1L, LocalDate.of(2026, 7, 27), new BigDecimal("700.00"), new BigDecimal("30.00"), new BigDecimal("4.29"), new BigDecimal("730.00"), LocalDate.of(2026, 7, 27).atTime(16, 0))
        ));

        List<WeeklyPerformanceResponse> result = service.getWeeklyPerformance(1L);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 7, 25));
        assertThat(result.get(0).dailyProfit()).isEqualByComparingTo("0.00");
        assertThat(result.get(0).returnRate()).isEqualByComparingTo("0");

        assertThat(result.get(1).dailyProfit()).isEqualByComparingTo("-10.00");
        assertThat(result.get(1).returnRate()).isEqualByComparingTo("-1.43");

        assertThat(result.get(2).dailyProfit()).isEqualByComparingTo("30.00");
        assertThat(result.get(2).returnRate()).isEqualByComparingTo("4.29");
    verify(transactionRepository, never()).findByPortfolioId(1L);
    verify(performanceCacheRepository, never()).replaceForPortfolio(eq(1L), anyList());
    }

    @Test
    @DisplayName("Weekly performance rebuilds cache when cached rows are incomplete")
    void getWeeklyPerformance_incompleteCache_rebuilds() {
    List<LocalDate> orderedDates = List.of(
        LocalDate.of(2026, 7, 25),
        LocalDate.of(2026, 7, 26)
    );
    when(marketPriceDailyRepository.findLatestTradeDates(7)).thenReturn(List.of(
        LocalDate.of(2026, 7, 26),
        LocalDate.of(2026, 7, 25)
    ));
    when(marketPriceDailyRepository.findLatestFetchedAtForTradeDates(orderedDates))
        .thenReturn(LocalDate.of(2026, 7, 26).atTime(16, 0));
    when(performanceCacheRepository.findByPortfolioIdAndPerformanceDates(1L, orderedDates)).thenReturn(List.of(
        new PortfolioPerformanceCache(1L, 1L, LocalDate.of(2026, 7, 25), new BigDecimal("200.00"), new BigDecimal("100.00"), new BigDecimal("50.00"), new BigDecimal("300.00"), LocalDate.of(2026, 7, 26).atTime(16, 0))
    ));
    when(transactionRepository.findByPortfolioId(1L)).thenReturn(List.of(
        new Transaction(1L, 1L, 10L, "BUY", new BigDecimal("2"), new BigDecimal("100.00"), new BigDecimal("200.00"), LocalDate.of(2026, 7, 25).atTime(10, 0))
    ));
    when(marketPriceDailyRepository.findClosePricesByTradeDate(LocalDate.of(2026, 7, 25), java.util.Set.of(10L)))
        .thenReturn(Map.of(10L, new BigDecimal("110.00")));
    when(marketPriceDailyRepository.findClosePricesByTradeDate(LocalDate.of(2026, 7, 26), java.util.Set.of(10L)))
        .thenReturn(Map.of(10L, new BigDecimal("95.00")));

        List<WeeklyPerformanceResponse> result = service.getWeeklyPerformance(1L);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).dailyProfit()).isEqualByComparingTo("20.00");
    assertThat(result.get(1).dailyProfit()).isEqualByComparingTo("-10.00");
    verify(performanceCacheRepository).replaceForPortfolio(eq(1L), anyList());
    }

    @Test
    @DisplayName("Weekly performance rebuilds cache when cached rows are stale")
    void getWeeklyPerformance_staleCache_rebuilds() {
        List<LocalDate> orderedDates = List.of(
        LocalDate.of(2026, 7, 25),
        LocalDate.of(2026, 7, 26)
        );
        when(marketPriceDailyRepository.findLatestTradeDates(7)).thenReturn(List.of(
        LocalDate.of(2026, 7, 26),
        LocalDate.of(2026, 7, 25)
        ));
        when(marketPriceDailyRepository.findLatestFetchedAtForTradeDates(orderedDates))
        .thenReturn(LocalDate.of(2026, 7, 26).atTime(16, 0));
    when(performanceCacheRepository.findByPortfolioIdAndPerformanceDates(1L, orderedDates)).thenReturn(List.of(
        new PortfolioPerformanceCache(1L, 1L, LocalDate.of(2026, 7, 25), new BigDecimal("200.00"), new BigDecimal("20.00"), new BigDecimal("10.00"), new BigDecimal("220.00"), LocalDate.of(2026, 7, 25).atTime(16, 0)),
        new PortfolioPerformanceCache(2L, 1L, LocalDate.of(2026, 7, 26), new BigDecimal("200.00"), new BigDecimal("-10.00"), new BigDecimal("-5.00"), new BigDecimal("190.00"), LocalDate.of(2026, 7, 25).atTime(16, 0))
    ));
        when(transactionRepository.findByPortfolioId(1L)).thenReturn(List.of(
        new Transaction(
            1L,
            1L,
            10L,
            "BUY",
            new BigDecimal("2"),
            new BigDecimal("100.00"),
            new BigDecimal("200.00"),
            LocalDate.of(2026, 7, 25).atTime(10, 0)
        )
        ));
        when(marketPriceDailyRepository.findClosePricesByTradeDate(LocalDate.of(2026, 7, 25), java.util.Set.of(10L)))
        .thenReturn(java.util.Map.of(10L, new BigDecimal("110.00")));
        when(marketPriceDailyRepository.findClosePricesByTradeDate(LocalDate.of(2026, 7, 26), java.util.Set.of(10L)))
        .thenReturn(java.util.Map.of(10L, new BigDecimal("95.00")));

        List<WeeklyPerformanceResponse> result = service.getWeeklyPerformance(1L);

        assertThat(result).hasSize(2);
    assertThat(result.get(0).dailyProfit()).isEqualByComparingTo("20.00");
    assertThat(result.get(1).dailyProfit()).isEqualByComparingTo("-10.00");
    verify(performanceCacheRepository).replaceForPortfolio(eq(1L), anyList());
    }

    @Test
    @DisplayName("Weekly performance rebuild uses weighted average cost and sell carry-forward")
    void getWeeklyPerformance_rebuildsCacheFromTransactions() {
    List<LocalDate> orderedDates = List.of(
        LocalDate.of(2026, 7, 25),
        LocalDate.of(2026, 7, 26),
        LocalDate.of(2026, 7, 27)
    );
    when(marketPriceDailyRepository.findLatestTradeDates(7)).thenReturn(List.of(
        LocalDate.of(2026, 7, 27),
        LocalDate.of(2026, 7, 26),
        LocalDate.of(2026, 7, 25)
    ));
    when(marketPriceDailyRepository.findLatestFetchedAtForTradeDates(orderedDates))
        .thenReturn(LocalDate.of(2026, 7, 27).atTime(16, 0));
    when(performanceCacheRepository.findByPortfolioIdAndPerformanceDates(1L, orderedDates)).thenReturn(List.of());
    when(transactionRepository.findByPortfolioId(1L)).thenReturn(List.of(
        new Transaction(1L, 1L, 10L, "BUY", new BigDecimal("10"), new BigDecimal("100.00"), new BigDecimal("1000.00"), LocalDate.of(2026, 7, 25).atTime(10, 0)),
        new Transaction(2L, 1L, 10L, "BUY", new BigDecimal("10"), new BigDecimal("120.00"), new BigDecimal("1200.00"), LocalDate.of(2026, 7, 26).atTime(10, 0)),
        new Transaction(3L, 1L, 10L, "SELL", new BigDecimal("5"), new BigDecimal("130.00"), new BigDecimal("650.00"), LocalDate.of(2026, 7, 27).atTime(10, 0))
    ));
    when(marketPriceDailyRepository.findClosePricesByTradeDate(LocalDate.of(2026, 7, 25), java.util.Set.of(10L)))
        .thenReturn(Map.of(10L, new BigDecimal("110.00")));
    when(marketPriceDailyRepository.findClosePricesByTradeDate(LocalDate.of(2026, 7, 26), java.util.Set.of(10L)))
        .thenReturn(Map.of(10L, new BigDecimal("130.00")));
    when(marketPriceDailyRepository.findClosePricesByTradeDate(LocalDate.of(2026, 7, 27), java.util.Set.of(10L)))
        .thenReturn(Map.of(10L, new BigDecimal("90.00")));

    List<WeeklyPerformanceResponse> result = service.getWeeklyPerformance(1L);

    assertThat(result).hasSize(3);
    assertThat(result.get(0).dailyProfit()).isEqualByComparingTo("100.00");
    assertThat(result.get(0).returnRate()).isEqualByComparingTo("10.00");
    assertThat(result.get(1).dailyProfit()).isEqualByComparingTo("400.00");
    assertThat(result.get(1).returnRate()).isEqualByComparingTo("18.18");
    assertThat(result.get(2).dailyProfit()).isEqualByComparingTo("-300.00");
    assertThat(result.get(2).returnRate()).isEqualByComparingTo("-18.18");
        verify(performanceCacheRepository).replaceForPortfolio(eq(1L), anyList());
    }

    @Test
    @DisplayName("Weekly performance keeps invested cost when a close price is missing")
    void getWeeklyPerformance_missingClosePrice_returnsZeroProfitForThatDate() {
    List<LocalDate> orderedDates = List.of(LocalDate.of(2026, 7, 25));
    when(marketPriceDailyRepository.findLatestTradeDates(7)).thenReturn(List.of(LocalDate.of(2026, 7, 25)));
    when(marketPriceDailyRepository.findLatestFetchedAtForTradeDates(orderedDates))
        .thenReturn(LocalDate.of(2026, 7, 25).atTime(16, 0));
    when(performanceCacheRepository.findByPortfolioIdAndPerformanceDates(1L, orderedDates)).thenReturn(List.of());
    when(transactionRepository.findByPortfolioId(1L)).thenReturn(List.of(
        new Transaction(1L, 1L, 10L, "BUY", new BigDecimal("2"), new BigDecimal("100.00"), new BigDecimal("200.00"), LocalDate.of(2026, 7, 25).atTime(10, 0))
    ));
    when(marketPriceDailyRepository.findClosePricesByTradeDate(LocalDate.of(2026, 7, 25), java.util.Set.of(10L)))
        .thenReturn(Map.of());

    List<WeeklyPerformanceResponse> result = service.getWeeklyPerformance(1L);

    assertThat(result).singleElement().satisfies(entry -> {
        assertThat(entry.totalValue()).isEqualByComparingTo("200.00");
        assertThat(entry.dailyProfit()).isEqualByComparingTo("0.00");
        assertThat(entry.returnRate()).isEqualByComparingTo("0");
    });
    }
}