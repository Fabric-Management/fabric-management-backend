package com.fabricmanagement.common.platform.ai.config;

import com.fabricmanagement.common.platform.ai.infra.client.LLMClient;
import com.fabricmanagement.common.platform.ai.infra.client.impl.OpenAIClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI Configuration - LLM Client Bean Selection.
 *
 * <p>Selects appropriate LLM client based on application.ai.provider configuration.</p>
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class AIConfig {

    private final AIProperties aiProperties;
    private final ObjectMapper objectMapper;

    @Bean
    @ConditionalOnProperty(name = "application.ai.provider", havingValue = "openai", matchIfMissing = true)
    public LLMClient openAIClient() {
        log.info("Configuring OpenAI client: model={}", aiProperties.getModel());
        return new OpenAIClient(aiProperties, objectMapper);
    }

    // Future: AnthropicClient, LocalLLMClient beans can be added here
    // @Bean
    // @ConditionalOnProperty(name = "application.ai.provider", havingValue = "anthropic")
    // public LLMClient anthropicClient() {
    //     return new AnthropicClient(aiProperties, objectMapper);
    // }
}

