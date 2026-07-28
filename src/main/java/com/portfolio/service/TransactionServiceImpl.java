package com.portfolio.service;

import com.portfolio.dto.TransactionRequest;
import com.portfolio.dto.TransactionResponse;
import com.portfolio.model.Holding;
import com.portfolio.model.Portfolio;
import com.portfolio.model.Stock;
import com.portfolio.model.Transaction;
import com.portfolio.repository.HoldingRepository;
import com.portfolio.repository.PortfolioRepository;
import com.portfolio.repository.StockRepository;
import com.portfolio.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TransactionServiceImpl implements TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);

    private final StockRepository stockRepository;
    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    private final PriceService priceService;

    public TransactionServiceImpl(StockRepository stockRepository,
                                  PortfolioRepository portfolioRepository,
                                  HoldingRepository holdingRepository,
                                  TransactionRepository transactionRepository,
                                  PriceService priceService) {
        this.stockRepository = stockRepository;
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.transactionRepository = transactionRepository;
        this.priceService = priceService;
    }

    @Override
    @Transactional
    public TransactionResponse executeTransaction(Long portfolioId, TransactionRequest request) {
        // Validate stock exists
        Stock stock = stockRepository.findById(request.stockId())
                .orElseThrow(() -> new IllegalArgumentException("Stock not found: " + request.stockId()));

        // Validate portfolio exists
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found: " + portfolioId));

        BigDecimal currentPrice = priceService.getCurrentPrice(stock.symbol());
        BigDecimal totalAmount = currentPrice.multiply(request.quantity()).setScale(2, RoundingMode.HALF_UP);

        Optional<Holding> existingHolding = holdingRepository.findByPortfolioIdAndStockId(portfolioId, request.stockId());

        if ("BUY".equals(request.type())) {
            return executeBuy(portfolio, stock, existingHolding, request.quantity(), currentPrice, totalAmount);
        } else {
            return executeSell(portfolio, stock, existingHolding, request.quantity(), currentPrice, totalAmount);
        }
    }

    private TransactionResponse executeBuy(Portfolio portfolio, Stock stock,
                                           Optional<Holding> existingHolding,
                                           BigDecimal quantity, BigDecimal price, BigDecimal totalAmount) {
        // Validate sufficient cash
        if (portfolio.cashBalance().compareTo(totalAmount) < 0) {
            throw new IllegalArgumentException(
                    String.format("Insufficient cash. Available: %.2f, Required: %.2f",
                            portfolio.cashBalance(), totalAmount));
        }

        // Deduct cash
        BigDecimal newCash = portfolio.cashBalance().subtract(totalAmount);
        portfolioRepository.updateCashBalance(portfolio.id(), newCash);

        // Update or create holding
        if (existingHolding.isPresent()) {
            Holding holding = existingHolding.get();
            BigDecimal newQuantity = holding.quantity().add(quantity);
            // New average cost = (old total cost + new total cost) / new total quantity
            BigDecimal oldTotalCost = holding.averageCost().multiply(holding.quantity());
            BigDecimal newTotalCost = oldTotalCost.add(totalAmount);
            BigDecimal newAvgCost = newTotalCost.divide(newQuantity, 4, RoundingMode.HALF_UP);
            holdingRepository.updateQuantityAndAverageCost(holding.id(), newQuantity, newAvgCost);
        } else {
            Holding newHolding = new Holding(null, portfolio.id(), stock.id(), quantity, price);
            holdingRepository.save(newHolding);
        }

        // Record transaction
        Transaction tx = new Transaction(null, portfolio.id(), stock.id(), "BUY",
                quantity, price, totalAmount, LocalDateTime.now());
        Transaction savedTx = transactionRepository.save(tx);

        log.info("BUY: {} x {} of {} @ {}, remaining cash: {}", quantity, stock.symbol(), stock.name(), price, newCash);

        return new TransactionResponse(savedTx.id(), "BUY", stock.symbol(),
                quantity, price, totalAmount, newCash, savedTx.createdAt());
    }

    private TransactionResponse executeSell(Portfolio portfolio, Stock stock,
                                            Optional<Holding> existingHolding,
                                            BigDecimal quantity, BigDecimal price, BigDecimal totalAmount) {
        // Validate holding exists
        if (existingHolding.isEmpty()) {
            throw new IllegalArgumentException("No holding found for stock: " + stock.symbol());
        }

        Holding holding = existingHolding.get();

        // Validate sufficient quantity
        if (holding.quantity().compareTo(quantity) < 0) {
            throw new IllegalArgumentException(
                    String.format("Insufficient shares. Held: %s, Requested to sell: %s",
                            holding.quantity().toPlainString(), quantity.toPlainString()));
        }

        // Add cash
        BigDecimal newCash = portfolio.cashBalance().add(totalAmount);
        portfolioRepository.updateCashBalance(portfolio.id(), newCash);

        // Update or remove holding
        BigDecimal remainingQuantity = holding.quantity().subtract(quantity);
        if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            holdingRepository.deleteById(holding.id());
        } else {
            // Average cost stays the same when selling
            holdingRepository.updateQuantityAndAverageCost(holding.id(), remainingQuantity, holding.averageCost());
        }

        // Record transaction
        Transaction tx = new Transaction(null, portfolio.id(), stock.id(), "SELL",
                quantity, price, totalAmount, LocalDateTime.now());
        Transaction savedTx = transactionRepository.save(tx);

        log.info("SELL: {} x {} of {} @ {}, remaining cash: {}", quantity, stock.symbol(), stock.name(), price, newCash);

        return new TransactionResponse(savedTx.id(), "SELL", stock.symbol(),
                quantity, price, totalAmount, newCash, savedTx.createdAt());
    }
}
