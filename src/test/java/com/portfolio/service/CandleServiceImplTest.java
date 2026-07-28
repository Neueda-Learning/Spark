package com.portfolio.service;

import com.portfolio.dto.CandleSeriesResponse;
import com.portfolio.model.MarketPriceDaily;
import com.portfolio.model.Stock;
import com.portfolio.repository.MarketPriceRepository;
import com.portfolio.repository.StockRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CandleServiceImplTest {

    private final StockRepository stockRepository = mock(StockRepository.class);
    private final MarketPriceRepository marketPriceRepository = mock(MarketPriceRepository.class);
    private final CandleServiceImpl service = new CandleServiceImpl(stockRepository, marketPriceRepository);

    @Test
    void returnsDailyCandlesInAscendingOrder() {
        Stock stock = new Stock(1L, "AAPL", "Apple Inc.", "STOCK", "Technology", "NASDAQ", "USD");
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(marketPriceRepository.findRecentByStockId(1L, 2)).thenReturn(List.of(
                price(LocalDate.of(2026, 7, 28), "224", "228", "222", "227", 80),
                price(LocalDate.of(2026, 7, 27), "220", "225", "219", "224", 70)
        ));

        CandleSeriesResponse result = service.getCandles(1L, CandleInterval.DAILY, 2);

        assertThat(result.interval()).isEqualTo("DAILY");
        assertThat(result.candles()).extracting(candle -> candle.date())
                .containsExactly(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 28));
        assertThat(result.candles().getLast().close()).isEqualByComparingTo("227");
    }

    @Test
    void aggregatesDailyPricesIntoWeeklyCandlesInAscendingOrder() {
        Stock stock = new Stock(1L, "AAPL", "Apple Inc.", "STOCK", "Technology", "NASDAQ", "USD");
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(marketPriceRepository.findRecentByStockId(1L, 371)).thenReturn(List.of(
                price(LocalDate.of(2026, 7, 27), "220", "225", "219", "224", 70),
                price(LocalDate.of(2026, 7, 24), "214", "218", "213", "217", 50),
                price(LocalDate.of(2026, 7, 22), "212", "219", "208", "215", 40),
                price(LocalDate.of(2026, 7, 20), "210", "216", "209", "214", 30)
        ));

        CandleSeriesResponse result = service.getCandles(1L, CandleInterval.WEEKLY, 52);

        assertThat(result.asOf()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(result.candles()).hasSize(2);
        assertThat(result.candles().getFirst().date()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(result.candles().getFirst().open()).isEqualByComparingTo("210");
        assertThat(result.candles().getFirst().high()).isEqualByComparingTo("219");
        assertThat(result.candles().getFirst().low()).isEqualByComparingTo("208");
        assertThat(result.candles().getFirst().close()).isEqualByComparingTo("217");
        assertThat(result.candles().getFirst().volume()).isEqualTo(120);
        assertThat(result.candles().getLast().date()).isEqualTo(LocalDate.of(2026, 7, 27));
    }

    @Test
    void aggregatesDailyPricesIntoMonthlyCandles() {
        Stock stock = new Stock(1L, "AAPL", "Apple Inc.", "STOCK", "Technology", "NASDAQ", "USD");
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(marketPriceRepository.findRecentByStockId(1L, 403)).thenReturn(List.of(
                price(LocalDate.of(2026, 7, 28), "220", "230", "218", "225", 70),
                price(LocalDate.of(2026, 7, 1), "210", "226", "208", "222", 50),
                price(LocalDate.of(2026, 6, 30), "200", "210", "195", "205", 40)
        ));

        CandleSeriesResponse result = service.getCandles(1L, CandleInterval.MONTHLY, 12);

        assertThat(result.interval()).isEqualTo("MONTHLY");
        assertThat(result.candles()).hasSize(2);
        assertThat(result.candles().getLast().date()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(result.candles().getLast().open()).isEqualByComparingTo("210");
        assertThat(result.candles().getLast().high()).isEqualByComparingTo("230");
        assertThat(result.candles().getLast().low()).isEqualByComparingTo("208");
        assertThat(result.candles().getLast().close()).isEqualByComparingTo("225");
        assertThat(result.candles().getLast().volume()).isEqualTo(120);
    }

    @Test
    void returnsEmptySeriesForCash() {
        Stock cash = new Stock(20L, "USD", "US Dollar", "CASH", "Cash", "FOREX", "USD");
        when(stockRepository.findById(20L)).thenReturn(Optional.of(cash));

        CandleSeriesResponse result = service.getCandles(20L, CandleInterval.DAILY, 120);

        assertThat(result.candles()).isEmpty();
        assertThat(result.source()).isEqualTo("FIXED");
        assertThat(result.interval()).isEqualTo("DAILY");
    }

    private MarketPriceDaily price(
            LocalDate date,
            String open,
            String high,
            String low,
            String close,
            long volume
    ) {
        return new MarketPriceDaily(
                null,
                1L,
                date,
                new BigDecimal(open),
                new BigDecimal(high),
                new BigDecimal(low),
                new BigDecimal(close),
                new BigDecimal(close),
                volume,
                "YAHOO",
                Instant.parse("2026-07-27T23:00:00Z")
        );
    }
}
