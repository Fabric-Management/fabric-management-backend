package com.fabricmanagement.common.platform.ai.app;

import com.fabricmanagement.common.platform.user.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prompt Builder - Constructs LLM prompts with system message and user context.
 *
 * <p>Builds complete prompt messages by combining:
 * <ul>
 *   <li>System prompt (from SystemPrompts)</li>
 *   <li>User context (tenant, role, department)</li>
 *   <li>User message</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PromptBuilder {

    /**
     * Build complete prompt message list for LLM.
     *
     * @param userMessage the user's message
     * @param user user context (optional, for personalization)
     * @return list of messages in LLM format
     */
    public List<Map<String, String>> buildPrompt(String userMessage, UserDto user) {
        List<Map<String, String>> messages = new ArrayList<>();

        // System message with user context
        SystemPrompts.UserContext context = user != null 
            ? SystemPrompts.UserContext.of(user) 
            : null;
        String systemPrompt = SystemPrompts.fabricAIPrompt(context);

        messages.add(Map.of(
            "role", "system",
            "content", systemPrompt
        ));

        // User message
        messages.add(Map.of(
            "role", "user",
            "content", userMessage
        ));

        log.debug("Built prompt with {} messages (system + user)", messages.size());

        return messages;
    }

    /**
     * Build prompt with conversation history (for multi-turn conversations).
     *
     * @param userMessage current user message
     * @param conversationHistory previous messages
     * @param user user context
     * @return complete message list with history
     */
    public List<Map<String, String>> buildPromptWithHistory(
            String userMessage,
            List<Map<String, String>> conversationHistory,
            UserDto user) {
        List<Map<String, String>> messages = new ArrayList<>();

        // System message
        SystemPrompts.UserContext context = user != null 
            ? SystemPrompts.UserContext.of(user) 
            : null;
        String systemPrompt = SystemPrompts.fabricAIPrompt(context);
        messages.add(Map.of("role", "system", "content", systemPrompt));

        // Conversation history
        if (conversationHistory != null) {
            messages.addAll(conversationHistory);
        }

        // Current user message
        messages.add(Map.of("role", "user", "content", userMessage));

        log.debug("Built prompt with history: {} total messages", messages.size());

        return messages;
    }
}

