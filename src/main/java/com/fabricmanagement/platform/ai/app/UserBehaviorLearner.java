package com.fabricmanagement.platform.ai.app;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * User Behavior Learner - Tracks and learns user preferences and patterns.
 *
 * <p>Simple learning mechanism (no ML): tracks language, common queries, function usage.
 *
 * <p>MANIFESTO: KISS - Simple counters and patterns, no complex ML
 */
@Component
@Slf4j
public class UserBehaviorLearner {

  // User preferences: userId -> UserPreferences
  private final Map<UUID, UserPreferences> userPreferences = new ConcurrentHashMap<>();

  // Function usage stats: userId -> functionName -> count
  private final Map<UUID, Map<String, Integer>> functionUsage = new ConcurrentHashMap<>();

  // Common query patterns: userId -> queryPattern -> count
  private final Map<UUID, Map<String, Integer>> queryPatterns = new ConcurrentHashMap<>();

  /**
   * Learn from user interaction.
   *
   * @param userId user ID
   * @param query user query
   * @param functionName function called (if any)
   * @param responseLanguage detected response language
   */
  public void learn(UUID userId, String query, String functionName, String responseLanguage) {
    if (userId == null) {
      return; // Skip anonymous users
    }

    // Track preferences
    UserPreferences prefs = userPreferences.computeIfAbsent(userId, k -> new UserPreferences());
    if (responseLanguage != null) {
      prefs.learnLanguage(responseLanguage);
    }

    // Track function usage
    if (functionName != null) {
      functionUsage
          .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
          .merge(functionName, 1, Integer::sum);
    }

    // Track query patterns (normalize for pattern matching)
    String pattern = extractPattern(query);
    if (pattern != null) {
      queryPatterns
          .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
          .merge(pattern, 1, Integer::sum);
    }

    log.debug(
        "Learned from user {}: language={}, function={}, pattern={}",
        userId,
        responseLanguage,
        functionName,
        pattern);
  }

  /** Get user preferences (language, style, etc.). */
  public UserPreferences getPreferences(UUID userId) {
    return userPreferences.getOrDefault(userId, new UserPreferences());
  }

  /** Get favorite functions for a user (most used). */
  public List<String> getFavoriteFunctions(UUID userId, int limit) {
    Map<String, Integer> usage = functionUsage.get(userId);
    if (usage == null || usage.isEmpty()) {
      return List.of();
    }

    return usage.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(limit)
        .map(Map.Entry::getKey)
        .toList();
  }

  /** Get common query patterns for a user. */
  public List<String> getCommonPatterns(UUID userId, int limit) {
    Map<String, Integer> patterns = queryPatterns.get(userId);
    if (patterns == null || patterns.isEmpty()) {
      return List.of();
    }

    return patterns.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(limit)
        .map(Map.Entry::getKey)
        .toList();
  }

  /** Extract pattern from query (normalize for learning). */
  private String extractPattern(String query) {
    if (query == null || query.isBlank()) {
      return null;
    }

    // Normalize: lowercase, remove special chars, keep structure
    String normalized =
        query
            .toLowerCase()
            .trim()
            .replaceAll("[0-9]+", "NUM") // Replace numbers with NUM
            .replaceAll("\\s+", " ");

    // Extract intent keywords
    if (normalized.contains("stok") || normalized.contains("stock")) {
      return "STOCK_QUERY";
    }
    if (normalized.contains("fiber") || normalized.contains("lif")) {
      return "FIBER_QUERY";
    }
    if (normalized.contains("material") || normalized.contains("malzeme")) {
      return "MATERIAL_QUERY";
    }
    if (normalized.contains("durum") || normalized.contains("status")) {
      return "STATUS_QUERY";
    }
    if (normalized.contains("ara") || normalized.contains("search")) {
      return "SEARCH_QUERY";
    }

    return "GENERAL_QUERY";
  }

  /** Detect language from query (simple heuristic). */
  public String detectLanguage(String query) {
    if (query == null || query.isBlank()) {
      return "en";
    }

    // Simple Turkish language detection
    // Common Turkish words/patterns
    Pattern turkishPattern =
        Pattern.compile(
            ".*(stok|fiber|malzeme|durum|ara|bul|var|yok|kaç|ne|nasıl|neden|hangi|kim|nere|gibi|ile|için|üretim|satış).*",
            Pattern.CASE_INSENSITIVE);

    if (turkishPattern.matcher(query).matches()) {
      return "tr";
    }

    return "en"; // Default to English
  }

  /** User preferences (language, style, etc.). */
  @Data
  public static class UserPreferences {
    private String preferredLanguage = "en"; // "tr" or "en"
    private int trCount = 0;
    private int enCount = 0;
    private Instant lastUpdated = Instant.now();

    /** Learn language preference. */
    void learnLanguage(String language) {
      if ("tr".equals(language)) {
        trCount++;
      } else {
        enCount++;
      }

      // Update preferred language based on majority
      if (trCount > enCount) {
        preferredLanguage = "tr";
      } else {
        preferredLanguage = "en";
      }

      lastUpdated = Instant.now();
    }

    /** Get confidence in language preference (0.0 - 1.0). */
    public double getLanguageConfidence() {
      int total = trCount + enCount;
      if (total == 0) {
        return 0.0;
      }
      int majority = Math.max(trCount, enCount);
      return (double) majority / total;
    }
  }

  /** Get stats for monitoring. */
  public Map<String, Object> getStats() {
    return Map.of(
        "usersTracked", userPreferences.size(),
        "functionsTracked", functionUsage.size(),
        "patternsTracked", queryPatterns.size());
  }
}
