package com.portfolio.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AiAssistantRepository {
    List<HeldStockRow> findHeldStocks(Long portfolioId);
    List<AvailableStockRow> findAvailableStocks(Long portfolioId);
        Optional<BigDecimal> findPortfolioCashBalance(Long portfolioId);
        List<PerformancePointRow> findPerformanceSeries(Long portfolioId, int limit);

    record HeldStockRow(
            Long stockId,
            String symbol,
            String name,
            String assetType,
            BigDecimal quantity,
            BigDecimal averageCost
    ) {}

    record AvailableStockRow(
            Long stockId,
            String symbol,
            String name,
            String assetType
    ) {}

    record PerformancePointRow(
            LocalDate date,
            BigDecimal totalValue,
            BigDecimal cashBalance
    ) {}
}