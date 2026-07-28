package com.portfolio.controller;

import com.portfolio.dto.CandleResponse;
import com.portfolio.dto.CandleSeriesResponse;
import com.portfolio.dto.PriceHistoryResponse;
import com.portfolio.dto.StockInfoResponse;
import com.portfolio.model.Stock;
import com.portfolio.service.CandleService;
import com.portfolio.service.PriceService;
import com.portfolio.service.StockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StockController.class)
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StockService stockService;

    @MockBean
    private PriceService priceService;

    @MockBean
    private CandleService candleService;

    private final Stock aapl = new Stock(1L, "AAPL", "Apple Inc.", "STOCK", "Technology", "NASDAQ", "USD");

    @Test
    @DisplayName("GET /api/stocks returns 200 and a JSON array with prices")
    void getAll_returns200() throws Exception {
        StockInfoResponse response = new StockInfoResponse(
                1L, "AAPL", "Apple Inc.", "STOCK", "Technology", "NASDAQ",
                new BigDecimal("195.50"), new BigDecimal("1.25"));
        when(stockService.getAllStocksWithPrice()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].currentPrice").value(195.50));
    }

    @Test
    @DisplayName("GET /api/stocks/{id} returns 200 when stock exists")
    void getById_found_returns200() throws Exception {
        when(stockService.getStockById(1L)).thenReturn(Optional.of(aapl));

        mockMvc.perform(get("/api/stocks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.name").value("Apple Inc."));
    }

    @Test
    @DisplayName("GET /api/stocks/{id} returns 404 when not found")
    void getById_notFound_returns404() throws Exception {
        when(stockService.getStockById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/stocks/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/stocks/{id}/prices returns 7-day price history")
    void getPrices_returns200() throws Exception {
        when(stockService.getStockById(1L)).thenReturn(Optional.of(aapl));
        when(priceService.getSevenDayPriceHistory("AAPL")).thenReturn(List.of(
                new PriceHistoryResponse(LocalDate.of(2026, 7, 20), new BigDecimal("193.00"),
                        new BigDecimal("195.50"), new BigDecimal("196.00"), new BigDecimal("192.50"), 15_000_000L)
        ));

        mockMvc.perform(get("/api/stocks/1/prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].closePrice").value(195.50))
                .andExpect(jsonPath("$[0].date").value("2026-07-20"));
    }

    @Test
    @DisplayName("GET /api/stocks/{id}/prices returns 404 when stock not found")
    void getPrices_stockNotFound_returns404() throws Exception {
        when(stockService.getStockById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/stocks/99/prices"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/stocks/{id}/candles returns weekly candles")
    void getCandles_returns200() throws Exception {
        when(stockService.getStockById(1L)).thenReturn(Optional.of(aapl));
        when(candleService.getWeeklyCandles(1L, 52)).thenReturn(new CandleSeriesResponse(
                1L,
                "AAPL",
                "WEEKLY",
                "YAHOO",
                LocalDate.of(2026, 7, 24),
                List.of(new CandleResponse(
                        LocalDate.of(2026, 7, 20),
                        new BigDecimal("210.10"),
                        new BigDecimal("218.20"),
                        new BigDecimal("208.40"),
                        new BigDecimal("216.75"),
                        new BigDecimal("216.75"),
                        305_000_000L
                ))
        ));

        mockMvc.perform(get("/api/stocks/1/candles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interval").value("WEEKLY"))
                .andExpect(jsonPath("$.asOf").value("2026-07-24"))
                .andExpect(jsonPath("$.candles[0].date").value("2026-07-20"))
                .andExpect(jsonPath("$.candles[0].close").value(216.75));
    }

    @Test
    @DisplayName("GET /api/stocks/{id}/candles rejects unsupported interval")
    void getCandles_unsupportedInterval_returns400() throws Exception {
        mockMvc.perform(get("/api/stocks/1/candles").param("interval", "DAILY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported candle interval: DAILY"));
    }

    @Test
    @DisplayName("GET /api/stocks/{id}/candles rejects weeks outside supported range")
    void getCandles_invalidWeeks_returns400() throws Exception {
        mockMvc.perform(get("/api/stocks/1/candles").param("weeks", "105"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("weeks must be between 1 and 104"));
    }

    @Test
    @DisplayName("GET /api/stocks/{id}/candles returns 404 when stock not found")
    void getCandles_stockNotFound_returns404() throws Exception {
        when(stockService.getStockById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/stocks/99/candles"))
                .andExpect(status().isNotFound());
    }
}
