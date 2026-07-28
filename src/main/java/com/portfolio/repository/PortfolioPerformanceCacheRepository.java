package com.portfolio.repository;

import com.portfolio.model.PortfolioPerformanceCache;

import java.time.LocalDate;
import java.util.List;

public interface PortfolioPerformanceCacheRepository {
    List<PortfolioPerformanceCache> findByPortfolioIdAndPerformanceDates(Long portfolioId, List<LocalDate> performanceDates);
    void replaceForPortfolio(Long portfolioId, List<PortfolioPerformanceCache> entries);
    void deleteByPortfolioId(Long portfolioId);
}
