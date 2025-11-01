package com.fabricmanagement.common.platform.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AI Action Request - Structured action from AI assistant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIActionRequest {

    /**
     * Action name (e.g., "check_material_stock", "create_purchase_order")
     */
    private String action;

    /**
     * Action parameters
     */
    private Map<String, Object> parameters;

    /**
     * Whether user confirmation is required
     */
    private Boolean requiresConfirmation;
}

