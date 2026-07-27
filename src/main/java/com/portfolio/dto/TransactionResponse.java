package com.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO after a successful buy/sell transaction.
 */
public record TransactionResponse(
        Long id,
        String type,
        String symbol,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        BigDecimal remainingCash,
        LocalDateTime createdAt
) {}
