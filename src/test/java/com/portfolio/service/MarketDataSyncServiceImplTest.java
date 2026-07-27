package com.portfolio.service;

import com.portfolio.model.MarketPriceDaily;
import com.portfolio.model.Stock;
import com.portfolio.repository.MarketPriceRepository;
import com.portfolio.repository.StockRepository;
import com.portfolio.service.marketdata.MarketDataProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MarketDataSyncServiceImplTest {

    private final StockRepository stockRepository = mock(StockRepository.class);
    private final MarketPriceRepository marketPriceRepository = mock(MarketPriceRepository.class);
    private final MarketDataProvider provider = mock(MarketDataProvider.class);

    @Test
    void filtersInvalidPricesAndSkipsCash() throws Exception {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-27T23:00:00Z"),
                ZoneId.of("Asia/Shanghai")
        );
        MarketDataSyncServiceImpl service = service(clock);
        Stock aapl = stock(1L, "AAPL", "STOCK");
        Stock cash = stock(20L, "USD", "CASH");
        when(stockRepository.findAll()).thenReturn(List.of(aapl, cash));
        when(marketPriceRepository.findLastTradeDate(1L)).thenReturn(Optional.empty());
        when(provider.fetchDailyPrices(eq(1L), eq("AAPL"), any(), eq(LocalDate.of(2026, 7, 27))))
                .thenReturn(List.of(
                        price(LocalDate.of(2026, 7, 24), "100", "105", "99", "104", 100),
                        price(LocalDate.of(2026, 7, 28), "100", "105", "99", "104", 100),
                        price(LocalDate.of(2026, 7, 24), "100", "99", "98", "104", 100)
                ));

        MarketDataSyncService.SyncSummary summary = service.syncAll();

        assertThat(summary).isEqualTo(new MarketDataSyncService.SyncSummary(1, 1, 2, 0));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarketPriceDaily>> prices = ArgumentCaptor.forClass(List.class);
        verify(marketPriceRepository).upsertAll(prices.capture());
        assertThat(prices.getValue()).extracting(MarketPriceDaily::tradeDate)
                .containsExactly(LocalDate.of(2026, 7, 24));
        verify(provider, never()).fetchDailyPrices(eq(20L), any(), any(), any());
    }

    @Test
    void usesPreviousDateBeforeCloseCutoffInNewYork() {
        Clock beforeCutoff = Clock.fixed(
                Instant.parse("2026-07-27T22:29:59Z"),
                ZoneId.of("Asia/Shanghai")
        );
        Clock afterCutoff = Clock.fixed(
                Instant.parse("2026-07-27T22:30:00Z"),
                ZoneId.of("Asia/Shanghai")
        );

        assertThat(service(beforeCutoff).lastAllowedTradeDate())
                .isEqualTo(LocalDate.of(2026, 7, 26));
        assertThat(service(afterCutoff).lastAllowedTradeDate())
                .isEqualTo(LocalDate.of(2026, 7, 27));
    }

    private MarketDataSyncServiceImpl service(Clock clock) {
        return new MarketDataSyncServiceImpl(
                stockRepository,
                marketPriceRepository,
                provider,
                clock,
                "America/New_York",
                "18:30",
                18,
                10
        );
    }

    private Stock stock(Long id, String symbol, String assetType) {
        return new Stock(id, symbol, symbol, assetType, "Sector", "Exchange", "USD");
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
