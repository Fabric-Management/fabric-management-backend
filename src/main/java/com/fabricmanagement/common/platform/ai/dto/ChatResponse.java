package com.fabricmanagement.common.platform.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Chat Response DTO.
 *
 * <p>Response from LLM chat API.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /**
     * Response message content
     */
    private String message;

    /**
     * Model used for generation
     */
    private String model;

    /**
     * Token usage statistics
     */
    private TokenUsage tokenUsage;

    /**
     * Finish reason (stop, length, etc.)
     */
    private String finishReason;

    /**
     * Tool calls from OpenAI (function calling)
     */
    private List<ToolCall> toolCalls;

    /**
     * Conversation ID (for multi-turn conversations)
     */
    private java.util.UUID conversationId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String id;
        private String functionName;
        private String arguments;
    }
}

