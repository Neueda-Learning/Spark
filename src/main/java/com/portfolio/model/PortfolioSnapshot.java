package com.portfolio.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Daily snapshot of portfolio value for performance charting.
 */
public record PortfolioSnapshot(
        Long id,
        Long portfolioId,
        LocalDate snapshotDate,
        BigDecimal totalValue,
        BigDecimal cashBalance,
        BigDecimal investedCost
) {}
