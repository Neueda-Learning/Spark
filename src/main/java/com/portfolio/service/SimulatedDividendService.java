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

    // Simulated tax rates by asset type
    // US domestic stocks: 0% (no withholding for domestic investors)
    // Bond ETFs: 0% (qualified dividends)
    // International stocks: would be 15-30% (not in our universe)
    private static final Map<String, BigDecimal> TAX_RATES = Map.of(
        "STOCK", BigDecimal.ZERO,           // 美股无预扣税（国内投资者）
        "BOND", new BigDecimal("0.0000"),   // 债券ETF免税
        "CASH_EQUIVALENT", BigDecimal.ZERO  // 现金类免税
    );

    // Alternative: symbol-specific rates (can be enabled for more realism)
    // For now, all rates are 0% to keep it simple for domestic investors
    private static final Map<String, BigDecimal> SYMBOL_TAX_RATES = Map.ofEntries(
        // US Stocks - 0% withholding
        Map.entry("AAPL", BigDecimal.ZERO),
        Map.entry("MSFT", BigDecimal.ZERO),
        Map.entry("GOOGL", BigDecimal.ZERO),
        Map.entry("AMZN", BigDecimal.ZERO),
        Map.entry("NVDA", BigDecimal.ZERO),
        Map.entry("TSLA", BigDecimal.ZERO),
        Map.entry("META", BigDecimal.ZERO),
        Map.entry("JPM", BigDecimal.ZERO),
        Map.entry("JNJ", BigDecimal.ZERO),
        Map.entry("V", BigDecimal.ZERO),
        Map.entry("PG", BigDecimal.ZERO),
        Map.entry("XOM", BigDecimal.ZERO),
        Map.entry("UNH", BigDecimal.ZERO),
        Map.entry("MA", BigDecimal.ZERO),
        // Bond ETFs - 0% (tax-exempt or qualified)
        Map.entry("AGG", BigDecimal.ZERO),
        Map.entry("BND", BigDecimal.ZERO),
        Map.entry("TLT", BigDecimal.ZERO),
        Map.entry("LQD", BigDecimal.ZERO),
        // Cash - 0%
        Map.entry("USD", BigDecimal.ZERO),
        Map.entry("USDMONEY", BigDecimal.ZERO)
    );

    @Override
    public BigDecimal getTaxRate(String symbol) {
        // Try symbol-specific rate first
        BigDecimal rate = SYMBOL_TAX_RATES.get(symbol);
        if (rate != null) {
            return rate;
        }
        // Default: 0% (domestic investor, no withholding)
        return BigDecimal.ZERO;
    }
}
