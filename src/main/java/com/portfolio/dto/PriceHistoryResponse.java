package com.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response DTO for a single day's price data (used in hover popup chart).
 */
public record PriceHistoryResponse(
        LocalDate date,
        BigDecimal openPrice,
        BigDecimal closePrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        long volume
) {}
