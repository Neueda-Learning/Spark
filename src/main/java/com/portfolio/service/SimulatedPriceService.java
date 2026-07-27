package com.portfolio.service;

import com.portfolio.dto.PriceHistoryResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Simulated price service that generates realistic stock prices.
 * In production, replace this with yahoofinance-api integration:
 *
 * <pre>
 *   Stock stock = YahooFinance.get("AAPL");
 *   BigDecimal price = stock.getQuote().getPrice();
 *   List<HistoricalQuote> history = stock.getHistory();
 * </pre>
 *
 * @see <a href="https://github.com/sstrickx/yahoofinance-api">yahoofinance-api</a>
 */
@Service
public class SimulatedPriceService implements PriceService {

    private static final Map<String, BigDecimal> BASE_PRICES = new LinkedHashMap<>();
    private static final Random RANDOM = new Random(42); // fixed seed for consistent demo data

    static {
        // Stocks (14)
        BASE_PRICES.put("AAPL",  new BigDecimal("195.50"));
        BASE_PRICES.put("MSFT",  new BigDecimal("420.30"));
        BASE_PRICES.put("GOOGL", new BigDecimal("175.80"));
        BASE_PRICES.put("AMZN",  new BigDecimal("185.60"));
        BASE_PRICES.put("NVDA",  new BigDecimal("130.20"));
        BASE_PRICES.put("TSLA",  new BigDecimal("245.80"));
        BASE_PRICES.put("META",  new BigDecimal("505.75"));
        BASE_PRICES.put("JPM",   new BigDecimal("198.40"));
        BASE_PRICES.put("JNJ",   new BigDecimal("155.20"));
        BASE_PRICES.put("V",     new BigDecimal("280.60"));
        BASE_PRICES.put("PG",    new BigDecimal("165.30"));
        BASE_PRICES.put("XOM",   new BigDecimal("108.90"));
        BASE_PRICES.put("UNH",   new BigDecimal("510.40"));
        BASE_PRICES.put("MA",    new BigDecimal("460.20"));
        // Bonds (4)
        BASE_PRICES.put("AGG",   new BigDecimal("99.50"));
        BASE_PRICES.put("BND",   new BigDecimal("72.30"));
        BASE_PRICES.put("TLT",   new BigDecimal("92.80"));
        BASE_PRICES.put("LQD",   new BigDecimal("108.60"));
        // Cash (2)
        BASE_PRICES.put("USD",      new BigDecimal("1.00"));
        BASE_PRICES.put("USDMONEY", new BigDecimal("1.00"));
    }

    @Override
    public BigDecimal getCurrentPrice(String symbol) {
        BigDecimal base = BASE_PRICES.getOrDefault(symbol, new BigDecimal("100.00"));
        // Cash items don't fluctuate
        if ("USD".equals(symbol) || "USDMONEY".equals(symbol)) {
            return base;
        }
        // Generate a deterministic "today" variation based on symbol hash
        double variation = 1.0 + (seededRandom(symbol, 0) * 0.04 - 0.02); // ±2%
        return base.multiply(BigDecimal.valueOf(variation)).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal getChangePercent(String symbol) {
        if ("USD".equals(symbol) || "USDMONEY".equals(symbol)) {
            return BigDecimal.ZERO;
        }
        // Deterministic change percent based on symbol
        double change = seededRandom(symbol, 1) * 6 - 3; // -3% to +3%
        return BigDecimal.valueOf(change).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public List<PriceHistoryResponse> getSevenDayPriceHistory(String symbol) {
        BigDecimal base = BASE_PRICES.getOrDefault(symbol, new BigDecimal("100.00"));
        boolean isCash = "USD".equals(symbol) || "USDMONEY".equals(symbol);
        List<PriceHistoryResponse> history = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            double dayVariation;
            if (isCash) {
                dayVariation = 1.0;
            } else {
                // Deterministic variation per day
                dayVariation = 1.0 + (seededRandom(symbol, i + 10) * 0.06 - 0.03); // ±3%
            }
            BigDecimal close = base.multiply(BigDecimal.valueOf(dayVariation)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal open = base.multiply(BigDecimal.valueOf(1.0 + (seededRandom(symbol, i + 20) * 0.04 - 0.02)))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal high = close.max(open).multiply(BigDecimal.valueOf(1.0 + seededRandom(symbol, i + 30) * 0.01))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal low = close.min(open).multiply(BigDecimal.valueOf(1.0 - seededRandom(symbol, i + 40) * 0.01))
                    .setScale(2, RoundingMode.HALF_UP);
            long volume = isCash ? 0L : (long) (5_000_000 + seededRandom(symbol, i + 50) * 45_000_000);

            history.add(new PriceHistoryResponse(date, open, close, high, low, volume));
        }
        return history;
    }

    /**
     * Generates a deterministic double between 0.0 and 1.0 for a given symbol and seed offset.
     */
    private double seededRandom(String symbol, int offset) {
        Random r = new Random(symbol.hashCode() * 31L + offset);
        return r.nextDouble();
    }
}
