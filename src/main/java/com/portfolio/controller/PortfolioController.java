package com.portfolio.controller;

import com.portfolio.dto.*;
import com.portfolio.service.PortfolioService;
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
     * GET /api/portfolio/overview — 组合总览
     * 前端页面1：总价值、总盈利、收益率、饼状图（资产分配）
     */
    @GetMapping("/overview")
    public PortfolioOverviewResponse getOverview() {
        return portfolioService.getPortfolioOverview(DEFAULT_PORTFOLIO_ID);
    }

    /**
     * GET /api/portfolio/weekly-performance — 过去7天收益变化
     * 前端页面1：横轴日期、纵轴条形图(收益金额)+折线图(收益率)
     */
    @GetMapping("/weekly-performance")
    public List<WeeklyPerformanceResponse> getWeeklyPerformance() {
        return portfolioService.getWeeklyPerformance(DEFAULT_PORTFOLIO_ID);
    }

    /**
     * GET /api/portfolio/holdings — 当前持仓列表
     * 前端页面2上部分：代码、名字、持有数量、平均价格、当前盈利
     */
    @GetMapping("/holdings")
    public List<HoldingResponse> getHoldings() {
        return portfolioService.getHoldings(DEFAULT_PORTFOLIO_ID);
    }
}
