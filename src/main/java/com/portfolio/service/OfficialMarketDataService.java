package com.portfolio.service;

import com.portfolio.dto.PriceHistoryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
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

    private final PriceService priceService;
    private final String mode;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public OfficialMarketDataService(PriceService priceService,
                                     @Value("${ai.market-data.mode:internal}") String mode) {
        this.priceService = priceService;
        this.mode = mode == null ? "internal" : mode.trim().toLowerCase();
    }

    public List<Map<String, Object>> buildInsights(Collection<String> symbols) {
        List<Map<String, Object>> insights = new ArrayList<>();
        for (String symbol : symbols) {
            Optional<Map<String, Object>> insight = buildInsight(symbol);
            insight.ifPresent(insights::add);
        }
        return insights;
    }

    public Optional<Map<String, Object>> buildInsight(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }

        String normalizedSymbol = symbol.trim().toUpperCase();
        CacheEntry hit = cache.get(normalizedSymbol);
        long nowMillis = System.currentTimeMillis();
        if (hit != null && nowMillis - hit.cachedAtMillis < CACHE_TTL_MILLIS) {
            return Optional.of(hit.payload);
        }

        Map<String, Object> payload = switch (mode) {
            case "yahoo", "external" -> buildFromYahoo(normalizedSymbol);
            default -> buildFromInternalFeed(normalizedSymbol);
        };

        cache.put(normalizedSymbol, new CacheEntry(nowMillis, payload));
        return Optional.of(payload);
    }

    private Map<String, Object> buildFromInternalFeed(String symbol) {
        BigDecimal latestPrice = priceService.getCurrentPrice(symbol);
        BigDecimal dailyChangePercent = priceService.getChangePercent(symbol);

        List<PriceHistoryResponse> history = priceService.getSevenDayPriceHistory(symbol);
        BigDecimal weekHigh = history.stream()
            .map(PriceHistoryResponse::highPrice)
                .filter(v -> v != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
        BigDecimal weekLow = history.stream()
            .map(PriceHistoryResponse::lowPrice)
                .filter(v -> v != null)
                .min(Comparator.naturalOrder())
                .orElse(null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("symbol", symbol);
        payload.put("source", "internal_market_feed");
        payload.put("data_status", "trusted_internal_feed");
        payload.put("as_of", LocalDate.now().toString());
        payload.put("latest_price", scale(latestPrice, 2));
        payload.put("daily_change_percent", scale(dailyChangePercent, 2));
        payload.put("year_high", null);
        payload.put("year_low", null);
        payload.put("annualized_return_5y", null);
        payload.put("annualized_return_10y", null);
        payload.put("annualized_volatility_3y", null);
        payload.put("history_5y_start", null);
        payload.put("history_5y_end", null);
        payload.put("history_10y_start", null);
        payload.put("history_10y_end", null);
        payload.put("recent_7d_high", scale(weekHigh, 2));
        payload.put("recent_7d_low", scale(weekLow, 2));
        payload.put("notes", "Data comes from internal market feed configured in the current system.");
        return payload;
    }

    private Map<String, Object> buildFromYahoo(String symbol) {
        try {
            Stock stock = YahooFinance.get(symbol, true);
            if (stock == null || stock.getQuote() == null || stock.getQuote().getPrice() == null) {
                return unavailableInsight(symbol, "yahoo_not_found_or_no_quote");
            }

            BigDecimal latestPrice = stock.getQuote().getPrice();
            BigDecimal changePercent = stock.getQuote().getChangeInPercent();
            BigDecimal yearHigh = stock.getQuote().getYearHigh();
            BigDecimal yearLow = stock.getQuote().getYearLow();

            HistorySlice fiveYearSlice = loadHistorySlice(symbol, 5);
            HistorySlice tenYearSlice = loadHistorySlice(symbol, 10);
            HistorySlice threeYearSlice = loadHistorySlice(symbol, 3);

            BigDecimal return5y = annualizedReturn(fiveYearSlice.history, 5).orElse(null);
            BigDecimal return10y = annualizedReturn(tenYearSlice.history, 10).orElse(null);
            BigDecimal volatility3y = annualizedVolatility(threeYearSlice.history).orElse(null);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("symbol", symbol);
            payload.put("source", "yahoo_finance");
            payload.put("data_status", "official_realtime");
            payload.put("as_of", LocalDate.now().toString());
            payload.put("latest_price", scale(latestPrice, 2));
            payload.put("daily_change_percent", scale(changePercent, 2));
            payload.put("year_high", scale(yearHigh, 2));
            payload.put("year_low", scale(yearLow, 2));
            payload.put("annualized_return_5y", scale(return5y, 2));
            payload.put("annualized_return_10y", scale(return10y, 2));
            payload.put("annualized_volatility_3y", scale(volatility3y, 2));
            payload.put("history_5y_start", fiveYearSlice.startDate == null ? null : fiveYearSlice.startDate.toString());
            payload.put("history_5y_end", fiveYearSlice.endDate == null ? null : fiveYearSlice.endDate.toString());
            payload.put("history_10y_start", tenYearSlice.startDate == null ? null : tenYearSlice.startDate.toString());
            payload.put("history_10y_end", tenYearSlice.endDate == null ? null : tenYearSlice.endDate.toString());
            return payload;
        } catch (Exception e) {
            return unavailableInsight(symbol, "yahoo_request_failed");
        }
    }

    private Optional<BigDecimal> annualizedReturn(List<HistoricalQuote> history, int years) {
        List<HistoricalQuote> sorted = sortValid(history);
        if (sorted.size() < 2) {
            return Optional.empty();
        }

        BigDecimal start = sorted.getFirst().getClose();
        BigDecimal end = sorted.getLast().getClose();
        if (start == null || end == null || start.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        double ratio = end.doubleValue() / start.doubleValue();
        double annual = Math.pow(ratio, 1.0 / years) - 1.0;
        return Optional.of(BigDecimal.valueOf(annual * 100));
    }

    private Optional<BigDecimal> annualizedVolatility(List<HistoricalQuote> history) {
        List<HistoricalQuote> sorted = sortValid(history);
        if (sorted.size() < 6) {
            return Optional.empty();
        }

        List<Double> monthlyReturns = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            BigDecimal prev = sorted.get(i - 1).getClose();
            BigDecimal curr = sorted.get(i).getClose();
            if (prev.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            monthlyReturns.add(curr.doubleValue() / prev.doubleValue() - 1.0);
        }

        if (monthlyReturns.size() < 4) {
            return Optional.empty();
        }

        double mean = monthlyReturns.stream().mapToDouble(v -> v).average().orElse(0.0);
        double variance = monthlyReturns.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        double monthlyVol = Math.sqrt(variance);
        double annualVol = monthlyVol * Math.sqrt(12) * 100;
        return Optional.of(BigDecimal.valueOf(annualVol));
    }

    private HistorySlice loadHistorySlice(String symbol, int years) {
        try {
            Calendar from = Calendar.getInstance();
            from.add(Calendar.YEAR, -years);
            Calendar to = Calendar.getInstance();
            List<HistoricalQuote> history = YahooFinance.get(symbol).getHistory(from, to, Interval.MONTHLY);
            List<HistoricalQuote> sorted = sortValid(history == null ? List.of() : history);
            if (sorted.isEmpty()) {
                return new HistorySlice(List.of(), null, null);
            }

            LocalDate startDate = toLocalDate(sorted.getFirst());
            LocalDate endDate = toLocalDate(sorted.getLast());
            return new HistorySlice(sorted, startDate, endDate);
        } catch (Exception e) {
            return new HistorySlice(List.of(), null, null);
        }
    }

    private List<HistoricalQuote> sortValid(List<HistoricalQuote> history) {
        return history.stream()
                .filter(h -> h.getDate() != null && h.getClose() != null)
                .sorted(Comparator.comparing(h -> h.getDate().getTimeInMillis()))
                .toList();
    }

    private LocalDate toLocalDate(HistoricalQuote quote) {
        if (quote == null || quote.getDate() == null) {
            return null;
        }
        Calendar cal = quote.getDate();
        return LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }

    private Map<String, Object> unavailableInsight(String symbol, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("symbol", symbol);
        payload.put("source", "yahoo_finance");
        payload.put("data_status", "official_unavailable");
        payload.put("unavailable_reason", reason);
        payload.put("as_of", LocalDate.now().toString());
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
        return payload;
    }

    private BigDecimal scale(BigDecimal value, int scale) {
        if (value == null) {
            return null;
        }
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    private record CacheEntry(long cachedAtMillis, Map<String, Object> payload) {
    }

    private record HistorySlice(List<HistoricalQuote> history, LocalDate startDate, LocalDate endDate) {
    }
}
