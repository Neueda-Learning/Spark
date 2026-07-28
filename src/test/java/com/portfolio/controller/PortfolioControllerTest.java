package com.portfolio.controller;

import com.portfolio.dto.WeeklyPerformanceResponse;
import com.portfolio.service.PortfolioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioController.class)
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortfolioService portfolioService;

    @Test
    @DisplayName("GET /api/portfolio/weekly-performance returns daily performance items")
    void getWeeklyPerformance_returnsPerformanceItems() throws Exception {
        when(portfolioService.getWeeklyPerformance(1L)).thenReturn(List.of(
                new WeeklyPerformanceResponse(
                        LocalDate.of(2026, 7, 25),
                        new BigDecimal("700.00"),
                        new BigDecimal("20.00"),
                        new BigDecimal("10.00")
                ),
                new WeeklyPerformanceResponse(
                        LocalDate.of(2026, 7, 26),
                        new BigDecimal("690.00"),
                        new BigDecimal("-10.00"),
                        new BigDecimal("-5.00")
                )
        ));

        mockMvc.perform(get("/api/portfolio/weekly-performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-07-25"))
                .andExpect(jsonPath("$[0].totalValue").value(700.00))
                .andExpect(jsonPath("$[0].dailyProfit").value(20.00))
                .andExpect(jsonPath("$[0].returnRate").value(10.00))
                .andExpect(jsonPath("$[1].date").value("2026-07-26"))
                .andExpect(jsonPath("$[1].dailyProfit").value(-10.00));

        verify(portfolioService).getWeeklyPerformance(1L);
    }

    @Test
    @DisplayName("GET /api/portfolio/weekly-performance returns empty array when service has no data")
    void getWeeklyPerformance_returnsEmptyArray() throws Exception {
        when(portfolioService.getWeeklyPerformance(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/portfolio/weekly-performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(portfolioService).getWeeklyPerformance(1L);
    }
}