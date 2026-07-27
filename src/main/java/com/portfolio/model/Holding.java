package com.portfolio.model;

import java.math.BigDecimal;

/**
 * Represents a position in a portfolio — how many shares of a stock are held and at what average cost.
 */
public record Holding(
        Long id,
        Long portfolioId,
        Long stockId,
        BigDecimal quantity,
        BigDecimal averageCost
) {}
