package com.portfolio.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Request DTO for buy/sell transactions.
 */
public record TransactionRequest(

        @NotNull(message = "Stock ID is required")
        Long stockId,

        @NotNull(message = "Transaction type is required")
        @Pattern(regexp = "BUY|SELL", message = "Type must be BUY or SELL")
        String type,

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.0001", message = "Quantity must be positive")
        BigDecimal quantity
) {}
