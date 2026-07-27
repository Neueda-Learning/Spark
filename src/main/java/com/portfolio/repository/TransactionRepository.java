package com.portfolio.repository;

import com.portfolio.model.Transaction;
import java.util.List;

public interface TransactionRepository {
    Transaction save(Transaction transaction);
    List<Transaction> findByPortfolioId(Long portfolioId);
}
