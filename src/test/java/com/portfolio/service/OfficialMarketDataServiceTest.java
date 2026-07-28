package com.portfolio.service;

import com.portfolio.model.MarketPriceDaily;
import com.portfolio.model.Stock;
import com.portfolio.repository.MarketPriceRepository;
import com.portfolio.repository.StockRepository;
import com.portfolio.service.marketdata.MarketDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfficialMarketDataServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-28T12:00:00Z"),
            ZoneOffset.UTC
    );
    private static final Stock AAPL = new Stock(
            1L,
            "AAPL",
            "Apple Inc.",
            "STOCK",
            "Technology",
            "NASDAQ",
            "USD"
    );

    @Mock
    private PriceService priceService;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private MarketPriceRepository marketPriceRepository;

    @Mock
    private MarketDataProvider marketDataProvider;

    private OfficialMarketDataService service;

    @BeforeEach
    void setUp() {
        service = serviceWithMode("internal");
    }

    @Test
    void buildsInternalInsightFromDatabaseMarketPrices() {
        when(stockRepository.findBySymbol("AAPL")).thenReturn(Optional.of(AAPL));
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("215.25"));
        when(priceService.getChangePercent("AAPL")).thenReturn(new BigDecimal("1.75"));
        when(priceService.getPriceDate("AAPL")).thenReturn(LocalDate.of(2026, 7, 27));
        when(priceService.getPriceSource("AAPL")).thenReturn("YAHOO");
        when(marketPriceRepository.findRecentByStockId(1L, 7)).thenReturn(List.of(
                price(LocalDate.of(2026, 7, 27), "212.00", "216.00", "211.00", "215.25"),
                price(LocalDate.of(2026, 7, 25), "207.00", "213.00", "205.00", "211.55")
        ));

        Map<String, Object> insight = service.buildInsight(" aapl ").orElseThrow();

        assertThat(insight)
                .containsEntry("symbol", "AAPL")
                .containsEntry("data_status", "trusted_internal_feed")
                .containsEntry("as_of", "2026-07-27")
                .containsEntry("latest_price", new BigDecimal("215.25"))
                .containsEntry("daily_change_percent", new BigDecimal("1.75"))
                .containsEntry("recent_7d_high", new BigDecimal("216.00"))
                .containsEntry("recent_7d_low", new BigDecimal("205.00"));
        verifyNoInteractions(marketDataProvider);
    }

    @Test
    void buildsYahooInsightThroughExistingMarketDataProvider() throws Exception {
        service = serviceWithMode("yahoo");
        when(marketDataProvider.fetchDailyPrices(
                isNull(),
                eq("AAPL"),
                eq(LocalDate.of(2016, 7, 28)),
                eq(LocalDate.of(2026, 7, 28))
        )).thenReturn(List.of(
                price(LocalDate.of(2016, 7, 28), "25.00", "26.00", "24.00", "25.00"),
                price(LocalDate.of(2024, 1, 2), "180.00", "182.00", "178.00", "181.00"),
                price(LocalDate.of(2025, 6, 2), "195.00", "198.00", "193.00", "197.00"),
                price(LocalDate.of(2026, 7, 23), "205.00", "207.00", "203.00", "206.00"),
                price(LocalDate.of(2026, 7, 24), "206.00", "210.00", "205.00", "209.00"),
                price(LocalDate.of(2026, 7, 25), "209.00", "212.00", "208.00", "211.00"),
                price(LocalDate.of(2026, 7, 27), "211.00", "216.00", "210.00", "215.00")
        ));

        Map<String, Object> insight = service.buildInsight("AAPL").orElseThrow();

        assertThat(insight)
                .containsEntry("source", "yahoo_finance")
                .containsEntry("data_status", "official_realtime")
                .containsEntry("as_of", "2026-07-27")
                .containsEntry("latest_price", new BigDecimal("215.00"))
                .containsEntry("daily_change_percent", new BigDecimal("1.90"))
                .containsEntry("year_high", new BigDecimal("216.00"))
                .containsEntry("year_low", new BigDecimal("203.00"));
        assertThat(insight.get("annualized_return_10y")).isNotNull();
        assertThat(insight.get("annualized_volatility_3y")).isNotNull();
        verifyNoInteractions(priceService, stockRepository, marketPriceRepository);
    }

    @Test
    void degradesGracefullyWhenInternalMarketDataIsUnavailable() {
        when(stockRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());

        Map<String, Object> insight = service.buildInsight("UNKNOWN").orElseThrow();

        assertThat(insight)
                .containsEntry("source", "internal_market_feed")
                .containsEntry("data_status", "internal_unavailable")
                .containsEntry("unavailable_reason", "internal_market_data_unavailable");
    }

    @Test
    void evictsOldestEntryWhenCacheReachesItsLimit() {
        AtomicLong millis = new AtomicLong();
        Clock tickingClock = mock(Clock.class);
        when(tickingClock.millis()).thenAnswer(invocation -> millis.incrementAndGet());
        when(tickingClock.instant()).thenReturn(CLOCK.instant());
        when(tickingClock.getZone()).thenReturn(CLOCK.getZone());
        service = new OfficialMarketDataService(
                priceService,
                stockRepository,
                marketPriceRepository,
                marketDataProvider,
                tickingClock,
                "internal"
        );
        when(stockRepository.findBySymbol(anyString())).thenAnswer(invocation -> {
            String symbol = invocation.getArgument(0);
            return Optional.of(new Stock(
                    1L,
                    symbol,
                    symbol,
                    "STOCK",
                    "Technology",
                    "NASDAQ",
                    "USD"
            ));
        });
        when(priceService.getCurrentPrice(anyString())).thenReturn(BigDecimal.TEN);
        when(priceService.getChangePercent(anyString())).thenReturn(BigDecimal.ZERO);
        when(priceService.getPriceDate(anyString())).thenReturn(LocalDate.of(2026, 7, 28));
        when(priceService.getPriceSource(anyString())).thenReturn("TEST");
        when(marketPriceRepository.findRecentByStockId(1L, 7)).thenReturn(List.of());

        for (int index = 0; index <= 256; index++) {
            service.buildInsight("S" + index);
        }
        service.buildInsight("S0");

        verify(stockRepository, times(2)).findBySymbol("S0");
    }

    private OfficialMarketDataService serviceWithMode(String mode) {
        return new OfficialMarketDataService(
                priceService,
                stockRepository,
                marketPriceRepository,
                marketDataProvider,
                CLOCK,
                mode
        );
    }

    private static MarketPriceDaily price(
            LocalDate date,
            String open,
            String high,
            String low,
            String close
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
                100L,
                "YAHOO",
                CLOCK.instant()
        );
    }
}
