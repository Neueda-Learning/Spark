package com.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response DTO for a single day in the 7-day performance chart (page 1).
 */
public record WeeklyPerformanceResponse(
        LocalDate date,
        BigDecimal totalValue,
        BigDecimal dailyProfit,
        BigDecimal returnRate
) {}
