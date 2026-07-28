package com.portfolio.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Simulated dividend service with predefined tax rates.
 * In production, this would call a real tax API or use account-specific tax info.
 */
@Service
public class SimulatedDividendService implements DividendService {

    @Override
    public BigDecimal getTaxRate(String symbol) {
        return switch (symbol) {
            // 美股 — 合格股息 15%（持有>60天，联邦税率）
            case "AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "TSLA",
                "META", "JPM", "JNJ", "V", "PG", "XOM", "UNH", "MA"
                -> new BigDecimal("0.15");

            // 债券ETF — 利息收入，按普通所得税 22%
            case "AGG", "BND", "TLT", "LQD"
                -> new BigDecimal("0.22");

            // 现金类 — 利息收入 22%
            case "USD", "USDMONEY"
                -> new BigDecimal("0.22");

            // 默认
            default -> BigDecimal.ZERO;
        };
    }
}
