package com.portfolio.repository;

import com.portfolio.model.DividendHistory;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for dividend_history table.
 */
public interface DividendHistoryRepository {
    Optional<DividendHistory> findById(Long id);
    
    /**
     * Find all dividend events for a given symbol.
     */
    List<DividendHistory> findBySymbol(String symbol);
    
    /**
     * Find all dividend events.
     */
    List<DividendHistory> findAll();
    
    /**
     * Find dividend events with ex_date on or before a given date.
     */
    List<DividendHistory> findUpToDate(java.time.LocalDate date);
}
