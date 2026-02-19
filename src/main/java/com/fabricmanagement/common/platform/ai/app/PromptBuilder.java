package com.fabricmanagement.common.platform.ai.app;

import com.fabricmanagement.common.platform.user.dto.UserDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Prompt Builder - Constructs LLM prompts with system message and user context.
 *
 * <p>Builds complete prompt messages by combining:
 *
 * <ul>
 *   <li>System prompt (from SystemPrompts) - CACHED for performance
 *   <li>User context (tenant, role, department)
 *   <li>User preferences (learned behavior)
 *   <li>User message
 * </ul>
 *
 * <p><b>Token Optimization:</b> System prompt is cached to avoid rebuilding on every request.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PromptBuilder {

  // Cache base system prompt (without user context) to reduce token usage
  private static volatile String cachedBasePrompt = null;
  private static final Object cacheLock = new Object();

  /**
   * Build complete prompt message list for LLM.
   *
   * <p><b>Token Optimization:</b> System prompt is cached to avoid rebuilding.
   *
   * @param userMessage the user's message
   * @param user user context (optional, for personalization)
   * @return list of messages in LLM format
   */
  public List<Map<String, String>> buildPrompt(String userMessage, UserDto user) {
    List<Map<String, String>> messages = new ArrayList<>();

    // System message with user context (optimized: cache base prompt)
    SystemPrompts.UserContext context = user != null ? SystemPrompts.UserContext.of(user) : null;
    String systemPrompt = getCachedSystemPrompt(context);

    messages.add(Map.of("role", "system", "content", systemPrompt));

    // User message
    messages.add(Map.of("role", "user", "content", userMessage));

    log.debug("Built prompt with {} messages (system + user)", messages.size());

    return messages;
  }

  /**
   * Get cached system prompt to reduce token usage.
   *
   * <p>Base prompt (without user context) is cached. User-specific parts are appended dynamically.
   */
  private String getCachedSystemPrompt(SystemPrompts.UserContext context) {
    // Build base prompt once (without user context)
    if (cachedBasePrompt == null) {
      synchronized (cacheLock) {
        if (cachedBasePrompt == null) {
          cachedBasePrompt = SystemPrompts.fabricAIPrompt(null);
          log.info("Cached base system prompt (size: ~{} chars)", cachedBasePrompt.length());
        }
      }
    }

    // If no user context, return cached prompt directly
    if (context == null) {
      return cachedBasePrompt;
    }

    // User context is small, append it to cached base
    String userContextPart = buildUserContextPart(context);
    if (userContextPart != null && !userContextPart.isBlank()) {
      return cachedBasePrompt + "\n\n" + userContextPart;
    }

    return cachedBasePrompt;
  }

  /** Build user context part of system prompt (lightweight, only when needed). */
  private String buildUserContextPart(SystemPrompts.UserContext context) {
    if (context == null || context.getUser() == null) {
      return null;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("## User Context:\n\n");
    sb.append("Current user: ").append(context.getUser().getDisplayName()).append("\n");

    if (context.getUser().getOrganizationId() != null) {
      sb.append("Organization ID: ").append(context.getUser().getOrganizationId()).append("\n");
    }

    if (context.getPreferences() != null) {
      var prefs = context.getPreferences();
      if (prefs.getLanguageConfidence() > 0.5) {
        sb.append("Preferred language: ").append(prefs.getPreferredLanguage()).append("\n");
      }
    }

    return sb.toString();
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
      String userMessage, List<Map<String, String>> conversationHistory, UserDto user) {
    List<Map<String, String>> messages = new ArrayList<>();

    // System message (cached)
    SystemPrompts.UserContext context = user != null ? SystemPrompts.UserContext.of(user) : null;
    String systemPrompt = getCachedSystemPrompt(context);
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
