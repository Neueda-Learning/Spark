package com.portfolio.controller;

import com.portfolio.dto.PriceHistoryResponse;
import com.portfolio.dto.StockInfoResponse;
import com.portfolio.model.Stock;
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
}
