package com.portfolio.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents an investment portfolio.
 */
public record Portfolio(
        Long id,
        String name,
        BigDecimal cashBalance,
        LocalDateTime createdAt
) {}
