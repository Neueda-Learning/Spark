package com.portfolio.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cached portfolio performance derived from transactions and daily close prices.
 */
public record PortfolioPerformanceCache(
        Long id,
        Long portfolioId,
        LocalDate performanceDate,
        BigDecimal investedCost,
        BigDecimal cumulativeProfit,
        BigDecimal returnRate,
        BigDecimal totalValue,
        LocalDateTime refreshedAt
) {
}
