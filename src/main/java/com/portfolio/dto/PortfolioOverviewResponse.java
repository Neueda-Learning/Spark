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
        BigDecimal totalDividendPaid,       // 已到账分红（已加到现金余额）
        BigDecimal totalDividendPending,    // 待到账分红（除息日已过，派息日未到）
        List<AssetAllocation> allocation
) {
    public record AssetAllocation(
            String assetType,
            String label,
            BigDecimal value,
            BigDecimal percentage
    ) {}
}
