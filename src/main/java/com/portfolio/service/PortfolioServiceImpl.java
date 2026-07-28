package com.portfolio.service;

import com.portfolio.dto.*;
import com.portfolio.model.*;
import com.portfolio.repository.*;
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
    private final DividendHistoryRepository dividendHistoryRepository;
    private final UserDividendRepository userDividendRepository;
    private final DividendService dividendService;

    public PortfolioServiceImpl(PortfolioRepository portfolioRepository,
                                HoldingRepository holdingRepository,
                                StockRepository stockRepository,
                                PortfolioSnapshotRepository snapshotRepository,
                                TransactionRepository transactionRepository,
                                PriceService priceService,
                                DividendHistoryRepository dividendHistoryRepository,
                                UserDividendRepository userDividendRepository,
                                DividendService dividendService) {
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.stockRepository = stockRepository;
        this.snapshotRepository = snapshotRepository;
        this.transactionRepository = transactionRepository;
        this.priceService = priceService;
        this.dividendHistoryRepository = dividendHistoryRepository;
        this.userDividendRepository = userDividendRepository;
        this.dividendService = dividendService;
    }

    @Override
    public PortfolioOverviewResponse getPortfolioOverview(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found: " + portfolioId));

        List<Holding> holdings = holdingRepository.findByPortfolioId(portfolioId);
        LocalDate today = LocalDate.now();

        // ======== 1. 处理分红：计算 + 派息日到账 ========
        processDividends(portfolioId, today);

        // 重新读取 portfolio（可能已被 processDividends 更新现金余额）
        portfolio = portfolioRepository.findById(portfolioId).orElseThrow();

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
            userDividendRepository.save(userDividend);
            log.info("Created dividend record: {} {} shares={}, gross={}, net={}",
                    dh.symbol(), dh.exDate(), sharesHeld, grossAmount, netAmount);
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
        List<Transaction> transactions = transactionRepository
                .findByPortfolioIdAndStockIdBeforeDate(portfolioId, stockId, date);

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
    public List<WeeklyPerformanceResponse> getWeeklyPerformance(Long portfolioId) {
        List<PortfolioSnapshot> snapshots = snapshotRepository.findByPortfolioIdOrderBySnapshotDateDesc(portfolioId, 7);

        if (!snapshots.isEmpty()) {
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

        return computeWeeklyPerformanceFromHoldings(portfolioId);
    }

    /**
     * 当 portfolio_snapshot 表为空时，根据当前持仓和模拟价格历史
     * 反推过去 7 天每天的组合总价值，计算每日盈亏和收益率。
     */
    private List<WeeklyPerformanceResponse> computeWeeklyPerformanceFromHoldings(Long portfolioId) {
        List<Holding> holdings = holdingRepository.findByPortfolioId(portfolioId);

        if (holdings.isEmpty()) {
            return List.of();
        }

        Map<String, List<PriceHistoryResponse>> priceHistories = new LinkedHashMap<>();
        for (Holding holding : holdings) {
            Stock stock = stockRepository.findById(holding.stockId()).orElse(null);
            if (stock == null) continue;
            priceHistories.put(stock.symbol(), priceService.getSevenDayPriceHistory(stock.symbol()));
        }

        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElseThrow();
        BigDecimal cashBalance = portfolio.cashBalance();

        List<PriceHistoryResponse> referenceDates = priceHistories.values().iterator().next();
        int days = referenceDates.size();

        List<WeeklyPerformanceResponse> result = new ArrayList<>();
        BigDecimal firstDayValue = null;

        for (int dayIdx = 0; dayIdx < days; dayIdx++) {
            LocalDate date = referenceDates.get(dayIdx).date();
            BigDecimal dayHoldingsValue = BigDecimal.ZERO;

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

            BigDecimal dailyProfit;
            if (dayIdx == 0) {
                dailyProfit = BigDecimal.ZERO;
            } else {
                BigDecimal prevValue = result.get(result.size() - 1).totalValue();
                dailyProfit = totalValue.subtract(prevValue).setScale(2, RoundingMode.HALF_UP);
            }

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
