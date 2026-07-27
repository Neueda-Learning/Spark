package com.portfolio.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Records a buy or sell transaction.
 */
public record Transaction(
        Long id,
        Long portfolioId,
        Long stockId,
        String type,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        LocalDateTime createdAt
) {}
