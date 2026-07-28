package com.portfolio.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

/**
 * Daily close price imported into local storage.
 */
public record MarketPriceDaily(
        Long id,
        Long stockId,
        LocalDate tradeDate,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        BigDecimal adjustedClose,
        long volume,
        String source,
        Instant fetchedAt
) {
}
