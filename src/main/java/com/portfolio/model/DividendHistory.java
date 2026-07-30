package com.portfolio.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a dividend event announced by a company.
 * Maps to the dividend_history table.
 */
public record DividendHistory(
        Long id,
        String symbol,
        LocalDate exDate,       // Ex-dividend date used to determine eligible holdings.
        LocalDate payDate,      // Payment date when the dividend is credited.
        BigDecimal dividendPerShare,  // Gross dividend amount per share before tax.
        String frequency,        // quarterly / semi-annual / annual
        LocalDate createdAt      // Record creation date.
) {}
