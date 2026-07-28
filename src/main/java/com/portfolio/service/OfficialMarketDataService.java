package com.portfolio.service;

import com.portfolio.model.MarketPriceDaily;
import com.portfolio.model.Stock;
import com.portfolio.repository.MarketPriceRepository;
import com.portfolio.repository.StockRepository;
import com.portfolio.service.marketdata.MarketDataProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OfficialMarketDataService {

    private static final long CACHE_TTL_MILLIS = 30L * 60L * 1000L;
    private static final int MAX_CACHE_ENTRIES = 256;

    private final PriceService priceService;
    private final StockRepository stockRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final MarketDataProvider marketDataProvider;
    private final Clock clock;
    private final String mode;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public OfficialMarketDataService(
            PriceService priceService,
            StockRepository stockRepository,
            MarketPriceRepository marketPriceRepository,
            MarketDataProvider marketDataProvider,
            Clock clock,
            @Value("${ai.market-data.mode:internal}") String mode
    ) {
        this.priceService = priceService;
        this.stockRepository = stockRepository;
        this.marketPriceRepository = marketPriceRepository;
        this.marketDataProvider = marketDataProvider;
        this.clock = clock;
        this.mode = mode == null ? "internal" : mode.trim().toLowerCase();
    }

    public List<Map<String, Object>> buildInsights(Collection<String> symbols) {
        List<Map<String, Object>> insights = new ArrayList<>();
        for (String symbol : symbols) {
            buildInsight(symbol).ifPresent(insights::add);
        }
        return insights;
    }

    public Optional<Map<String, Object>> buildInsight(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }

        String normalizedSymbol = symbol.trim().toUpperCase();
        CacheEntry hit = cache.get(normalizedSymbol);
        long nowMillis = clock.millis();
        if (hit != null && nowMillis - hit.cachedAtMillis < CACHE_TTL_MILLIS) {
            return Optional.of(hit.payload);
        }
        if (hit != null) {
            cache.remove(normalizedSymbol, hit);
        }

        Map<String, Object> payload = switch (mode) {
            case "yahoo", "external" -> buildFromYahoo(normalizedSymbol);
            default -> buildFromInternalFeed(normalizedSymbol);
        };
        cacheInsight(normalizedSymbol, nowMillis, payload);
        return Optional.of(payload);
    }

    private synchronized void cacheInsight(
            String symbol,
            long cachedAtMillis,
            Map<String, Object> payload
    ) {
        long expiryCutoff = cachedAtMillis - CACHE_TTL_MILLIS;
        cache.entrySet().removeIf(entry -> entry.getValue().cachedAtMillis <= expiryCutoff);

        if (cache.size() >= MAX_CACHE_ENTRIES && !cache.containsKey(symbol)) {
            cache.entrySet().stream()
                    .min(Map.Entry.comparingByValue(
                            Comparator.comparingLong(CacheEntry::cachedAtMillis)
                    ))
                    .map(Map.Entry::getKey)
                    .ifPresent(cache::remove);
        }
        cache.put(symbol, new CacheEntry(cachedAtMillis, payload));
    }

    private Map<String, Object> buildFromInternalFeed(String symbol) {
        try {
            Stock stock = stockRepository.findBySymbol(symbol)
                    .orElseThrow(() -> new IllegalArgumentException("Stock not found: " + symbol));
            BigDecimal latestPrice = priceService.getCurrentPrice(symbol);
            BigDecimal dailyChangePercent = priceService.getChangePercent(symbol);
            List<MarketPriceDaily> recent = "CASH".equals(stock.assetType())
                    ? List.of()
                    : marketPriceRepository.findRecentByStockId(stock.id(), 7);

            BigDecimal weekHigh = recent.stream()
                    .map(MarketPriceDaily::highPrice)
                    .filter(value -> value != null)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            BigDecimal weekLow = recent.stream()
                    .map(MarketPriceDaily::lowPrice)
                    .filter(value -> value != null)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            LocalDate asOf = Optional.ofNullable(priceService.getPriceDate(symbol))
                    .orElse(LocalDate.now(clock));

            Map<String, Object> payload = basePayload(
                    symbol,
                    "internal_market_feed",
                    "trusted_internal_feed",
                    asOf
            );
            payload.put("latest_price", scale(latestPrice, 2));
            payload.put("daily_change_percent", scale(dailyChangePercent, 2));
            payload.put("recent_7d_high", scale(weekHigh, 2));
            payload.put("recent_7d_low", scale(weekLow, 2));
            payload.put("price_source", priceService.getPriceSource(symbol));
            payload.put("stale", priceService.isStale(symbol));
            payload.put("notes", "Data comes from the market feed configured in the current system.");
            return payload;
        } catch (RuntimeException exception) {
            return unavailableInsight(symbol, "internal_market_feed", "internal_market_data_unavailable");
        }
    }

    private Map<String, Object> buildFromYahoo(String symbol) {
        LocalDate today = LocalDate.now(clock);
        try {
            List<MarketPriceDaily> history = sortValid(marketDataProvider.fetchDailyPrices(
                    null,
                    symbol,
                    today.minusYears(10),
                    today
            ));
            if (history.isEmpty()) {
                return unavailableInsight(symbol, "yahoo_finance", "yahoo_not_found_or_no_quote");
            }

            MarketPriceDaily latest = history.getLast();
            MarketPriceDaily previous = history.size() > 1 ? history.get(history.size() - 2) : null;
            List<MarketPriceDaily> oneYear = from(history, today.minusYears(1));
            List<MarketPriceDaily> threeYears = from(history, today.minusYears(3));
            List<MarketPriceDaily> fiveYears = from(history, today.minusYears(5));
            List<MarketPriceDaily> tenYears = from(history, today.minusYears(10));
            List<MarketPriceDaily> recent = history.subList(Math.max(0, history.size() - 7), history.size());

            Map<String, Object> payload = basePayload(
                    symbol,
                    "yahoo_finance",
                    "official_realtime",
                    latest.tradeDate()
            );
            payload.put("latest_price", scale(latest.closePrice(), 2));
            payload.put("daily_change_percent", scale(changePercent(previous, latest), 2));
            payload.put("year_high", scale(maxHigh(oneYear), 2));
            payload.put("year_low", scale(minLow(oneYear), 2));
            payload.put("annualized_return_5y", scale(annualizedReturn(fiveYears).orElse(null), 2));
            payload.put("annualized_return_10y", scale(annualizedReturn(tenYears).orElse(null), 2));
            payload.put("annualized_volatility_3y", scale(annualizedVolatility(threeYears).orElse(null), 2));
            payload.put("history_5y_start", firstDate(fiveYears));
            payload.put("history_5y_end", lastDate(fiveYears));
            payload.put("history_10y_start", firstDate(tenYears));
            payload.put("history_10y_end", lastDate(tenYears));
            payload.put("recent_7d_high", scale(maxHigh(recent), 2));
            payload.put("recent_7d_low", scale(minLow(recent), 2));
            return payload;
        } catch (Exception exception) {
            return unavailableInsight(symbol, "yahoo_finance", "yahoo_request_failed");
        }
    }

    private List<MarketPriceDaily> sortValid(List<MarketPriceDaily> history) {
        if (history == null) {
            return List.of();
        }
        return history.stream()
                .filter(price -> price != null
                        && price.tradeDate() != null
                        && price.closePrice() != null)
                .sorted(Comparator.comparing(MarketPriceDaily::tradeDate))
                .toList();
    }

    private List<MarketPriceDaily> from(List<MarketPriceDaily> history, LocalDate startDate) {
        return history.stream()
                .filter(price -> !price.tradeDate().isBefore(startDate))
                .toList();
    }

    private Optional<BigDecimal> annualizedReturn(List<MarketPriceDaily> history) {
        if (history.size() < 2) {
            return Optional.empty();
        }
        MarketPriceDaily first = history.getFirst();
        MarketPriceDaily last = history.getLast();
        if (first.closePrice().compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        long days = ChronoUnit.DAYS.between(first.tradeDate(), last.tradeDate());
        if (days < 90) {
            return Optional.empty();
        }
        double years = days / 365.2425;
        double ratio = last.closePrice().doubleValue() / first.closePrice().doubleValue();
        return Optional.of(BigDecimal.valueOf((Math.pow(ratio, 1.0 / years) - 1.0) * 100));
    }

    private Optional<BigDecimal> annualizedVolatility(List<MarketPriceDaily> history) {
        if (history.size() < 6) {
            return Optional.empty();
        }

        List<Double> dailyReturns = new ArrayList<>();
        for (int index = 1; index < history.size(); index++) {
            BigDecimal previous = history.get(index - 1).closePrice();
            BigDecimal current = history.get(index).closePrice();
            if (previous.compareTo(BigDecimal.ZERO) > 0) {
                dailyReturns.add(current.doubleValue() / previous.doubleValue() - 1.0);
            }
        }
        if (dailyReturns.size() < 5) {
            return Optional.empty();
        }

        double mean = dailyReturns.stream().mapToDouble(value -> value).average().orElse(0.0);
        double variance = dailyReturns.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .average()
                .orElse(0.0);
        return Optional.of(BigDecimal.valueOf(Math.sqrt(variance) * Math.sqrt(252) * 100));
    }

    private BigDecimal changePercent(MarketPriceDaily previous, MarketPriceDaily latest) {
        if (previous == null || previous.closePrice().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return latest.closePrice()
                .subtract(previous.closePrice())
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.closePrice(), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal maxHigh(List<MarketPriceDaily> history) {
        return history.stream()
                .map(MarketPriceDaily::highPrice)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private BigDecimal minLow(List<MarketPriceDaily> history) {
        return history.stream()
                .map(MarketPriceDaily::lowPrice)
                .filter(value -> value != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private String firstDate(List<MarketPriceDaily> history) {
        return history.isEmpty() ? null : history.getFirst().tradeDate().toString();
    }

    private String lastDate(List<MarketPriceDaily> history) {
        return history.isEmpty() ? null : history.getLast().tradeDate().toString();
    }

    private Map<String, Object> basePayload(
            String symbol,
            String source,
            String dataStatus,
            LocalDate asOf
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("symbol", symbol);
        payload.put("source", source);
        payload.put("data_status", dataStatus);
        payload.put("as_of", asOf == null ? null : asOf.toString());
        payload.put("latest_price", null);
        payload.put("daily_change_percent", null);
        payload.put("year_high", null);
        payload.put("year_low", null);
        payload.put("annualized_return_5y", null);
        payload.put("annualized_return_10y", null);
        payload.put("annualized_volatility_3y", null);
        payload.put("history_5y_start", null);
        payload.put("history_5y_end", null);
        payload.put("history_10y_start", null);
        payload.put("history_10y_end", null);
        payload.put("recent_7d_high", null);
        payload.put("recent_7d_low", null);
        return payload;
    }

    private Map<String, Object> unavailableInsight(String symbol, String source, String reason) {
        String dataStatus = "yahoo_finance".equals(source)
                ? "official_unavailable"
                : "internal_unavailable";
        Map<String, Object> payload = basePayload(
                symbol,
                source,
                dataStatus,
                LocalDate.now(clock)
        );
        payload.put("unavailable_reason", reason);
        return payload;
    }

    private BigDecimal scale(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
    }

    private record CacheEntry(long cachedAtMillis, Map<String, Object> payload) {
    }
}
