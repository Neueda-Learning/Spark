package com.portfolio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Component
public class HttpLlmGateway implements LlmGateway {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final String provider;
    private final String model;
    private final String apiKey;
    private final String chatUrl;

    public HttpLlmGateway(RestTemplate restTemplate,
                          ObjectMapper objectMapper,
                          @Value("${ai.provider:openai}") String provider,
                          @Value("${ai.model:gpt-4o-mini}") String model,
                          @Value("${ai.api-key:}") String apiKey,
                          @Value("${ai.chat-url:https://api.openai.com/v1/chat/completions}") String chatUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.provider = provider;
        this.model = model;
        this.apiKey = apiKey;
        this.chatUrl = chatUrl;
    }

    @Override
    public String chat(List<Message> messages) {
        validateApiKey();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = buildRequestBody(messages, false);

        ResponseEntity<String> response = restTemplate.exchange(
                chatUrl,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new IllegalStateException("Invalid LLM response: missing choices[0].message.content");
            }
            return content.asText();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse LLM response from provider: " + provider, e);
        }
    }

    @Override
    public void streamChat(List<Message> messages, Consumer<String> chunkConsumer) {
        validateApiKey();

        Map<String, Object> body = buildRequestBody(messages, true);
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize streaming request body", e);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(chatUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() >= 400) {
                String errorBody = response.body().reduce("", (left, right) -> left + right);
                throw new IllegalStateException("LLM provider returned status " + response.statusCode() + ": " + errorBody);
            }

            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> handleStreamLine(line, chunkConsumer));
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Streaming request to provider failed", e);
        }
    }

    private Map<String, Object> buildRequestBody(List<Message> messages, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages.stream().map(m -> Map.of(
                "role", mapRole(m.role()),
                "content", m.content()
        )).toList());
        body.put("temperature", 0.4);
        if (stream) {
            body.put("stream", true);
        }
        return body;
    }

    private void handleStreamLine(String line, Consumer<String> chunkConsumer) {
        if (line == null || line.isBlank() || !line.startsWith("data:")) {
            return;
        }

        String payload = line.substring(5).trim();
        if ("[DONE]".equals(payload)) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode contentNode = root.path("choices").path(0).path("delta").path("content");
            String chunk = extractContent(contentNode);
            if (!chunk.isEmpty()) {
                chunkConsumer.accept(chunk);
            }
        } catch (Exception ignored) {
            // Ignore non-content streaming events.
        }
    }

    private String extractContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            contentNode.forEach(node -> {
                if (node.has("text")) {
                    builder.append(node.path("text").asText(""));
                }
            });
            return builder.toString();
        }
        return "";
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("AI provider API key is not configured.");
        }
    }

    private String mapRole(String role) {
        String r = role == null ? "user" : role.toLowerCase();
        return switch (r) {
            case "system", "user", "assistant" -> r;
            default -> "user";
        };
    }
}