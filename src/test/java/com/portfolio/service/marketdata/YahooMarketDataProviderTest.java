package com.portfolio.service.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.model.MarketPriceDaily;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class YahooMarketDataProviderTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-07-25T00:00:00Z");

    private final YahooMarketDataProvider provider = new YahooMarketDataProvider(
            mock(HttpClient.class),
            new ObjectMapper(),
            Clock.fixed(FETCHED_AT, ZoneId.of("America/New_York")),
            "https://query1.finance.yahoo.com/"
    );

    @Test
    void springUsesTheProductionConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(
                    "objectMapper",
                    ObjectMapper.class,
                    (Supplier<ObjectMapper>) ObjectMapper::new
            );
            context.registerBean(Clock.class, () -> Clock.system(ZoneId.of("America/New_York")));
            context.register(YahooMarketDataProvider.class);
            context.refresh();

            assertThat(context.getBean(YahooMarketDataProvider.class)).isNotNull();
        }
    }

    @Test
    void parsesYahooChartResponseIntoDailyPrices() throws Exception {
        String response = """
                {
                  "chart": {
                    "result": [{
                      "timestamp": [1784899800, 1785159000],
                      "indicators": {
                        "quote": [{
                          "open": [210.10, 216.00],
                          "high": [218.20, 220.00],
                          "low": [208.40, 214.50],
                          "close": [216.75, 219.25],
                          "volume": [305000000, null]
                        }],
                        "adjclose": [{
                          "adjclose": [216.70, 219.20]
                        }]
                      }
                    }],
                    "error": null
                  }
                }
                """;

        List<MarketPriceDaily> converted = provider.parseResponse(1L, response, FETCHED_AT);

        assertThat(converted).hasSize(2);
        MarketPriceDaily first = converted.getFirst();
        assertThat(first.stockId()).isEqualTo(1L);
        assertThat(first.tradeDate()).isEqualTo(LocalDate.of(2026, 7, 24));
        assertThat(first.openPrice()).isEqualByComparingTo("210.10");
        assertThat(first.highPrice()).isEqualByComparingTo("218.20");
        assertThat(first.lowPrice()).isEqualByComparingTo("208.40");
        assertThat(first.closePrice()).isEqualByComparingTo("216.75");
        assertThat(first.adjustedClose()).isEqualByComparingTo("216.70");
        assertThat(first.volume()).isEqualTo(305_000_000L);
        assertThat(first.source()).isEqualTo("YAHOO");
        assertThat(first.fetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(converted.getLast().volume()).isZero();
    }

    @Test
    void buildsExclusiveEndDateChartUri() {
        String uri = provider.buildUri(
                "AAPL",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 24)
        ).toString();

        assertThat(uri)
                .startsWith("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?")
                .contains("interval=1d")
                .contains("includeAdjustedClose=true");
    }

    @Test
    void exposesYahooErrorDescription() {
        String response = """
                {
                  "chart": {
                    "result": null,
                    "error": {
                      "code": "Not Found",
                      "description": "No data found, symbol may be delisted"
                    }
                  }
                }
                """;

        assertThatThrownBy(() -> provider.parseResponse(1L, response, FETCHED_AT))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("No data found");
    }
}
