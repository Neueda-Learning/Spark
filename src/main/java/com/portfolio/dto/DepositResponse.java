package com.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response after a successful deposit.
 */
public record DepositResponse(
        boolean success,
        String message,
        BigDecimal previousBalance,
        BigDecimal depositAmount,
        BigDecimal newBalance,
        LocalDateTime depositedAt
) {}
