package com.portfolio.service;

import com.portfolio.dto.*;
import com.portfolio.model.DividendHistory;
import com.portfolio.model.Holding;
import com.portfolio.model.Portfolio;
import com.portfolio.model.PortfolioPerformanceCache;
import com.portfolio.model.Stock;
import com.portfolio.model.UserDividend;
import com.portfolio.repository.DividendHistoryRepository;
import com.portfolio.repository.HoldingRepository;
import com.portfolio.repository.MarketPriceRepository;
import com.portfolio.repository.PortfolioPerformanceCacheRepository;
import com.portfolio.repository.PortfolioRepository;
import com.portfolio.repository.StockRepository;
import com.portfolio.repository.TransactionRepository;
import com.portfolio.repository.UserDividendRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.portfolio.model.Transaction;

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
    private final MarketPriceRepository marketPriceRepository;
    private final PriceService priceService;
    private final DividendHistoryRepository dividendHistoryRepository;
    private final UserDividendRepository userDividendRepository;
    private final DividendService dividendService;

    public PortfolioServiceImpl(PortfolioRepository portfolioRepository,
                                HoldingRepository holdingRepository,
                                StockRepository stockRepository,
                                PortfolioPerformanceCacheRepository performanceCacheRepository,
                                TransactionRepository transactionRepository,
                                MarketPriceRepository marketPriceRepository,
                                PriceService priceService,
                                DividendHistoryRepository dividendHistoryRepository,
                                UserDividendRepository userDividendRepository,
                                DividendService dividendService) {
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.stockRepository = stockRepository;
        this.performanceCacheRepository = performanceCacheRepository;
        this.transactionRepository = transactionRepository;
        this.marketPriceRepository = marketPriceRepository;
        this.priceService = priceService;
        this.dividendHistoryRepository = dividendHistoryRepository;
        this.userDividendRepository = userDividendRepository;
        this.dividendService = dividendService;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public PortfolioOverviewResponse getPortfolioOverview(Long portfolioId) {
       

        List<Holding> holdings = holdingRepository.findByPortfolioId(portfolioId);
        LocalDate today = LocalDate.now();

        // ======== 1. 处理分红：计算 + 派息日到账 ========
        processDividends(portfolioId, today);

        // 读取 portfolio
         Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found: " + portfolioId));

        // ======== 2. 计算持仓市值 ========
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
        BigDecimal stockProfit = holdingsValue.subtract(totalCost);
        BigDecimal returnRate = totalCost.compareTo(BigDecimal.ZERO) > 0
                ? stockProfit.multiply(BigDecimal.valueOf(100)).divide(totalCost, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // ======== 3. 统计分红 ========
        // 已到账分红：所有 status = 'paid' 的 net_amount 总和
        BigDecimal totalDividendPaid = userDividendRepository
                .sumPaidByPortfolioIdAndDateRange(portfolioId, LocalDate.of(2025, 1, 1), today);

        // 待到账分红：所有 status = 'pending' 的 net_amount 总和
        BigDecimal totalDividendPending = userDividendRepository
                .sumPendingByPortfolioId(portfolioId);

        // ======== 4. 资产配置 ========
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
                totalDividendPaid.setScale(2, RoundingMode.HALF_UP),
                totalDividendPending.setScale(2, RoundingMode.HALF_UP),
                allocation
        );
    }

    /**
     * 处理分红：
     * 1. 遍历所有 dividend_history 记录（ex_date <= today）
     * 2. 对每条记录，计算 ex_date 前一天的持仓数量
     * 3. 如果持仓 > 0，创建 user_dividend 记录（如不存在）
     * 4. 如果 pay_date <= today 且 status = 'pending'，将分红加到现金余额，标记为 paid
     */
    private void processDividends(Long portfolioId, LocalDate today) {
        List<DividendHistory> allDividends = dividendHistoryRepository.findUpToDate(today);

        for (DividendHistory dh : allDividends) {
            // 查找 stock id
            Optional<Stock> stockOpt = stockRepository.findBySymbol(dh.symbol());
            if (stockOpt.isEmpty()) continue;
            Stock stock = stockOpt.get();

            // 检查是否已有该分红记录
            Optional<UserDividend> existing = userDividendRepository
                    .findByPortfolioIdAndSymbolAndExDate(portfolioId, dh.symbol(), dh.exDate());
            if (existing.isPresent()) continue;

            // 计算除息日持仓数量（ex_date 前一天收盘持有）
            BigDecimal sharesOnExDate = getHoldingOnDate(portfolioId, stock.id(), dh.exDate().minusDays(1));
            if (sharesOnExDate.compareTo(BigDecimal.ZERO) <= 0) continue;

            // 创建 user_dividend 记录
            int sharesHeld = sharesOnExDate.setScale(0, RoundingMode.DOWN).intValue();
            BigDecimal grossAmount = dh.dividendPerShare()
                    .multiply(BigDecimal.valueOf(sharesHeld))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxRate = dividendService.getTaxRate(dh.symbol());
            BigDecimal netAmount = grossAmount
                    .multiply(BigDecimal.ONE.subtract(taxRate))
                    .setScale(2, RoundingMode.HALF_UP);

            UserDividend userDividend = new UserDividend(
                    null,               // id (auto-generated)
                    portfolioId,
                    dh.symbol(),
                    dh.exDate(),
                    dh.payDate(),
                    sharesHeld,
                    dh.dividendPerShare(),
                    grossAmount,
                    taxRate,
                    netAmount,
                    "pending",          // status
                    null,               // paidAt
                    null                // createdAt
            );
            try {
                userDividendRepository.save(userDividend);
                log.info("Created dividend record: {} {} shares={}, gross={}, net={}",
                        dh.symbol(), dh.exDate(), sharesHeld, grossAmount, netAmount);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                continue;
            }
        }

        // 处理派息日到账：将 pending 且 pay_date <= today 的分红加到现金余额
        List<UserDividend> pendingToPay = userDividendRepository.findPendingDividends(portfolioId, today);
        if (!pendingToPay.isEmpty()) {
            Portfolio portfolio = portfolioRepository.findById(portfolioId).orElseThrow();
            BigDecimal cashBalance = portfolio.cashBalance();

            for (UserDividend ud : pendingToPay) {
                cashBalance = cashBalance.add(ud.netAmount());
                userDividendRepository.markAsPaid(ud.id());
                log.info("Dividend paid: {} net={} added to cash, new balance={}",
                        ud.symbol(), ud.netAmount(), cashBalance);
            }

            portfolioRepository.updateCashBalance(portfolioId, cashBalance);
        }
    }

    /**
     * 计算某只股票在指定日期（含）之前的持仓数量。
     * 通过遍历该日期之前（含）的所有交易记录，累加买入、减去卖出得到。
     */
    private BigDecimal getHoldingOnDate(Long portfolioId, Long stockId, LocalDate date) {
        List<Transaction> transactions = transactionRepository.findByPortfolioIdAndStockIdBeforeDate(portfolioId, stockId, date);

        if (transactions.isEmpty()) {
            return BigDecimal.ZERO;
        }

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

            // 该持仓的已到账分红：从 user_dividend 表查
            BigDecimal holdingDividend = calculateHoldingDividend(portfolioId, stock.symbol());

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
                    holdingDividend
            ));
        }

        return responses;
    }

    /**
     * 计算某个持仓标的的累计已到账分红。
     */
    private BigDecimal calculateHoldingDividend(Long portfolioId, String symbol) {
        List<UserDividend> dividends = userDividendRepository
                .findByPortfolioIdAndDateRange(portfolioId, LocalDate.of(2025, 1, 1), LocalDate.now());
        return dividends.stream()
                .filter(d -> d.symbol().equals(symbol))
                .map(UserDividend::netAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public List<WeeklyPerformanceResponse> getWeeklyPerformance(Long portfolioId) {
        List<LocalDate> tradeDates = marketPriceRepository.findLatestTradeDates(PERFORMANCE_WINDOW_DAYS);
        if (tradeDates.isEmpty()) {
            return List.of();
        }

        List<LocalDate> orderedDates = new ArrayList<>(tradeDates);
        Collections.reverse(orderedDates);

        List<PortfolioPerformanceCache> cached = performanceCacheRepository
                .findByPortfolioIdAndPerformanceDates(portfolioId, orderedDates);
        LocalDateTime latestMarketRefresh = marketPriceRepository
                .findLatestFetchedAtForTradeDates(orderedDates);

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

    private List<PortfolioPerformanceCache> rebuildPerformanceCache(
            Long portfolioId,
            List<LocalDate> orderedDates
    ) {
        List<Transaction> transactions =
                new ArrayList<>(transactionRepository.findByPortfolioId(portfolioId));
        transactions.sort(Comparator.comparing(Transaction::createdAt));

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
            Map<Long, BigDecimal> closePrices = marketPriceRepository
                    .findClosePricesByTradeDate(tradeDate, positions.keySet());

            for (Map.Entry<Long, PositionState> entry : positions.entrySet()) {
                PositionState state = entry.getValue();
                if (state.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal cost = state.averageCost()
                        .multiply(state.quantity())
                        .setScale(2, RoundingMode.HALF_UP);
                investedCost = investedCost.add(cost);

                BigDecimal closePrice = closePrices.get(entry.getKey());
                if (closePrice == null) {
                    log.warn(
                            "Missing close price for stock {} on {} when rebuilding weekly performance cache",
                            entry.getKey(),
                            tradeDate
                    );
                    continue;
                }

                BigDecimal profit = closePrice.subtract(state.averageCost())
                        .multiply(state.quantity())
                        .setScale(2, RoundingMode.HALF_UP);
                cumulativeProfit = cumulativeProfit.add(profit);
            }

            BigDecimal returnRate = investedCost.compareTo(BigDecimal.ZERO) > 0
                    ? cumulativeProfit.multiply(HUNDRED)
                            .divide(investedCost, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal totalValue = investedCost.add(cumulativeProfit)
                    .setScale(2, RoundingMode.HALF_UP);

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

    private void applyTransaction(Map<Long, PositionState> positions, Transaction transaction) {
        PositionState current = positions.getOrDefault(
                transaction.stockId(),
                new PositionState(BigDecimal.ZERO, BigDecimal.ZERO)
        );

        if ("BUY".equals(transaction.type())) {
            BigDecimal newQuantity = current.quantity().add(transaction.quantity());
            BigDecimal existingCost = current.averageCost().multiply(current.quantity());
            BigDecimal combinedCost =
                    existingCost.add(transaction.unitPrice().multiply(transaction.quantity()));
            BigDecimal newAverageCost = newQuantity.compareTo(BigDecimal.ZERO) > 0
                    ? combinedCost.divide(newQuantity, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            positions.put(
                    transaction.stockId(),
                    new PositionState(newQuantity, newAverageCost)
            );
            return;
        }

        BigDecimal remainingQuantity = current.quantity().subtract(transaction.quantity());
        if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            positions.remove(transaction.stockId());
            return;
        }

        positions.put(
                transaction.stockId(),
                new PositionState(remainingQuantity, current.averageCost())
        );
    }

    private record PositionState(BigDecimal quantity, BigDecimal averageCost) {
    }
}
