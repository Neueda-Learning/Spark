package com.portfolio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.dto.AiChatRequest;
import com.portfolio.repository.AiAssistantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceImplTest {

    @Mock
    private AiAssistantRepository repository;

    @Mock
    private PriceService priceService;

    @Mock
    private OfficialMarketDataService officialMarketDataService;

    @Mock
    private LlmGateway llmGateway;

    private AiAssistantServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiAssistantServiceImpl(
                repository,
                priceService,
                officialMarketDataService,
                llmGateway,
                new ObjectMapper()
        );
    }

    @Test
    void keepsPartialContextWhenPricesAreUnavailable() {
        when(repository.findHeldStocks(1L)).thenReturn(List.of(
                new AiAssistantRepository.HeldStockRow(
                        1L,
                        "AAPL",
                        "Apple Inc.",
                        "STOCK",
                        new BigDecimal("2"),
                        new BigDecimal("150")
                )
        ));
        when(repository.findAvailableStocks(1L)).thenReturn(List.of(
                new AiAssistantRepository.AvailableStockRow(
                        2L,
                        "TSLA",
                        "Tesla Inc.",
                        "STOCK"
                )
        ));
        when(repository.findPortfolioCashBalance(1L)).thenReturn(Optional.of(new BigDecimal("1000")));
        when(priceService.getCurrentPrice(anyString()))
                .thenThrow(new IllegalStateException("market data unavailable"));
        when(officialMarketDataService.buildInsights(anyList())).thenReturn(List.of());
        when(llmGateway.chat(anyList())).thenReturn("partial analysis");

        assertThat(service.chat(
                1L,
                new AiChatRequest("Which stock should I buy?", List.of())
        ).reply()).isEqualTo("partial analysis");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LlmGateway.Message>> messagesCaptor =
                ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(llmGateway).chat(messagesCaptor.capture());

        String context = messagesCaptor.getValue().get(1).content();
        assertThat(context)
                .contains("\"symbol\" : \"AAPL\"")
                .contains("\"symbol\" : \"TSLA\"")
                .contains("Current price is unavailable for held symbol AAPL")
                .contains("Market data is unavailable for candidate symbol TSLA")
                .contains("\"total_market_value\" : 0.00")
                .doesNotContain("\"latest_price\" : 0");
    }
}
