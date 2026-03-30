package com.fabricmanagement.platform.ai.app;

import com.fabricmanagement.common.infrastructure.ai.AIToolProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Registry that collects all domain-specific AI tool providers. Decouples AIFunctionCaller from
 * direct module dependencies.
 *
 * <p>Handles tool discovery, results caching (60s TTL), and execution orchestration.
 */
@Component
@Slf4j
public class AIToolRegistry {

  private static final long CACHE_TTL_MS = 60_000;
  private final Map<String, AIToolProvider> toolMap = new HashMap<>();
  private final Map<String, CacheEntry> functionResultCache = new ConcurrentHashMap<>();

  private record CacheEntry(String result, long expiresAt) {
    boolean isExpired() {
      return System.currentTimeMillis() > expiresAt;
    }
  }

  public AIToolRegistry(List<AIToolProvider> providers) {
    for (AIToolProvider provider : providers) {
      for (String toolName : provider.getSupportedTools()) {
        if (toolMap.containsKey(toolName)) {
          log.warn("Duplicate AI tool registered: {}. Overwriting.", toolName);
        }
        toolMap.put(toolName, provider);
      }
    }
    log.info("Registered {} AI tools from {} providers", toolMap.size(), providers.size());
  }

  public Set<String> getRegisteredTools() {
    return toolMap.keySet();
  }

  /**
   * Execute function call from AI with result caching.
   *
   * @param tenantId current tenant ID
   * @param toolName name of the tool to execute
   * @param parameters parameters for the tool
   * @return execution result string
   */
  public String execute(UUID tenantId, String toolName, Map<String, Object> parameters) {
    AIToolProvider provider = toolMap.get(toolName);
    if (provider == null) {
      throw new IllegalArgumentException("Unknown AI tool: " + toolName);
    }

    // ✅ Performance: Check cache first (prevents redundant DB queries)
    String cacheKey = buildCacheKey(toolName, tenantId, parameters);
    CacheEntry cached = functionResultCache.get(cacheKey);
    if (cached != null && !cached.isExpired()) {
      log.debug("AI Tool Cache HIT: toolName={}, tenantId={}", toolName, tenantId);
      return cached.result();
    }

    // Execute tool
    String result = provider.execute(tenantId, toolName, parameters);

    // ✅ Performance: Cache result (60 seconds TTL)
    // Note: create_* functions are NOT cached (they modify state)
    if (!toolName.startsWith("create_")) {
      long expiresAt = System.currentTimeMillis() + CACHE_TTL_MS;
      functionResultCache.put(cacheKey, new CacheEntry(result, expiresAt));
    }

    // Occasional cleanup of expired entries
    if (functionResultCache.size() % 20 == 0) {
      functionResultCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    return result;
  }

  public boolean hasTool(String toolName) {
    return toolMap.containsKey(toolName);
  }

  /** Build cache key for tool execution result. */
  private String buildCacheKey(String toolName, UUID tenantId, Map<String, Object> parameters) {
    String normalizedParams =
        parameters.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> e.getKey() + "=" + normalizeParameterValue(e.getValue()))
            .collect(Collectors.joining("&"));

    return String.format("%s:%s:%s", toolName, tenantId, normalizedParams);
  }

  /** Normalize parameter value for cache key. */
  private String normalizeParameterValue(Object value) {
    if (value == null) return "null";
    String str = value.toString().toLowerCase().trim();
    return str.length() > 100 ? str.substring(0, 100) : str;
  }
}
