package com.fabricmanagement.common.platform.ai.app;

import com.fabricmanagement.common.platform.ai.config.AIProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * History Trimmer - Optimizes conversation history to reduce token usage.
 *
 * <p>Smart trimming: Keeps system message, recent messages, and summarizes old ones.</p>
 * <p>MANIFESTO: KISS - Simple token estimation, no complex ML</p>
 */
@Component
@Slf4j
public class HistoryTrimmer {

    private final AIProperties aiProperties;
    
    public HistoryTrimmer(AIProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    // Approximate tokens per message (rough estimate)
    // System message: ~500 tokens, User message: ~50 tokens, Assistant: ~100 tokens, Tool result: ~200 tokens
    private static final int ESTIMATED_SYSTEM_TOKENS = 500;
    private static final int ESTIMATED_USER_TOKENS = 50;
    private static final int ESTIMATED_ASSISTANT_TOKENS = 100;
    private static final int ESTIMATED_TOOL_TOKENS = 200;
    
    private int getMaxContextTokens() {
        return aiProperties.getMaxContextTokens() != null 
            ? aiProperties.getMaxContextTokens() 
            : 2000; // Default 2000 tokens
    }
    
    // Keep at least last N messages for context
    private static final int MIN_RECENT_MESSAGES = 5;

    /**
     * Trim conversation history to fit within token budget.
     *
     * <p>Strategy:</p>
     * <ul>
     *   <li>Keep system message (always)</li>
     *   <li>Keep recent messages (last N)</li>
     *   <li>Remove or summarize old messages if needed</li>
     * </ul>
     *
     * @param history full conversation history
     * @return trimmed history within token limit
     */
    public List<Map<String, Object>> trim(List<Map<String, Object>> history) {
        if (history == null || history.isEmpty()) {
            return new ArrayList<>();
        }

        int maxTokens = getMaxContextTokens();
        int estimatedTokens = estimateTokens(history);
        
        if (estimatedTokens <= maxTokens) {
            log.debug("History within token limit: {} tokens", estimatedTokens);
            return new ArrayList<>(history); // No trimming needed
        }

        log.info("Trimming history: {} tokens -> target: {} tokens", estimatedTokens, maxTokens);

        List<Map<String, Object>> trimmed = new ArrayList<>();
        
        // Step 1: Always keep system message (first one)
        Map<String, Object> systemMsg = history.stream()
            .filter(msg -> "system".equals(msg.get("role")))
            .findFirst()
            .orElse(null);
        
        if (systemMsg != null) {
            trimmed.add(systemMsg);
        }

        // Step 2: Keep recent messages (last N)
        List<Map<String, Object>> recentMessages = history.stream()
            .filter(msg -> !"system".equals(msg.get("role")))
            .skip(Math.max(0, history.size() - MIN_RECENT_MESSAGES - 1))
            .toList();
        
        trimmed.addAll(recentMessages);

        // Step 3: Estimate tokens after keeping system + recent
        int maxTokensLimit = getMaxContextTokens();
        int newEstimatedTokens = estimateTokens(trimmed);
        
        // If still over limit, remove oldest non-system messages
        if (newEstimatedTokens > maxTokensLimit) {
            // Remove oldest messages until within limit
            trimmed = aggressiveTrim(trimmed, maxTokensLimit);
        }

        log.info("Trimmed history: {} messages -> {} messages, ~{} tokens", 
            history.size(), trimmed.size(), estimateTokens(trimmed));

        return trimmed;
    }

    /**
     * Aggressively trim to fit token limit by removing oldest messages.
     */
    private List<Map<String, Object>> aggressiveTrim(List<Map<String, Object>> messages, int maxTokens) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        // Keep system message
        Map<String, Object> systemMsg = messages.stream()
            .filter(msg -> "system".equals(msg.get("role")))
            .findFirst()
            .orElse(null);
        
        if (systemMsg != null) {
            result.add(systemMsg);
        }

        // Add messages from end (most recent first) until token limit
        int currentTokens = estimateTokens(result);
        int systemTokens = currentTokens;

        // Add recent messages in reverse order (newest first)
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> msg = messages.get(i);
            if ("system".equals(msg.get("role"))) {
                continue; // Already added
            }

            int msgTokens = estimateMessageTokens(msg);
            if (currentTokens + msgTokens <= maxTokens) {
                result.add(1, msg); // Insert after system message
                currentTokens += msgTokens;
            } else {
                break; // Can't fit more
            }
        }

        return result;
    }

    /**
     * Estimate total tokens in conversation history.
     */
    private int estimateTokens(List<Map<String, Object>> history) {
        return history.stream()
            .mapToInt(this::estimateMessageTokens)
            .sum();
    }

    /**
     * Estimate tokens for a single message.
     */
    private int estimateMessageTokens(Map<String, Object> message) {
        String role = (String) message.get("role");
        Object content = message.get("content");
        
        int contentTokens = 0;
        if (content instanceof String) {
            // Rough estimate: ~4 characters per token
            contentTokens = ((String) content).length() / 4;
        }
        
        // Add role overhead
        return switch (role) {
            case "system" -> ESTIMATED_SYSTEM_TOKENS + contentTokens;
            case "user" -> ESTIMATED_USER_TOKENS + contentTokens;
            case "assistant" -> ESTIMATED_ASSISTANT_TOKENS + contentTokens;
            case "tool" -> ESTIMATED_TOOL_TOKENS + contentTokens;
            default -> contentTokens;
        };
    }
}

