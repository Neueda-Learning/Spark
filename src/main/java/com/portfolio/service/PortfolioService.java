package com.portfolio.service;

import com.portfolio.dto.*;

import java.math.BigDecimal;
import java.util.List;

public interface PortfolioService {
    PortfolioOverviewResponse getPortfolioOverview(Long portfolioId);
    List<HoldingResponse> getHoldings(Long portfolioId);
    List<WeeklyPerformanceResponse> getWeeklyPerformance(Long portfolioId);
    DepositResponse deposit(Long portfolioId, BigDecimal amount);
}
