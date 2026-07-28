package com.portfolio.service;

import java.util.List;
import java.util.function.Consumer;

public interface LlmGateway {

    String chat(List<Message> messages);

    void streamChat(List<Message> messages, Consumer<String> chunkConsumer);

    record Message(String role, String content) {}
}