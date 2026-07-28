package com.portfolio.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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
