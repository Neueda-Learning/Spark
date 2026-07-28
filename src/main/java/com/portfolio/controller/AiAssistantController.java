package com.portfolio.controller;

import com.portfolio.dto.AiChatRequest;
import com.portfolio.dto.AiChatResponse;
import com.portfolio.service.AiAssistantService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/portfolio")
public class AiAssistantController {

    private static final Long DEFAULT_PORTFOLIO_ID = 1L;

    private final AiAssistantService aiAssistantService;

    public AiAssistantController(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    /**
     * POST /api/portfolio/ai-assistant/chat
     * User sends plain chat message; portfolio context and prompts are composed internally.
     */
    @PostMapping("/ai-assistant/chat")
    public AiChatResponse chat(@Valid @RequestBody AiChatRequest request) {
        return aiAssistantService.chat(DEFAULT_PORTFOLIO_ID, request);
    }

    @PostMapping(value = "/ai-assistant/chat/stream", produces = MediaType.TEXT_PLAIN_VALUE)
    public StreamingResponseBody streamChat(@Valid @RequestBody AiChatRequest request) {
        return outputStream -> aiAssistantService.streamChat(DEFAULT_PORTFOLIO_ID, request, chunk -> {
            try {
                outputStream.write(chunk.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            } catch (java.io.IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}