package com.portfolio.service;

import com.portfolio.dto.WeeklyPerformanceResponse;
import com.portfolio.model.PortfolioPerformanceCache;
import com.portfolio.model.Transaction;
import com.portfolio.repository.DividendHistoryRepository;
import com.portfolio.repository.HoldingRepository;
import com.portfolio.repository.MarketPriceRepository;
import com.portfolio.repository.PortfolioPerformanceCacheRepository;
import com.portfolio.repository.PortfolioRepository;
import com.portfolio.repository.StockRepository;
import com.portfolio.repository.TransactionRepository;
import com.portfolio.repository.UserDividendRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private MarketPriceRepository marketPriceRepository;

    @Mock
    private PriceService priceService;

    @Mock
    private DividendHistoryRepository dividendHistoryRepository;

    @Mock
    private UserDividendRepository userDividendRepository;

    @Mock
    private DividendService dividendService;

    @InjectMocks
    private PortfolioServiceImpl service;

    @Test
    void returnsEmptyWeeklyPerformanceWhenThereAreNoTradeDates() {
        when(marketPriceRepository.findLatestTradeDates(7)).thenReturn(List.of());

        assertThat(service.getWeeklyPerformance(1L)).isEmpty();

        verify(performanceCacheRepository, never())
                .findByPortfolioIdAndPerformanceDates(eq(1L), anyList());
        verify(transactionRepository, never()).findByPortfolioId(1L);
    }

    @Test
    void returnsFreshWeeklyPerformanceCacheWithoutRebuilding() {
        List<LocalDate> orderedDates = List.of(
                LocalDate.of(2026, 7, 25),
                LocalDate.of(2026, 7, 26)
        );
        when(marketPriceRepository.findLatestTradeDates(7))
                .thenReturn(orderedDates.reversed());
        when(marketPriceRepository.findLatestFetchedAtForTradeDates(orderedDates))
                .thenReturn(LocalDate.of(2026, 7, 26).atTime(16, 0));
        when(performanceCacheRepository.findByPortfolioIdAndPerformanceDates(1L, orderedDates))
                .thenReturn(List.of(
                        cache(1L, orderedDates.get(0), "1000.00", "100.00", "10.00", "1100.00", 16),
                        cache(2L, orderedDates.get(1), "1000.00", "50.00", "5.00", "1050.00", 16)
                ));

        List<WeeklyPerformanceResponse> result = service.getWeeklyPerformance(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).dailyProfit()).isEqualByComparingTo("100.00");
        assertThat(result.get(1).returnRate()).isEqualByComparingTo("5.00");
        verify(transactionRepository, never()).findByPortfolioId(1L);
        verify(performanceCacheRepository, never())
                .replaceForPortfolio(eq(1L), anyList());
    }

    @Test
    void rebuildsWeeklyPerformanceFromBuyAndSellTransactions() {
        List<LocalDate> orderedDates = List.of(
                LocalDate.of(2026, 7, 25),
                LocalDate.of(2026, 7, 26),
                LocalDate.of(2026, 7, 27)
        );
        when(marketPriceRepository.findLatestTradeDates(7))
                .thenReturn(orderedDates.reversed());
        when(marketPriceRepository.findLatestFetchedAtForTradeDates(orderedDates))
                .thenReturn(LocalDate.of(2026, 7, 27).atTime(16, 0));
        when(performanceCacheRepository.findByPortfolioIdAndPerformanceDates(1L, orderedDates))
                .thenReturn(List.of());
        when(transactionRepository.findByPortfolioId(1L)).thenReturn(List.of(
                transaction(1L, "BUY", "10", "100.00", orderedDates.get(0)),
                transaction(2L, "BUY", "10", "120.00", orderedDates.get(1)),
                transaction(3L, "SELL", "5", "130.00", orderedDates.get(2))
        ));
        when(marketPriceRepository.findClosePricesByTradeDate(orderedDates.get(0), Set.of(10L)))
                .thenReturn(Map.of(10L, new BigDecimal("110.00")));
        when(marketPriceRepository.findClosePricesByTradeDate(orderedDates.get(1), Set.of(10L)))
                .thenReturn(Map.of(10L, new BigDecimal("130.00")));
        when(marketPriceRepository.findClosePricesByTradeDate(orderedDates.get(2), Set.of(10L)))
                .thenReturn(Map.of(10L, new BigDecimal("90.00")));

        List<WeeklyPerformanceResponse> result = service.getWeeklyPerformance(1L);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).dailyProfit()).isEqualByComparingTo("100.00");
        assertThat(result.get(0).returnRate()).isEqualByComparingTo("10.00");
        assertThat(result.get(1).dailyProfit()).isEqualByComparingTo("400.00");
        assertThat(result.get(1).returnRate()).isEqualByComparingTo("18.18");
        assertThat(result.get(2).dailyProfit()).isEqualByComparingTo("-300.00");
        assertThat(result.get(2).returnRate()).isEqualByComparingTo("-18.18");
        verify(performanceCacheRepository)
                .replaceForPortfolio(eq(1L), anyList());
    }

    @Test
    void keepsInvestedCostWhenAClosingPriceIsMissing() {
        LocalDate tradeDate = LocalDate.of(2026, 7, 25);
        List<LocalDate> orderedDates = List.of(tradeDate);
        when(marketPriceRepository.findLatestTradeDates(7)).thenReturn(orderedDates);
        when(marketPriceRepository.findLatestFetchedAtForTradeDates(orderedDates))
                .thenReturn(tradeDate.atTime(16, 0));
        when(performanceCacheRepository.findByPortfolioIdAndPerformanceDates(1L, orderedDates))
                .thenReturn(List.of());
        when(transactionRepository.findByPortfolioId(1L)).thenReturn(List.of(
                transaction(1L, "BUY", "2", "100.00", tradeDate)
        ));
        when(marketPriceRepository.findClosePricesByTradeDate(tradeDate, Set.of(10L)))
                .thenReturn(Map.of());

        WeeklyPerformanceResponse result = service.getWeeklyPerformance(1L).getFirst();

        assertThat(result.totalValue()).isEqualByComparingTo("200.00");
        assertThat(result.dailyProfit()).isEqualByComparingTo("0.00");
        assertThat(result.returnRate()).isEqualByComparingTo("0");
    }

    private static PortfolioPerformanceCache cache(
            Long id,
            LocalDate date,
            String investedCost,
            String cumulativeProfit,
            String returnRate,
            String totalValue,
            int refreshHour
    ) {
        return new PortfolioPerformanceCache(
                id,
                1L,
                date,
                new BigDecimal(investedCost),
                new BigDecimal(cumulativeProfit),
                new BigDecimal(returnRate),
                new BigDecimal(totalValue),
                LocalDate.of(2026, 7, 26).atTime(refreshHour, 0)
        );
    }

    private static Transaction transaction(
            Long id,
            String type,
            String quantity,
            String unitPrice,
            LocalDate date
    ) {
        BigDecimal quantityValue = new BigDecimal(quantity);
        BigDecimal unitPriceValue = new BigDecimal(unitPrice);
        return new Transaction(
                id,
                1L,
                10L,
                type,
                quantityValue,
                unitPriceValue,
                quantityValue.multiply(unitPriceValue),
                date.atTime(10, 0)
        );
    }
}
