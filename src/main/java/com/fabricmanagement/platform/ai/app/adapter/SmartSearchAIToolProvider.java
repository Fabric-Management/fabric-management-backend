package com.fabricmanagement.platform.ai.app.adapter;

import com.fabricmanagement.common.infrastructure.ai.AIToolProvider;
import com.fabricmanagement.platform.ai.app.AIToolRegistry;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * AI Tool Provider for Smart Search operations. This provider orchestrates searches across
 * different domain modules (Fiber, Product) by detecting the intended entity type from the query
 * string.
 *
 * <p>Use {@link ObjectProvider} for {@link AIToolRegistry} to prevent circular dependencies.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmartSearchAIToolProvider implements AIToolProvider {

  private final ObjectProvider<AIToolRegistry> toolRegistryProvider;

  @Override
  public Set<String> getSupportedTools() {
    return Set.of("smart_search");
  }

  @Override
  public String execute(UUID tenantId, String toolName, Map<String, Object> parameters) {
    if ("smart_search".equals(toolName)) {
      return smartSearch(tenantId, parameters);
    }
    throw new IllegalArgumentException("Unknown AI tool: " + toolName);
  }

  private AIToolRegistry registry() {
    return toolRegistryProvider.getObject();
  }

  /** Orchestrates a search across different domain modules based on the query content. */
  private String smartSearch(UUID tenantId, Map<String, Object> parameters) {
    String query = (String) parameters.getOrDefault("query", "");

    if (query.isBlank()) {
      return "💡 Please provide a search term (e.g., 'cotton', 'denim fabric', '30/1 yarn').";
    }

    EntityType entityType = detectEntityType(query);
    log.debug("Smart search detected entity type: {} for query: {}", entityType, query);

    StringBuilder result = new StringBuilder();
    result.append(String.format("🔍 Smart searching for %s...\n\n", entityType.displayName));

    boolean found = false;

    // Route based on detected type
    switch (entityType) {
      case FIBER -> {
        String fiberResult = registry().execute(tenantId, "search_fibers", parameters);
        result.append(fiberResult);
        found = !fiberResult.contains("No results found");
      }
      case YARN -> {
        Map<String, Object> yarnParams = new java.util.HashMap<>(parameters);
        yarnParams.put("type", "YARN");
        String yarnResult = registry().execute(tenantId, "search_products", yarnParams);
        result.append(yarnResult);
        found = !yarnResult.contains("No products found");
      }
      case FABRIC -> {
        Map<String, Object> fabricParams = new java.util.HashMap<>(parameters);
        fabricParams.put("type", "FABRIC");
        String fabricResult = registry().execute(tenantId, "search_products", fabricParams);
        result.append(fabricResult);
        found = !fabricResult.contains("No products found");
      }
      case UNKNOWN -> {
        // Search both domain modules mapping
        String fiberRes = registry().execute(tenantId, "search_fibers", parameters);
        String productRes = registry().execute(tenantId, "search_products", parameters);

        if (!fiberRes.contains("No results found")) {
          found = true;
          result.append("✅ Fiber(s):\n").append(fiberRes).append("\n");
        }
        if (!productRes.contains("No products found")) {
          found = true;
          result.append("\n✅ Product(s):\n").append(productRes);
        }
      }
    }

    if (!found) {
      result.setLength(0); // Clear progress
      result.append(String.format("❌ No results found for '%s'.\n\n", query));
      result.append("💡 Tips:\n");
      result.append("- 'cotton' → search FIBER\n");
      result.append("- 'cotton yarn' → search YARN\n");
      result.append("- 'gabardine' → search FABRIC\n");
    }

    return result.toString();
  }

  /**
   * Detect entity type from query string using pattern matching logic ported from AIFunctionCaller.
   */
  private EntityType detectEntityType(String query) {
    if (query == null || query.isBlank()) {
      return EntityType.UNKNOWN;
    }

    String lowerQuery = query.toLowerCase().trim();

    // YARN indicators
    if (lowerQuery.contains("iplik")
        || lowerQuery.contains("yarn")
        || lowerQuery.contains("ipliği")
        || lowerQuery.contains("ipliğin")) {
      return EntityType.YARN;
    }

    // FABRIC indicators
    if (lowerQuery.contains("kumaş")
        || lowerQuery.contains("fabric")
        || lowerQuery.contains("gabardin")
        || lowerQuery.contains("poplin")
        || lowerQuery.contains("denim")
        || lowerQuery.contains("twill")
        || lowerQuery.contains("jersey")
        || lowerQuery.contains("rib")
        || lowerQuery.matches(".*\\d+/\\d+.*")
        || lowerQuery.contains("gsm")) {
      return EntityType.FABRIC;
    }

    // FIBER indicators
    String fiberPattern =
        ".*\\b(pamuk|cotton|polyester|poliester|yün|wool|keten|linen|ipek|silk|"
            + "akrilik|acrylic|naylon|nylon|viscose|viskoz|viskon|elastan|elastane)\\b.*";
    if (lowerQuery.matches(fiberPattern)
        && !lowerQuery.contains("iplik")
        && !lowerQuery.contains("yarn")
        && !lowerQuery.contains("kumaş")
        && !lowerQuery.contains("fabric")
        && !lowerQuery.contains("elyaf")
        && !lowerQuery.contains("fiber")) {
      return EntityType.FIBER;
    }

    return EntityType.UNKNOWN;
  }

  /** Entity type enum for smart search orchestration. */
  private enum EntityType {
    FIBER("Fiber (Cotton, Polyester, etc.)"),
    YARN("Yarn"),
    FABRIC("Fabric"),
    UNKNOWN("Unknown (wide search)");

    private final String displayName;

    EntityType(String displayName) {
      this.displayName = displayName;
    }
  }
}
