package com.portfolio.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Simulated dividend service with predefined tax rates.
 * In production, this would call a real tax API or use account-specific tax info.
 */
@Service
public class SimulatedDividendService implements DividendService {

    @Override
    public BigDecimal getTaxRate(String symbol) {
        return switch (symbol) {
            // US equities - qualified dividends taxed at 15% when holding requirements are met.
            case "AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "TSLA",
                "META", "JPM", "JNJ", "V", "PG", "XOM", "UNH", "MA"
                -> new BigDecimal("0.15");

            // Bond ETFs - interest income taxed as ordinary income at 22%.
            case "AGG", "BND", "TLT", "LQD"
                -> new BigDecimal("0.22");

            // Cash-like instruments - interest income taxed at 22%.
            case "USD", "USDMONEY"
                -> new BigDecimal("0.22");

            // Default
            default -> BigDecimal.ZERO;
        };
    }
}
