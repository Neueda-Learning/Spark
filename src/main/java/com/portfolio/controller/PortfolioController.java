package com.portfolio.controller;

import com.portfolio.dto.*;
import com.portfolio.service.PortfolioService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private static final Long DEFAULT_PORTFOLIO_ID = 1L;

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    /**
     * GET /api/portfolio/overview - portfolio overview.
     * Frontend dashboard: total value, total profit, return rate, and asset allocation chart.
     */
    @GetMapping("/overview")
    public PortfolioOverviewResponse getOverview() {
        return portfolioService.getPortfolioOverview(DEFAULT_PORTFOLIO_ID);
    }

    /**
     * GET /api/portfolio/weekly-performance - performance changes over the last 7 days.
     * Frontend dashboard: date on the X axis, profit bars, and return-rate line.
     */
    @GetMapping("/weekly-performance")
    public List<WeeklyPerformanceResponse> getWeeklyPerformance() {
        return portfolioService.getWeeklyPerformance(DEFAULT_PORTFOLIO_ID);
    }

    /**
     * GET /api/portfolio/holdings - current holdings list.
     * Frontend trades page top section: symbol, name, quantity, average price, and current profit.
     */
    @GetMapping("/holdings")
    public List<HoldingResponse> getHoldings() {
        return portfolioService.getHoldings(DEFAULT_PORTFOLIO_ID);
    }

    /**
     * POST /api/portfolio/deposit - bank deposit.
     * Adds the specified amount to the portfolio cash balance.
     */
    @PostMapping("/deposit")
    public DepositResponse deposit(@Valid @RequestBody DepositRequest request) {
        return portfolioService.deposit(DEFAULT_PORTFOLIO_ID, request.amount());
    }

}
