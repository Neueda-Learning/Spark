package com.portfolio.repository;

import com.portfolio.model.Transaction;
import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository {
    Transaction save(Transaction transaction);
    List<Transaction> findByPortfolioId(Long portfolioId);
    
    /**
     * Returns all transactions for a stock on or before the specified date, ordered by time ascending.
     */
    List<Transaction> findByPortfolioIdAndStockIdBeforeDate(Long portfolioId, Long stockId, LocalDate date);
}
