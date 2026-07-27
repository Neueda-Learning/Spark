package com.portfolio.service;

import com.portfolio.dto.*;
import com.portfolio.model.Holding;
import com.portfolio.model.Portfolio;
import com.portfolio.model.PortfolioSnapshot;
import com.portfolio.model.Stock;
import com.portfolio.repository.HoldingRepository;
import com.portfolio.repository.PortfolioRepository;
import com.portfolio.repository.PortfolioSnapshotRepository;
import com.portfolio.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PortfolioServiceImpl implements PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioServiceImpl.class);

    private static final Map<String, String> ASSET_TYPE_LABELS = Map.of(
            "STOCK", "股票",
            "BOND", "债券",
            "CASH", "现金"
    );

    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final StockRepository stockRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PriceService priceService;

    public PortfolioServiceImpl(PortfolioRepository portfolioRepository,
                                HoldingRepository holdingRepository,
                                StockRepository stockRepository,
                                PortfolioSnapshotRepository snapshotRepository,
                                PriceService priceService) {
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.stockRepository = stockRepository;
        this.snapshotRepository = snapshotRepository;
        this.priceService = priceService;
    }

    @Override
    public PortfolioOverviewResponse getPortfolioOverview(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found: " + portfolioId));

        List<Holding> holdings = holdingRepository.findByPortfolioId(portfolioId);

        // Calculate total holdings value and group by asset type
        BigDecimal holdingsValue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        Map<String, BigDecimal> allocationByType = new LinkedHashMap<>();

        for (Holding holding : holdings) {
            Stock stock = stockRepository.findById(holding.stockId()).orElse(null);
            if (stock == null) continue;

            BigDecimal currentPrice = priceService.getCurrentPrice(stock.symbol());
            BigDecimal marketValue = currentPrice.multiply(holding.quantity()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal cost = holding.averageCost().multiply(holding.quantity()).setScale(2, RoundingMode.HALF_UP);

            holdingsValue = holdingsValue.add(marketValue);
            totalCost = totalCost.add(cost);

            allocationByType.merge(stock.assetType(), marketValue, BigDecimal::add);
        }

        BigDecimal totalValue = holdingsValue.add(portfolio.cashBalance());
        // Total money invested in stocks = totalCost
        // Current value of stocks = holdingsValue
        // Stock profit = holdingsValue - totalCost
        // Total portfolio value = holdingsValue + cashBalance
        // So returnRate = (totalValue - initialCash) / initialCash
        // But we don't have initialCash stored. Let's use:
        // totalCost = what was spent buying stocks
        // cashBalance = what's left
        // initialCash = totalCost + cashBalance (if no sells) — but sells add cash back
        // Simpler approach: totalProfit = holdingsValue - totalCost (profit from stocks only)
        // returnRate = totalProfit / totalCost (return on invested money)

        // Actually the cleanest way:
        // totalProfit = totalValue - totalCost - cashBalance = holdingsValue - totalCost
        BigDecimal stockProfit = holdingsValue.subtract(totalCost);
        BigDecimal returnRate = totalCost.compareTo(BigDecimal.ZERO) > 0
                ? stockProfit.multiply(BigDecimal.valueOf(100)).divide(totalCost, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Build allocation
        List<PortfolioOverviewResponse.AssetAllocation> allocation = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : allocationByType.entrySet()) {
            BigDecimal percentage = totalValue.compareTo(BigDecimal.ZERO) > 0
                    ? entry.getValue().multiply(BigDecimal.valueOf(100)).divide(totalValue, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            allocation.add(new PortfolioOverviewResponse.AssetAllocation(
                    entry.getKey(),
                    ASSET_TYPE_LABELS.getOrDefault(entry.getKey(), entry.getKey()),
                    entry.getValue().setScale(2, RoundingMode.HALF_UP),
                    percentage
            ));
        }
        // Add cash to allocation
        BigDecimal cashPercentage = totalValue.compareTo(BigDecimal.ZERO) > 0
                ? portfolio.cashBalance().multiply(BigDecimal.valueOf(100)).divide(totalValue, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        allocation.add(new PortfolioOverviewResponse.AssetAllocation(
                "CASH", "现金",
                portfolio.cashBalance().setScale(2, RoundingMode.HALF_UP),
                cashPercentage
        ));

        return new PortfolioOverviewResponse(
                totalValue.setScale(2, RoundingMode.HALF_UP),
                totalCost.setScale(2, RoundingMode.HALF_UP),
                stockProfit.setScale(2, RoundingMode.HALF_UP),
                returnRate,
                portfolio.cashBalance().setScale(2, RoundingMode.HALF_UP),
                allocation
        );
    }

    @Override
    public List<HoldingResponse> getHoldings(Long portfolioId) {
        List<Holding> holdings = holdingRepository.findByPortfolioId(portfolioId);
        List<HoldingResponse> responses = new ArrayList<>();

        for (Holding holding : holdings) {
            Stock stock = stockRepository.findById(holding.stockId()).orElse(null);
            if (stock == null) continue;

            BigDecimal currentPrice = priceService.getCurrentPrice(stock.symbol());
            BigDecimal marketValue = currentPrice.multiply(holding.quantity()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal cost = holding.averageCost().multiply(holding.quantity()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal profit = marketValue.subtract(cost).setScale(2, RoundingMode.HALF_UP);
            BigDecimal profitPercent = cost.compareTo(BigDecimal.ZERO) > 0
                    ? profit.multiply(BigDecimal.valueOf(100)).divide(cost, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            responses.add(new HoldingResponse(
                    stock.id(),
                    stock.symbol(),
                    stock.name(),
                    stock.assetType(),
                    holding.quantity(),
                    holding.averageCost(),
                    currentPrice,
                    marketValue,
                    profit,
                    profitPercent
            ));
        }

        return responses;
    }

    @Override
    public List<WeeklyPerformanceResponse> getWeeklyPerformance(Long portfolioId) {
        List<PortfolioSnapshot> snapshots = snapshotRepository.findByPortfolioIdOrderBySnapshotDateDesc(portfolioId, 7);

        if (snapshots.isEmpty()) {
            // No snapshots yet — generate based on current state
            // Return empty list; frontend should handle this gracefully
            return List.of();
        }

        // Reverse to chronological order
        List<PortfolioSnapshot> ordered = new ArrayList<>(snapshots);
        Collections.reverse(ordered);

        BigDecimal initialCost = ordered.getFirst().investedCost();
        List<WeeklyPerformanceResponse> result = new ArrayList<>();

        for (int i = 0; i < ordered.size(); i++) {
            PortfolioSnapshot snap = ordered.get(i);
            BigDecimal dailyProfit;
            if (i == 0) {
                dailyProfit = BigDecimal.ZERO;
            } else {
                dailyProfit = snap.totalValue().subtract(ordered.get(i - 1).totalValue());
            }
            BigDecimal returnRate = initialCost.compareTo(BigDecimal.ZERO) > 0
                    ? snap.totalValue().subtract(initialCost)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(initialCost, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            result.add(new WeeklyPerformanceResponse(
                    snap.snapshotDate(),
                    snap.totalValue(),
                    dailyProfit.setScale(2, RoundingMode.HALF_UP),
                    returnRate
            ));
        }

        return result;
    }
}
