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
        LocalDate exDate,       // 除息日：判断是否持有的截止日
        LocalDate payDate,      // 派息日：分红到账日
        BigDecimal dividendPerShare,  // 每股税前分红金额
        String frequency,        // quarterly / semi-annual / annual
        LocalDate createdAt      // 记录创建时间
) {}
