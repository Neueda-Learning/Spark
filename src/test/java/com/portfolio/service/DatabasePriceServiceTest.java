package com.portfolio.service;

import com.portfolio.model.MarketPriceDaily;
import com.portfolio.model.Stock;
import com.portfolio.repository.MarketPriceRepository;
import com.portfolio.repository.StockRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabasePriceServiceTest {

    private final StockRepository stockRepository = mock(StockRepository.class);
    private final MarketPriceRepository marketPriceRepository = mock(MarketPriceRepository.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-27T14:00:00Z"),
            ZoneId.of("America/New_York")
    );
    private final DatabasePriceService service = new DatabasePriceService(
            stockRepository,
            marketPriceRepository,
            clock,
            7
    );

    @Test
    void returnsLatestCloseAndDailyChange() {
        Stock stock = stock(1L, "AAPL", "STOCK");
        MarketPriceDaily latest = price(1L, LocalDate.of(2026, 7, 24), "110");
        MarketPriceDaily previous = price(1L, LocalDate.of(2026, 7, 23), "100");
        when(stockRepository.findBySymbol("AAPL")).thenReturn(Optional.of(stock));
        when(marketPriceRepository.findLatestByStockId(1L)).thenReturn(Optional.of(latest));
        when(marketPriceRepository.findRecentByStockId(1L, 2)).thenReturn(List.of(latest, previous));

        assertThat(service.getCurrentPrice("AAPL")).isEqualByComparingTo("110");
        assertThat(service.getChangePercent("AAPL")).isEqualByComparingTo("10.00");
        assertThat(service.getPriceDate("AAPL")).isEqualTo(LocalDate.of(2026, 7, 24));
        assertThat(service.getPriceSource("AAPL")).isEqualTo("YAHOO");
        assertThat(service.isStale("AAPL")).isFalse();
    }

    @Test
    void cashAlwaysUsesFixedPrice() {
        when(stockRepository.findBySymbol("USD"))
                .thenReturn(Optional.of(stock(20L, "USD", "CASH")));

        assertThat(service.getCurrentPrice("USD")).isEqualByComparingTo("1.00");
        assertThat(service.getChangePercent("USD")).isEqualByComparingTo("0.00");
        assertThat(service.getPriceSource("USD")).isEqualTo("FIXED");
        assertThat(service.isStale("USD")).isFalse();
    }

    @Test
    void missingMarketPriceIsExplicit() {
        when(stockRepository.findBySymbol("AAPL"))
                .thenReturn(Optional.of(stock(1L, "AAPL", "STOCK")));
        when(marketPriceRepository.findLatestByStockId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentPrice("AAPL"))
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessageContaining("AAPL");
    }

    private Stock stock(Long id, String symbol, String assetType) {
        return new Stock(id, symbol, symbol, assetType, "Sector", "Exchange", "USD");
    }

    private MarketPriceDaily price(Long stockId, LocalDate date, String close) {
        BigDecimal value = new BigDecimal(close);
        return new MarketPriceDaily(
                null,
                stockId,
                date,
                value,
                value,
                value,
                value,
                value,
                100,
                "YAHOO",
                Instant.parse("2026-07-25T00:00:00Z")
        );
    }
}
