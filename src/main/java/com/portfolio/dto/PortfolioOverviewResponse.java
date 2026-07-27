package com.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO for portfolio overview (page 1).
 */
public record PortfolioOverviewResponse(
        BigDecimal totalValue,
        BigDecimal totalCost,
        BigDecimal totalProfit,
        BigDecimal returnRate,
        BigDecimal cashBalance,
        List<AssetAllocation> allocation
) {
    public record AssetAllocation(
            String assetType,
            String label,
            BigDecimal value,
            BigDecimal percentage
    ) {}
}
