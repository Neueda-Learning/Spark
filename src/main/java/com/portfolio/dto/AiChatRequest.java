package com.portfolio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AiChatRequest(
        @NotBlank(message = "message is required")
        String message,
        @Valid
        List<ChatMessage> history
) {
    public record ChatMessage(
            @NotBlank(message = "role is required")
            String role,
            @NotBlank(message = "content is required")
            String content
    ) {}
}