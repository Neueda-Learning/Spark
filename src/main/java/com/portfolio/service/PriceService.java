package com.portfolio.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
    default LocalDate getPriceDate(String symbol) {
        return null;
    }

    default String getPriceSource(String symbol) {
        return null;
    }

    default boolean isStale(String symbol) {
        return false;
    }
}
