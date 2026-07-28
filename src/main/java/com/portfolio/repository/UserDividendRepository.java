package com.portfolio.repository;

import com.portfolio.model.UserDividend;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for user_dividend table.
 */
public interface UserDividendRepository {
    Optional<UserDividend> findById(Long id);
    /**
     * Save a new user dividend record.
     */
    UserDividend save(UserDividend userDividend);
    
    /**
     * Find a specific user dividend by portfolio, symbol, and ex_date.
     */
    Optional<UserDividend> findByPortfolioIdAndSymbolAndExDate(Long portfolioId, String symbol, LocalDate exDate);
    
    /**
     * Find all pending dividends (pay_date <= today).
     */
    List<UserDividend> findPendingDividends(Long portfolioId, LocalDate asOfDate);
    
    /**
     * Find all dividends for a portfolio within a date range.
     */
    List<UserDividend> findByPortfolioIdAndDateRange(Long portfolioId, LocalDate startDate, LocalDate endDate);
    
    /**
     * Update status to 'paid' and set paid_at timestamp.
     */
    void markAsPaid(Long id);
    
    /**
     * Calculate total pending dividend amount for a portfolio.
     */
    BigDecimal sumPendingByPortfolioId(Long portfolioId);
    
    /**
     * Calculate total paid dividend amount for a portfolio within a date range.
     */
    BigDecimal sumPaidByPortfolioIdAndDateRange(Long portfolioId, LocalDate startDate, LocalDate endDate);
}
