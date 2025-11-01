package com.fabricmanagement.common.platform.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Chat Response DTO for REST endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDto {

    /**
     * AI response message
     */
    private String message;

    /**
     * Conversation ID (for multi-turn)
     */
    private UUID conversationId;

    /**
     * Model used for generation
     */
    private String model;

    /**
     * Token usage statistics
     */
    private TokenUsageDto tokenUsage;

    /**
     * Suggested actions (if any)
     */
    private List<SuggestedAction> suggestedActions;

    /**
     * Whether user confirmation is required
     */
    private Boolean requiresConfirmation;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsageDto {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuggestedAction {
        private String type;      // e.g., "create_task", "check_stock"
        private String label;     // e.g., "Create purchase order"
        private String action;    // e.g., "/api/procurement/purchase-orders"
    }
}

