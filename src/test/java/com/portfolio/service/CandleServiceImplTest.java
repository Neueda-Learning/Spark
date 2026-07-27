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
    void aggregatesDailyPricesIntoWeeklyCandlesInAscendingOrder() {
        Stock stock = new Stock(1L, "AAPL", "Apple Inc.", "STOCK", "Technology", "NASDAQ", "USD");
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(marketPriceRepository.findRecentByStockId(1L, 371)).thenReturn(List.of(
                price(LocalDate.of(2026, 7, 27), "220", "225", "219", "224", 70),
                price(LocalDate.of(2026, 7, 24), "214", "218", "213", "217", 50),
                price(LocalDate.of(2026, 7, 22), "212", "219", "208", "215", 40),
                price(LocalDate.of(2026, 7, 20), "210", "216", "209", "214", 30)
        ));

        CandleSeriesResponse result = service.getWeeklyCandles(1L, 52);

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
    void returnsEmptySeriesForCash() {
        Stock cash = new Stock(20L, "USD", "US Dollar", "CASH", "Cash", "FOREX", "USD");
        when(stockRepository.findById(20L)).thenReturn(Optional.of(cash));

        CandleSeriesResponse result = service.getWeeklyCandles(20L, 52);

        assertThat(result.candles()).isEmpty();
        assertThat(result.source()).isEqualTo("FIXED");
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
