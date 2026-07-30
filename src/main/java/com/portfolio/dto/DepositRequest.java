package com.portfolio.dto;

import java.math.BigDecimal;

/**
 * Request body for bank deposit.
 */
public record DepositRequest(
        BigDecimal amount
) {}
