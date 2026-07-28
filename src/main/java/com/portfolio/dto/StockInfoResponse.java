package com.portfolio.dto;

import java.math.BigDecimal;

/**
 * Response DTO for a stock item shown on the market list (page 2).
 */
public record StockInfoResponse(
        Long id,
        String symbol,
        String name,
        String assetType,
        String sector,
        String exchange,
        BigDecimal currentPrice,
        BigDecimal changePercent
) {}
