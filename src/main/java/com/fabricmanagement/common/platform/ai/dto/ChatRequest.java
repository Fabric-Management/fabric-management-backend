package com.fabricmanagement.common.platform.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Chat Request DTO.
 *
 * <p>Request payload for LLM chat API.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /**
     * List of messages (system + user + assistant + tool)
     * Format: [{"role": "system", "content": "..."}, {"role": "user", "content": "..."}]
     * For tool calls: {"role": "assistant", "content": "...", "tool_calls": [...]}
     * For tool results: {"role": "tool", "content": "...", "tool_call_id": "..."}
     */
    private List<Map<String, Object>> messages;

    /**
     * Model name (optional, can override default)
     */
    private String model;

    /**
     * Temperature (optional, can override default)
     */
    private Double temperature;

    /**
     * Max tokens (optional, can override default)
     */
    private Integer maxTokens;

    /**
     * OpenAI function calling tools (optional)
     * Format: [{"type": "function", "function": {"name": "...", "parameters": {...}}}]
     */
    private List<Map<String, Object>> tools;

    /**
     * Tool choice: "none", "auto", or {"type": "function", "function": {"name": "..."}}
     */
    private Object toolChoice;
}

