package com.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * API response for AI assistant context + prompt bundle.
 */
public record AiAssistantResponse(
        @JsonProperty("held_stocks")
        List<HeldStock> heldStocks,
        @JsonProperty("available_stocks")
        List<AvailableStock> availableStocks,
        @JsonProperty("portfolio_summary")
        PortfolioSummary portfolioSummary,
        @JsonProperty("system_prompt")
        String systemPrompt,
        @JsonProperty("user_prompt")
        String userPrompt
) {
    public record HeldStock(
            Long stockId,
            String symbol,
            String name,
            String assetType,
            BigDecimal quantity,
            BigDecimal averageCost,
            BigDecimal latestPrice,
            BigDecimal profitRate
    ) {}

    public record AvailableStock(
            Long stockId,
            String symbol,
            String name,
            String assetType,
            BigDecimal latestPrice,
            BigDecimal changePercent
    ) {}

    public record PortfolioSummary(
            BigDecimal totalAsset,
            BigDecimal totalMarketValue,
            BigDecimal cashBalance
    ) {}
}