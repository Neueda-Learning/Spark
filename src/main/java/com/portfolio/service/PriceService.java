package com.portfolio.service;

import com.portfolio.dto.PriceHistoryResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Service for retrieving stock prices. In production, this would integrate with
 * yahoofinance-api (see https://github.com/sstrickx/yahoofinance-api).
 * This implementation uses simulated prices based on realistic base prices
 * with daily variation for demonstration purposes.
 */
public interface PriceService {
    /**
     * Get the current price for a stock by its symbol.
     */
    BigDecimal getCurrentPrice(String symbol);

    /**
     * Get the price change percentage for a stock.
     */
    BigDecimal getChangePercent(String symbol);

    /**
     * Get 7-day price history for a stock (for hover popup chart).
     */
    List<PriceHistoryResponse> getSevenDayPriceHistory(String symbol);

    /**
     * Get the closing price for a stock on a specific date.
     * Used for dividend calculation based on ex-dividend date price.
     */
    BigDecimal getPriceOnDate(String symbol, LocalDate date);
}
