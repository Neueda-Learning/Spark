package com.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

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
        BigDecimal changePercent,
        LocalDate priceDate,
        String priceSource,
        boolean stale
) {
    public StockInfoResponse(
            Long id,
            String symbol,
            String name,
            String assetType,
            String sector,
            String exchange,
            BigDecimal currentPrice,
            BigDecimal changePercent
    ) {
        this(id, symbol, name, assetType, sector, exchange, currentPrice, changePercent, null, null, false);
    }
}
