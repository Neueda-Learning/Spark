package com.portfolio.service;

import com.portfolio.dto.*;
import com.portfolio.model.Holding;
import com.portfolio.model.Portfolio;
import com.portfolio.model.PortfolioSnapshot;
import com.portfolio.model.Stock;
import com.portfolio.model.Transaction;
import com.portfolio.repository.HoldingRepository;
import com.portfolio.repository.PortfolioRepository;
import com.portfolio.repository.PortfolioSnapshotRepository;
import com.portfolio.repository.StockRepository;
import com.portfolio.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
    private final TransactionRepository transactionRepository;
    private final PriceService priceService;

    public PortfolioServiceImpl(PortfolioRepository portfolioRepository,
                                HoldingRepository holdingRepository,
                                StockRepository stockRepository,
                                PortfolioSnapshotRepository snapshotRepository,
                                TransactionRepository transactionRepository,
                                PriceService priceService) {
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.stockRepository = stockRepository;
        this.snapshotRepository = snapshotRepository;
        this.transactionRepository = transactionRepository;
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
        BigDecimal totalAnnualDividend = BigDecimal.ZERO;
        Map<String, BigDecimal> allocationByType = new LinkedHashMap<>();

        for (Holding holding : holdings) {
            Stock stock = stockRepository.findById(holding.stockId()).orElse(null);
            if (stock == null) continue;

            BigDecimal currentPrice = priceService.getCurrentPrice(stock.symbol());
            BigDecimal marketValue = currentPrice.multiply(holding.quantity()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal cost = holding.averageCost().multiply(holding.quantity()).setScale(2, RoundingMode.HALF_UP);

            holdingsValue = holdingsValue.add(marketValue);
            totalCost = totalCost.add(cost);

            // 计算年化分红：需要考虑除息日是否在持仓期间
            BigDecimal annualDividend = calculateAnnualDividendWithDividendDate(
                    portfolioId, stock, holding.quantity(), currentPrice);
            totalAnnualDividend = totalAnnualDividend.add(annualDividend);

            allocationByType.merge(stock.assetType(), marketValue, BigDecimal::add);
        }

        // 加上已清仓股票的分红（除息日持有但后来卖掉的）
        totalAnnualDividend = totalAnnualDividend.add(
                calculateDividendsForClosedPositions(portfolioId, holdings));

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
                totalAnnualDividend.setScale(2, RoundingMode.HALF_UP),
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

            // 年化分红：需要考虑除息日是否在持仓期间
            BigDecimal annualDividend = calculateAnnualDividendWithDividendDate(
                    portfolioId, stock, holding.quantity(), currentPrice);

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
                    profitPercent,
                    annualDividend
            ));
        }

        return responses;
    }

    @Override
    public List<WeeklyPerformanceResponse> getWeeklyPerformance(Long portfolioId) {
        List<PortfolioSnapshot> snapshots = snapshotRepository.findByPortfolioIdOrderBySnapshotDateDesc(portfolioId, 7);

        if (!snapshots.isEmpty()) {
            // 有快照数据，用快照计算（原有逻辑）
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

        // 没有快照数据，用持仓 + 7天价格历史实时回算
        return computeWeeklyPerformanceFromHoldings(portfolioId);
    }

    /**
     * 计算年化分红，考虑除息日判断。
     * 
     * 规则：
     * 1. 如果股票没有除息日或股息率为0，分红为0
     * 2. 如果有除息日，查询除息日当天的持仓数量
     * 3. 如果除息日当天持有该股票（数量 > 0），分红 = 持仓数量 × 除息日价格 × 股息率
     * 4. 如果除息日当天没有持有（数量为0），分红为0
     */
    private BigDecimal calculateAnnualDividendWithDividendDate(Long portfolioId, Stock stock, 
            BigDecimal currentQuantity, BigDecimal currentPrice) {
        // 没有除息日或股息率为0，不产生分红
        if (stock.dividendDate() == null || stock.dividendYield().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        // 查询除息日当天的持仓数量
        BigDecimal holdingOnDividendDate = getHoldingOnDate(portfolioId, stock.id(), stock.dividendDate());
        
        // 除息日当天没有持有该股票，没有分红
        if (holdingOnDividendDate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        // 获取除息日当天的价格（收盘价）
        BigDecimal priceOnDividendDate = priceService.getPriceOnDate(stock.symbol(), stock.dividendDate());
        
        // 除息日当天持有，计算分红 = 除息日持仓数量 × 除息日价格 × 股息率
        BigDecimal marketValueOnDividendDate = priceOnDividendDate.multiply(holdingOnDividendDate)
                .setScale(2, RoundingMode.HALF_UP);
        return marketValueOnDividendDate.multiply(stock.dividendYield()).setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * 计算某只股票在指定日期当天的持仓数量。
     * 通过遍历该日期之前（含）的所有交易记录，累加买入、减去卖出得到。
     * 如果没有交易记录，则使用当前持仓数量（假设一直持有）。
     */
    private BigDecimal getHoldingOnDate(Long portfolioId, Long stockId, LocalDate date) {
        List<Transaction> transactions = transactionRepository
                .findByPortfolioIdAndStockIdBeforeDate(portfolioId, stockId, date);
        
        // 没有任何交易记录，说明该股票一直是初始状态（没有持仓），返回0
        if (transactions.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        // 遍历交易记录，计算除息日当天的持仓数量
        BigDecimal quantity = BigDecimal.ZERO;
        for (Transaction tx : transactions) {
            if ("BUY".equals(tx.type())) {
                quantity = quantity.add(tx.quantity());
            } else if ("SELL".equals(tx.type())) {
                quantity = quantity.subtract(tx.quantity());
            }
        }
        
        return quantity;
    }

    /**
     * 计算已清仓股票的分红。
     * 遍历所有交易记录，找出曾经持有但当前已清仓的股票，
     * 检查除息日当天是否持有，如果有则计算分红。
     */
    private BigDecimal calculateDividendsForClosedPositions(Long portfolioId, List<Holding> currentHoldings) {
        // 获取当前持仓的 stockId 集合
        Set<Long> currentStockIds = currentHoldings.stream()
                .map(Holding::stockId)
                .collect(Collectors.toSet());
        
        // 获取所有交易记录
        List<Transaction> allTransactions = transactionRepository.findByPortfolioId(portfolioId);
        
        // 提取所有曾经持有的 stockId
        Set<Long> allTradedStockIds = allTransactions.stream()
                .map(Transaction::stockId)
                .collect(Collectors.toSet());
        
        // 找出已清仓的 stockId（曾经持有但当前不在持仓中）
        Set<Long> closedPositionStockIds = new HashSet<>(allTradedStockIds);
        closedPositionStockIds.removeAll(currentStockIds);
        
        if (closedPositionStockIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        // 对每支已清仓的股票，检查除息日是否持有过
        BigDecimal totalDividend = BigDecimal.ZERO;
        for (Long stockId : closedPositionStockIds) {
            Stock stock = stockRepository.findById(stockId).orElse(null);
            if (stock == null) continue;
            
            // 没有除息日或股息率为0，不产生分红
            if (stock.dividendDate() == null || stock.dividendYield().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            
            // 查询除息日当天的持仓数量
            BigDecimal holdingOnDividendDate = getHoldingOnDate(portfolioId, stockId, stock.dividendDate());
            
            // 除息日当天持有该股票，计算分红
            if (holdingOnDividendDate.compareTo(BigDecimal.ZERO) > 0) {
                // 获取除息日当天的价格（收盘价）
                BigDecimal priceOnDividendDate = priceService.getPriceOnDate(stock.symbol(), stock.dividendDate());
                BigDecimal marketValue = priceOnDividendDate.multiply(holdingOnDividendDate)
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal dividend = marketValue.multiply(stock.dividendYield())
                        .setScale(2, RoundingMode.HALF_UP);
                totalDividend = totalDividend.add(dividend);
            }
        }
        
        return totalDividend;
    }

    /**
     * 当 portfolio_snapshot 表为空时，根据当前持仓和模拟价格历史
     * 反推过去 7 天每天的组合总价值，计算每日盈亏和收益率。
     *
     * 计算逻辑：
     *   每天组合价值 = Σ(持仓数量 × 该标的当天收盘价) + 现金余额
     *   每日盈亏 = 当天组合价值 - 前一天组合价值（第一天为0）
     *   收益率 = (当天组合价值 - 第一天组合价值) / 第一天组合价值 × 100
     */
    private List<WeeklyPerformanceResponse> computeWeeklyPerformanceFromHoldings(Long portfolioId) {
        List<Holding> holdings = holdingRepository.findByPortfolioId(portfolioId);

        if (holdings.isEmpty()) {
            return List.of();
        }

        // 获取每支持仓标的的 7 天价格历史
        // Map<symbol, List<PriceHistoryResponse>>
        Map<String, List<PriceHistoryResponse>> priceHistories = new LinkedHashMap<>();
        for (Holding holding : holdings) {
            Stock stock = stockRepository.findById(holding.stockId()).orElse(null);
            if (stock == null) continue;
            priceHistories.put(stock.symbol(), priceService.getSevenDayPriceHistory(stock.symbol()));
        }

        // 获取现金余额
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElseThrow();
        BigDecimal cashBalance = portfolio.cashBalance();

        // 确定 7 天日期（取第一支标的的日期序列）
        List<PriceHistoryResponse> referenceDates = priceHistories.values().iterator().next();
        int days = referenceDates.size();

        // 对每一天，计算组合总价值
        List<WeeklyPerformanceResponse> result = new ArrayList<>();
        BigDecimal firstDayValue = null;

        for (int dayIdx = 0; dayIdx < days; dayIdx++) {
            LocalDate date = referenceDates.get(dayIdx).date();
            BigDecimal dayHoldingsValue = BigDecimal.ZERO;

            // 累加每支持仓当天的市值
            for (Holding holding : holdings) {
                Stock stock = stockRepository.findById(holding.stockId()).orElse(null);
                if (stock == null) continue;

                List<PriceHistoryResponse> history = priceHistories.get(stock.symbol());
                if (history != null && dayIdx < history.size()) {
                    BigDecimal closePrice = history.get(dayIdx).closePrice();
                    dayHoldingsValue = dayHoldingsValue.add(closePrice.multiply(holding.quantity()));
                }
            }

            BigDecimal totalValue = dayHoldingsValue.add(cashBalance).setScale(2, RoundingMode.HALF_UP);

            if (firstDayValue == null) {
                firstDayValue = totalValue;
            }

            // 每日盈亏
            BigDecimal dailyProfit;
            if (dayIdx == 0) {
                dailyProfit = BigDecimal.ZERO;
            } else {
                BigDecimal prevValue = result.get(result.size() - 1).totalValue();
                dailyProfit = totalValue.subtract(prevValue).setScale(2, RoundingMode.HALF_UP);
            }

            // 累计收益率
            BigDecimal returnRate = firstDayValue.compareTo(BigDecimal.ZERO) > 0
                    ? totalValue.subtract(firstDayValue)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(firstDayValue, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            result.add(new WeeklyPerformanceResponse(date, totalValue, dailyProfit, returnRate));
        }

        return result;
    }
}
