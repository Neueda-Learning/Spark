package com.portfolio.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface PriceService {
    /**
     * Get the current price for a stock by its symbol.
     */
    BigDecimal getCurrentPrice(String symbol);

    /**
     * Get the price change percentage for a stock.
     */
    BigDecimal getChangePercent(String symbol);

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
