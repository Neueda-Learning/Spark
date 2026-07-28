package com.portfolio.service;

import com.portfolio.dto.*;
import com.portfolio.model.Holding;
import com.portfolio.model.Portfolio;
import com.portfolio.model.PortfolioPerformanceCache;
import com.portfolio.model.Stock;
import com.portfolio.repository.HoldingRepository;
import com.portfolio.repository.MarketPriceDailyRepository;
import com.portfolio.repository.PortfolioRepository;
import com.portfolio.repository.PortfolioPerformanceCacheRepository;
import com.portfolio.repository.StockRepository;
import com.portfolio.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PortfolioServiceImpl implements PortfolioService {

        private static final int PERFORMANCE_WINDOW_DAYS = 7;
        private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final Logger log = LoggerFactory.getLogger(PortfolioServiceImpl.class);

    private static final Map<String, String> ASSET_TYPE_LABELS = Map.of(
            "STOCK", "股票",
            "BOND", "债券",
            "CASH", "现金"
    );

    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final StockRepository stockRepository;
        private final PortfolioPerformanceCacheRepository performanceCacheRepository;
        private final TransactionRepository transactionRepository;
        private final MarketPriceDailyRepository marketPriceDailyRepository;
    private final PriceService priceService;

    public PortfolioServiceImpl(PortfolioRepository portfolioRepository,
                                HoldingRepository holdingRepository,
                                StockRepository stockRepository,
                                                                PortfolioPerformanceCacheRepository performanceCacheRepository,
                                                                TransactionRepository transactionRepository,
                                                                MarketPriceDailyRepository marketPriceDailyRepository,
                                PriceService priceService) {
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.stockRepository = stockRepository;
                this.performanceCacheRepository = performanceCacheRepository;
                this.transactionRepository = transactionRepository;
                this.marketPriceDailyRepository = marketPriceDailyRepository;
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
    @org.springframework.transaction.annotation.Transactional
    public List<WeeklyPerformanceResponse> getWeeklyPerformance(Long portfolioId) {
                List<LocalDate> tradeDates = marketPriceDailyRepository.findLatestTradeDates(PERFORMANCE_WINDOW_DAYS);
                if (tradeDates.isEmpty()) {
            return List.of();
        }

                List<LocalDate> orderedDates = new ArrayList<>(tradeDates);
                Collections.reverse(orderedDates);

                List<PortfolioPerformanceCache> cached = performanceCacheRepository
                                .findByPortfolioIdAndPerformanceDates(portfolioId, orderedDates);
                LocalDateTime latestMarketRefresh = marketPriceDailyRepository.findLatestFetchedAtForTradeDates(orderedDates);

                boolean cacheComplete = cached.size() == orderedDates.size();
                boolean cacheFresh = latestMarketRefresh == null
                                || cached.stream().allMatch(entry -> !entry.refreshedAt().isBefore(latestMarketRefresh));

                List<PortfolioPerformanceCache> effectiveCache = cached;
                if (!cacheComplete || !cacheFresh) {
                        effectiveCache = rebuildPerformanceCache(portfolioId, orderedDates);
                        performanceCacheRepository.replaceForPortfolio(portfolioId, effectiveCache);
        }

                return effectiveCache.stream()
                                .map(entry -> new WeeklyPerformanceResponse(
                                                entry.performanceDate(),
                                                entry.totalValue(),
                                                entry.cumulativeProfit(),
                                                entry.returnRate()
                                ))
                                .toList();
    }

        private List<PortfolioPerformanceCache> rebuildPerformanceCache(Long portfolioId, List<LocalDate> orderedDates) {
                List<com.portfolio.model.Transaction> transactions = new ArrayList<>(transactionRepository.findByPortfolioId(portfolioId));
                transactions.sort(Comparator.comparing(com.portfolio.model.Transaction::createdAt));

                Map<Long, PositionState> positions = new HashMap<>();
                int transactionIndex = 0;
                LocalDateTime refreshedAt = LocalDateTime.now();
                List<PortfolioPerformanceCache> entries = new ArrayList<>();

                for (LocalDate tradeDate : orderedDates) {
                        while (transactionIndex < transactions.size()
                                        && !transactions.get(transactionIndex).createdAt().toLocalDate().isAfter(tradeDate)) {
                                applyTransaction(positions, transactions.get(transactionIndex));
                                transactionIndex++;
                        }

                        BigDecimal investedCost = BigDecimal.ZERO;
                        BigDecimal cumulativeProfit = BigDecimal.ZERO;
                        Map<Long, BigDecimal> closePrices = marketPriceDailyRepository.findClosePricesByTradeDate(tradeDate, positions.keySet());

                        for (Map.Entry<Long, PositionState> entry : positions.entrySet()) {
                                PositionState state = entry.getValue();
                                if (state.quantity.compareTo(BigDecimal.ZERO) <= 0) {
                                        continue;
                                }

                                BigDecimal cost = state.averageCost.multiply(state.quantity).setScale(2, RoundingMode.HALF_UP);
                                investedCost = investedCost.add(cost);

                                BigDecimal closePrice = closePrices.get(entry.getKey());
                                if (closePrice == null) {
                                        log.warn("Missing close price for stock {} on {} when rebuilding weekly performance cache", entry.getKey(), tradeDate);
                                        continue;
                                }

                                BigDecimal profit = closePrice.subtract(state.averageCost)
                                                .multiply(state.quantity)
                                                .setScale(2, RoundingMode.HALF_UP);
                                cumulativeProfit = cumulativeProfit.add(profit);
                        }

                        BigDecimal returnRate = investedCost.compareTo(BigDecimal.ZERO) > 0
                                        ? cumulativeProfit.multiply(HUNDRED).divide(investedCost, 2, RoundingMode.HALF_UP)
                                        : BigDecimal.ZERO;
                        BigDecimal totalValue = investedCost.add(cumulativeProfit).setScale(2, RoundingMode.HALF_UP);

                        entries.add(new PortfolioPerformanceCache(
                                        null,
                                        portfolioId,
                                        tradeDate,
                                        investedCost.setScale(2, RoundingMode.HALF_UP),
                                        cumulativeProfit.setScale(2, RoundingMode.HALF_UP),
                                        returnRate,
                                        totalValue,
                                        refreshedAt
                        ));
                }

                return entries;
        }

        private void applyTransaction(Map<Long, PositionState> positions, com.portfolio.model.Transaction transaction) {
                PositionState current = positions.getOrDefault(transaction.stockId(), new PositionState(BigDecimal.ZERO, BigDecimal.ZERO));

                if ("BUY".equals(transaction.type())) {
                        BigDecimal newQuantity = current.quantity.add(transaction.quantity());
                        BigDecimal existingCost = current.averageCost.multiply(current.quantity);
                        BigDecimal combinedCost = existingCost.add(transaction.unitPrice().multiply(transaction.quantity()));
                        BigDecimal newAverageCost = newQuantity.compareTo(BigDecimal.ZERO) > 0
                                        ? combinedCost.divide(newQuantity, 4, RoundingMode.HALF_UP)
                                        : BigDecimal.ZERO;
                        positions.put(transaction.stockId(), new PositionState(newQuantity, newAverageCost));
                        return;
                }

                BigDecimal remainingQuantity = current.quantity.subtract(transaction.quantity());
                if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                        positions.remove(transaction.stockId());
                        return;
                }

                positions.put(transaction.stockId(), new PositionState(remainingQuantity, current.averageCost));
        }

        private record PositionState(BigDecimal quantity, BigDecimal averageCost) {
        }
}
