package com.fabricmanagement.common.platform.ai.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conversation Store - Manages conversation history for multi-turn conversations.
 *
 * <p>Stores conversation messages in-memory (can be replaced with database later).</p>
 */
@Component
@Slf4j
public class ConversationStore {

    // In-memory storage: conversationId -> List of messages
    private final Map<UUID, List<Map<String, Object>>> conversations = new ConcurrentHashMap<>();
    
    // Max messages per conversation to prevent memory issues
    private static final int MAX_MESSAGES_PER_CONVERSATION = 50;

    /**
     * Get conversation history by ID.
     *
     * @param conversationId conversation ID
     * @return list of messages (empty if not found)
     */
    public List<Map<String, Object>> getHistory(UUID conversationId) {
        if (conversationId == null) {
            return Collections.emptyList();
        }
        
        List<Map<String, Object>> history = conversations.get(conversationId);
        if (history == null) {
            return Collections.emptyList();
        }
        
        // Return a copy to prevent external modification
        return new ArrayList<>(history);
    }

    /**
     * Add message to conversation history.
     *
     * @param conversationId conversation ID (null = no history)
     * @param role message role (user, assistant, system)
     * @param content message content
     * @param toolCalls optional tool calls (for assistant messages)
     */
    public UUID addMessage(UUID conversationId, String role, String content, List<Map<String, Object>> toolCalls) {
        UUID id = conversationId != null ? conversationId : UUID.randomUUID();
        
        conversations.computeIfAbsent(id, k -> new ArrayList<>());
        List<Map<String, Object>> messages = conversations.get(id);
        
        Map<String, Object> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        
        if (toolCalls != null && !toolCalls.isEmpty()) {
            message.put("tool_calls", toolCalls);
        }
        
        messages.add(message);
        
        // Limit conversation size
        if (messages.size() > MAX_MESSAGES_PER_CONVERSATION) {
            // Keep system message (first) and last N messages
            List<Map<String, Object>> systemMsgs = messages.stream()
                .filter(m -> "system".equals(m.get("role")))
                .toList();
            
            int keepSize = MAX_MESSAGES_PER_CONVERSATION - 10; // Keep last 40
            List<Map<String, Object>> recent = messages.subList(
                Math.max(messages.size() - keepSize, 0),
                messages.size()
            );
            
            messages.clear();
            messages.addAll(systemMsgs);
            messages.addAll(recent);
            
            log.debug("Trimmed conversation {} to {} messages", id, messages.size());
        }
        
        return id;
    }

    /**
     * Clear conversation history.
     *
     * @param conversationId conversation ID
     */
    public void clearHistory(UUID conversationId) {
        if (conversationId != null) {
            conversations.remove(conversationId);
            log.debug("Cleared conversation history: {}", conversationId);
        }
    }
}

