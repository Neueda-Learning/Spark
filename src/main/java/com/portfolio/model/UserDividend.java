package com.portfolio.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a user's dividend record for a specific dividend event.
 * Maps to the user_dividend table.
 */
public record UserDividend(
        Long id,
        Long portfolioId,
        String symbol,
        LocalDate exDate,
        LocalDate payDate,
        Integer sharesHeld,
        BigDecimal dividendPerShare,
        BigDecimal grossAmount,
        BigDecimal taxRate,
        BigDecimal netAmount,
        String status,          // pending / paid
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {}
