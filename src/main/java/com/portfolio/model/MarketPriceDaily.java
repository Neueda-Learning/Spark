package com.portfolio.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Daily close price imported into local storage.
 */
public record MarketPriceDaily(
        Long stockId,
        LocalDate tradeDate,
        BigDecimal closePrice,
        LocalDateTime fetchedAt
) {}
