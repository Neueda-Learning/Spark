package com.portfolio.dto;

import java.math.BigDecimal;

/**
 * Response DTO for a holding in the portfolio (page 2 — top section).
 */
public record HoldingResponse(
        Long stockId,
        String symbol,
        String name,
        String assetType,
        BigDecimal quantity,
        BigDecimal averageCost,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal profit,
        BigDecimal profitPercent
) {}
