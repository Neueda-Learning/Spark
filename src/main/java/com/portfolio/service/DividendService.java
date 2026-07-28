package com.portfolio.service;

import java.math.BigDecimal;

/**
 * Service for dividend-related calculations.
 */
public interface DividendService {
    
    /**
     * Get simulated tax rate for a given stock symbol.
     * Currently returns a simulated value; can be replaced with real API later.
     * 
     * @param symbol stock symbol
     * @return tax rate (e.g., 0.1000 = 10%)
     */
    BigDecimal getTaxRate(String symbol);
}
