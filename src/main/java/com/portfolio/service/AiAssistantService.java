package com.portfolio.service;

import com.portfolio.dto.AiChatRequest;
import com.portfolio.dto.AiChatResponse;

import java.util.function.Consumer;

public interface AiAssistantService {
    AiChatResponse chat(Long portfolioId, AiChatRequest request);
    void streamChat(Long portfolioId, AiChatRequest request, Consumer<String> chunkConsumer);
}