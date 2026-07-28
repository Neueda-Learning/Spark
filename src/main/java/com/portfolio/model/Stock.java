package com.portfolio.model;


/**
 * Represents an investment item (stock, bond, or cash equivalent).
 */
public record Stock(
        Long id,
        String symbol,
        String name,
        String assetType,
        String sector,
        String exchange,
        String currency
) {}
