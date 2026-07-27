package com.portfolio.service;

import com.portfolio.dto.PriceHistoryResponse;
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
