package com.portfolio.service.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.model.MarketPriceDaily;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
public class YahooMarketDataProvider implements MarketDataProvider {

    static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String baseUrl;

    @Autowired
    public YahooMarketDataProvider(
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${market-data.yahoo.base-url:https://query1.finance.yahoo.com}") String baseUrl
    ) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                objectMapper,
                clock,
                baseUrl
        );
    }

    YahooMarketDataProvider(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Clock clock,
            String baseUrl
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @Override
    public List<MarketPriceDaily> fetchDailyPrices(
            Long stockId,
            String symbol,
            LocalDate startDate,
            LocalDate endDate
    ) throws IOException {
        URI uri = buildUri(symbol, startDate, endDate);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 PortfolioManager/1.0")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Yahoo chart request interrupted", ex);
        }

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Yahoo chart request failed with HTTP " + response.statusCode()
                            + extractErrorDescription(response.body())
            );
        }
        return parseResponse(stockId, response.body(), Instant.now(clock));
    }

    URI buildUri(String symbol, LocalDate startDate, LocalDate endDate) {
        long period1 = startDate.atStartOfDay(MARKET_ZONE).toEpochSecond();
        long period2 = endDate.plusDays(1).atStartOfDay(MARKET_ZONE).toEpochSecond();
        String encodedSymbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return URI.create(
                baseUrl + "/v8/finance/chart/" + encodedSymbol
                        + "?period1=" + period1
                        + "&period2=" + period2
                        + "&interval=1d"
                        + "&events=div%2Csplits"
                        + "&includeAdjustedClose=true"
        );
    }

    List<MarketPriceDaily> parseResponse(Long stockId, String responseBody, Instant fetchedAt)
            throws IOException {
        JsonNode chart;
        try {
            chart = objectMapper.readTree(responseBody).path("chart");
        } catch (IOException ex) {
            throw new IOException("Yahoo chart returned invalid JSON", ex);
        }

        JsonNode error = chart.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            String code = error.path("code").asText("unknown");
            String description = error.path("description").asText("unknown error");
            throw new IOException("Yahoo chart error " + code + ": " + description);
        }

        JsonNode results = chart.path("result");
        if (!results.isArray() || results.isEmpty()) {
            return List.of();
        }

        JsonNode result = results.get(0);
        JsonNode timestamps = result.path("timestamp");
        JsonNode quotes = result.path("indicators").path("quote");
        if (!timestamps.isArray() || !quotes.isArray() || quotes.isEmpty()) {
            return List.of();
        }

        JsonNode quote = quotes.get(0);
        JsonNode opens = quote.path("open");
        JsonNode highs = quote.path("high");
        JsonNode lows = quote.path("low");
        JsonNode closes = quote.path("close");
        JsonNode volumes = quote.path("volume");
        JsonNode adjustedCloses = result.path("indicators")
                .path("adjclose")
                .path(0)
                .path("adjclose");

        List<MarketPriceDaily> prices = new ArrayList<>(timestamps.size());
        for (int index = 0; index < timestamps.size(); index++) {
            JsonNode timestamp = timestamps.get(index);
            if (timestamp == null || !timestamp.canConvertToLong()) {
                continue;
            }
            LocalDate tradeDate = Instant.ofEpochSecond(timestamp.asLong())
                    .atZone(MARKET_ZONE)
                    .toLocalDate();
            prices.add(new MarketPriceDaily(
                    null,
                    stockId,
                    tradeDate,
                    decimalAt(opens, index),
                    decimalAt(highs, index),
                    decimalAt(lows, index),
                    decimalAt(closes, index),
                    decimalAt(adjustedCloses, index),
                    longAt(volumes, index),
                    "YAHOO",
                    fetchedAt
            ));
        }
        return prices;
    }

    private BigDecimal decimalAt(JsonNode values, int index) {
        if (!values.isArray() || index >= values.size()) {
            return null;
        }
        JsonNode value = values.get(index);
        return value == null || value.isNull() || !value.isNumber()
                ? null
                : value.decimalValue();
    }

    private long longAt(JsonNode values, int index) {
        if (!values.isArray() || index >= values.size()) {
            return 0L;
        }
        JsonNode value = values.get(index);
        return value == null || value.isNull() || !value.canConvertToLong()
                ? 0L
                : value.asLong();
    }

    private String extractErrorDescription(String responseBody) {
        try {
            JsonNode error = objectMapper.readTree(responseBody).path("chart").path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                return ": " + error.path("description").asText("unknown error");
            }
        } catch (IOException ignored) {
            // The HTTP status remains actionable when Yahoo returns a non-JSON error page.
        }
        return "";
    }
}
