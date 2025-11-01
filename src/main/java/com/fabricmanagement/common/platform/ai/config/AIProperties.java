package com.fabricmanagement.common.platform.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI Configuration Properties.
 *
 * <p>Externalizes all AI-related configuration values from application.yml and environment variables.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @Autowired
 * private AIProperties aiProperties;
 *
 * String apiKey = aiProperties.getApiKey();
 * }</pre>
 */
@Component
@ConfigurationProperties(prefix = "application.ai")
@Data
public class AIProperties {

    /**
     * LLM provider: openai, anthropic, or local (Ollama)
     */
    private String provider = "openai";

    /**
     * API key for LLM provider (from .env)
     */
    private String apiKey;

    /**
     * Model name (e.g., gpt-4o-mini, claude-3-haiku)
     */
    private String model = "gpt-4o-mini";

    /**
     * Temperature for response generation (0.0-2.0)
     */
    private Double temperature = 0.7;

    /**
     * Maximum tokens in response
     */
    private Integer maxTokens = 1000;

    /**
     * Request timeout in milliseconds
     */
    private Integer timeout = 30000;

    /**
     * Enable/disable AI features
     */
    private Boolean enabled = true;
}

