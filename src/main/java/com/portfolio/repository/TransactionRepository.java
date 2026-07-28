package com.portfolio.repository;

import com.portfolio.model.Transaction;
import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository {
    Transaction save(Transaction transaction);
    List<Transaction> findByPortfolioId(Long portfolioId);
    
    /**
     * 查询某只股票在指定日期之前（含）的所有交易记录，按时间升序
     */
    List<Transaction> findByPortfolioIdAndStockIdBeforeDate(Long portfolioId, Long stockId, LocalDate date);
}
