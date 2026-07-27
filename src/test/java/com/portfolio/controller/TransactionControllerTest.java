package com.portfolio.controller;

import com.portfolio.dto.TransactionResponse;
import com.portfolio.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @Test
    @DisplayName("POST /api/portfolio/transactions returns 200 for valid BUY")
    void buy_valid_returns200() throws Exception {
        TransactionResponse response = new TransactionResponse(
                1L, "BUY", "AAPL", new BigDecimal("10"),
                new BigDecimal("195.50"), new BigDecimal("1955.00"),
                new BigDecimal("98045.00"), LocalDateTime.of(2026, 7, 26, 10, 0));
        when(transactionService.executeTransaction(eq(1L), any())).thenReturn(response);

        String body = """
                {
                  "stockId": 1,
                  "type": "BUY",
                  "quantity": 10
                }
                """;

        mockMvc.perform(post("/api/portfolio/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("BUY"))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.quantity").value(10));
    }

    @Test
    @DisplayName("POST /api/portfolio/transactions returns 400 when type is invalid")
    void invalidType_returns400() throws Exception {
        String body = """
                {
                  "stockId": 1,
                  "type": "INVALID",
                  "quantity": 10
                }
                """;

        mockMvc.perform(post("/api/portfolio/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("POST /api/portfolio/transactions returns 400 when quantity is missing")
    void missingQuantity_returns400() throws Exception {
        String body = """
                {
                  "stockId": 1,
                  "type": "BUY"
                }
                """;

        mockMvc.perform(post("/api/portfolio/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.quantity").exists());
    }

    @Test
    @DisplayName("POST /api/portfolio/transactions returns 400 when service rejects insufficient cash")
    void buy_insufficientCash_returns400() throws Exception {
        when(transactionService.executeTransaction(eq(1L), any()))
                .thenThrow(new IllegalArgumentException("Insufficient cash. Available: 100.00, Required: 19550.00"));

        String body = """
                {
                  "stockId": 1,
                  "type": "BUY",
                  "quantity": 100
                }
                """;

        mockMvc.perform(post("/api/portfolio/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient cash. Available: 100.00, Required: 19550.00"));
    }

    @Test
    @DisplayName("POST /api/portfolio/transactions returns 400 when service rejects insufficient shares")
    void sell_insufficientShares_returns400() throws Exception {
        when(transactionService.executeTransaction(eq(1L), any()))
                .thenThrow(new IllegalArgumentException("Insufficient shares. Held: 5, Requested to sell: 10"));

        String body = """
                {
                  "stockId": 1,
                  "type": "SELL",
                  "quantity": 10
                }
                """;

        mockMvc.perform(post("/api/portfolio/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient shares. Held: 5, Requested to sell: 10"));
    }
}
