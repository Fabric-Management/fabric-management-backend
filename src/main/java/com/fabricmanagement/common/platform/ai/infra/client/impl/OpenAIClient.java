package com.fabricmanagement.common.platform.ai.infra.client.impl;

import com.fabricmanagement.common.platform.ai.config.AIProperties;
import com.fabricmanagement.common.platform.ai.dto.ChatRequest;
import com.fabricmanagement.common.platform.ai.dto.ChatResponse;
import com.fabricmanagement.common.platform.ai.infra.client.LLMClient;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Client Implementation.
 *
 * <p>Implements LLMClient interface for OpenAI API integration.</p>
 * <p>Bean is registered via AIConfig based on application.ai.provider configuration.</p>
 */
@Slf4j
public class OpenAIClient implements LLMClient {

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    private final AIProperties aiProperties;
    @SuppressWarnings("unused") // Reserved for future error handling / custom deserialization
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public OpenAIClient(AIProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.restTemplate = createRestTemplate();
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(aiProperties.getTimeout());
        factory.setReadTimeout(aiProperties.getTimeout());
        return new RestTemplate(factory);
    }

    /**
     * Maximum number of retry attempts for transient errors (5xx).
     */
    private static final int MAX_RETRIES = 3;
    
    /**
     * Initial retry delay in milliseconds (exponential backoff: 1s → 2s → 4s).
     */
    private static final long INITIAL_RETRY_DELAY_MS = 1000L;

    @Override
    public ChatResponse chat(ChatRequest request) {
        if (!aiProperties.getEnabled()) {
            throw new IllegalStateException("AI features are disabled");
        }

        if (aiProperties.getApiKey() == null || aiProperties.getApiKey().isBlank()) {
            throw new IllegalStateException("AI API key is not configured. Please set OPENAI_API_KEY in .env file or as environment variable.");
        }

        log.debug("Sending chat request to OpenAI: model={}, messages={}", 
            request.getModel() != null ? request.getModel() : aiProperties.getModel(),
            request.getMessages().size());

        // Retry logic with exponential backoff for transient errors (5xx)
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Map<String, Object> requestBody = buildRequestBody(request);
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(aiProperties.getApiKey());

                HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);

                ResponseEntity<OpenAIResponse> response = restTemplate.exchange(
                    OPENAI_API_URL,
                    HttpMethod.POST,
                    httpEntity,
                    OpenAIResponse.class
                );

                OpenAIResponse openAIResponse = response.getBody();
                if (openAIResponse == null || openAIResponse.getChoices() == null || openAIResponse.getChoices().isEmpty()) {
                    throw new RuntimeException("Empty response from OpenAI");
                }

                // Success - return response
                if (attempt > 1) {
                    log.info("✅ OpenAI API call succeeded after {} retry attempts", attempt - 1);
                }
                return mapToChatResponse(openAIResponse);

            } catch (org.springframework.web.client.HttpServerErrorException e) {
                // 5xx errors (server errors) - retry with exponential backoff
                lastException = e;
                int statusCode = e.getStatusCode().value();
                
                if (attempt < MAX_RETRIES) {
                    long delayMs = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1);
                    log.warn("OpenAI API returned {} (attempt {}/{}) - retrying in {}ms: {}", 
                        statusCode, attempt, MAX_RETRIES, delayMs, e.getMessage());
                    
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                } else {
                    log.error("OpenAI API failed after {} attempts with {}: {}", 
                        MAX_RETRIES, statusCode, e.getMessage());
                }
                
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                // 4xx errors (client errors) - don't retry, fail immediately
                log.error("OpenAI API client error ({}): {}", e.getStatusCode().value(), e.getMessage());
                throw new RuntimeException(
                    String.format("OpenAI API client error (%d): %s. Please check your API key and request format.", 
                        e.getStatusCode().value(), e.getMessage()), e);
                    
            } catch (org.springframework.web.client.ResourceAccessException e) {
                // Network/timeout errors - retry with exponential backoff
                lastException = e;
                
                if (attempt < MAX_RETRIES) {
                    long delayMs = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1);
                    log.warn("OpenAI API network error (attempt {}/{}) - retrying in {}ms: {}", 
                        attempt, MAX_RETRIES, delayMs, e.getMessage());
                    
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                } else {
                    log.error("OpenAI API network error after {} attempts: {}", MAX_RETRIES, e.getMessage());
                }
                
            } catch (Exception e) {
                // Other errors - don't retry
                log.error("Unexpected error calling OpenAI API", e);
                throw new RuntimeException("Failed to get response from OpenAI: " + e.getMessage(), e);
            }
        }

        // All retries exhausted
        String errorMessage = lastException != null ? lastException.getMessage() : "Unknown error";
        throw new RuntimeException(
            String.format("Failed to get response from OpenAI after %d attempts. Last error: %s", 
                MAX_RETRIES, errorMessage), lastException);
    }

    private Map<String, Object> buildRequestBody(ChatRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel() != null ? request.getModel() : aiProperties.getModel());
        body.put("messages", request.getMessages());
        body.put("temperature", request.getTemperature() != null ? request.getTemperature() : aiProperties.getTemperature());
        body.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : aiProperties.getMaxTokens());
        
        // Add OpenAI function calling tools
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", request.getTools());
            body.put("tool_choice", request.getToolChoice() != null ? request.getToolChoice() : "auto");
        }
        
        return body;
    }

    private ChatResponse mapToChatResponse(OpenAIResponse openAIResponse) {
        OpenAIResponse.Choice choice = openAIResponse.getChoices().get(0);
        OpenAIResponse.Message message = choice.getMessage();

        ChatResponse.TokenUsage tokenUsage = ChatResponse.TokenUsage.builder()
            .promptTokens(openAIResponse.getUsage() != null ? openAIResponse.getUsage().getPromptTokens() : 0)
            .completionTokens(openAIResponse.getUsage() != null ? openAIResponse.getUsage().getCompletionTokens() : 0)
            .totalTokens(openAIResponse.getUsage() != null ? openAIResponse.getUsage().getTotalTokens() : 0)
            .build();

        ChatResponse.ChatResponseBuilder responseBuilder = ChatResponse.builder()
            .message(message.getContent() != null ? message.getContent() : "")
            .model(openAIResponse.getModel())
            .tokenUsage(tokenUsage)
            .finishReason(choice.getFinishReason());

        // Handle tool calls if present
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            List<ChatResponse.ToolCall> toolCalls = message.getToolCalls().stream()
                .map(tc -> ChatResponse.ToolCall.builder()
                    .id(tc.getId())
                    .functionName(tc.getFunction().getName())
                    .arguments(tc.getFunction().getArguments())
                    .build())
                .toList();
            responseBuilder.toolCalls(toolCalls);
        }

        return responseBuilder.build();
    }

    @Data
    static class OpenAIResponse {
        private String id;
        private String model;
        private List<Choice> choices;
        private Usage usage;

        @Data
        static class Choice {
            private Message message;
            private String finishReason;
            private Integer index;
        }

        @Data
        static class Message {
            private String role;
            private String content;
            @JsonProperty("tool_calls")
            private List<ToolCall> toolCalls;
        }

        @Data
        static class ToolCall {
            private String id;
            private String type;
            private ToolCallFunction function;
        }

        @Data
        static class ToolCallFunction {
            private String name;
            private String arguments;
        }

        @Data
        static class Usage {
            @JsonProperty("prompt_tokens")
            private Integer promptTokens;
            @JsonProperty("completion_tokens")
            private Integer completionTokens;
            @JsonProperty("total_tokens")
            private Integer totalTokens;
        }
    }
}

