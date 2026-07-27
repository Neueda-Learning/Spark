package com.portfolio.service;

import com.portfolio.dto.*;
import java.util.List;

public interface PortfolioService {
    PortfolioOverviewResponse getPortfolioOverview(Long portfolioId);
    List<HoldingResponse> getHoldings(Long portfolioId);
    List<WeeklyPerformanceResponse> getWeeklyPerformance(Long portfolioId);
}
