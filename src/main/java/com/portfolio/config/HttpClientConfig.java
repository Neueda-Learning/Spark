package com.portfolio.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class HttpClientConfig {

    @Bean
    public RestTemplate restTemplate(
            RestTemplateBuilder builder,
            @Value("${ai.http.connect-timeout:10s}") Duration connectTimeout,
            @Value("${ai.http.request-timeout:60s}") Duration requestTimeout
    ) {
        return builder
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(requestTimeout)
                .build();
    }
}
