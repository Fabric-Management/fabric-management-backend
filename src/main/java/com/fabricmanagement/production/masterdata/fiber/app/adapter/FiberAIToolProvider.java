package com.fabricmanagement.production.masterdata.fiber.app.adapter;

import com.fabricmanagement.common.infrastructure.ai.AIQueryNormalizer;
import com.fabricmanagement.common.infrastructure.ai.AIToolProvider;
import com.fabricmanagement.production.masterdata.fiber.api.facade.FiberFacade;
import com.fabricmanagement.production.masterdata.fiber.dto.CreateFiberRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberCategoryDto;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI Tool Provider for Fiber-specific operations. Moves fiber tools out of platform/ai to the
 * production/fiber domain.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FiberAIToolProvider implements AIToolProvider {

  private final FiberFacade fiberFacade;
  private final AIQueryNormalizer queryNormalizer;

  private static final int AI_SEARCH_LIMIT = 500;

  @Override
  public Set<String> getSupportedTools() {
    return Set.of("search_fibers", "get_fiber_info", "list_fiber_categories", "create_fiber");
  }

  @Override
  public String execute(UUID tenantId, String toolName, Map<String, Object> parameters) {
    return switch (toolName) {
      case "search_fibers" -> searchFibers(tenantId, parameters);
      case "get_fiber_info" -> getFiberInfo(tenantId, parameters);
      case "list_fiber_categories" -> listFiberCategories();
      case "create_fiber" -> createFiber(tenantId, parameters);
      default -> throw new IllegalArgumentException("Unknown AI tool: " + toolName);
    };
  }

  /** Search fibers by name or other criteria. Supports Turkish-to-English translation. */
  private String searchFibers(UUID tenantId, Map<String, Object> parameters) {
    String query = (String) parameters.getOrDefault("query", "");

    if (query.isBlank()) {
      long count = fiberFacade.findAll().size();
      return String.format("Total fiber count: %d", count);
    }

    // Turkish-to-English translation via common normalizer
    String normalizedQuery = queryNormalizer.normalizeFiberQuery(query);
    String lowerQuery = query.toLowerCase();
    String lowerNormalized = normalizedQuery.toLowerCase();

    // Clean query
    String cleanedQuery =
        lowerQuery.replace("elyaf", "").replace("fiber", "").replace("materyal", "").trim();

    // Use normalized query for DB search to leverage translations
    String searchQuery = normalizedQuery;
    if (searchQuery.isBlank()) {
      searchQuery = query;
    }

    // Use extended facade method for optimized search (Tenant + System)
    List<FiberDto> allMatching = fiberFacade.findByNameContaining(searchQuery);

    // Final filtering and filtering for safety (parity with previous logic)
    List<FiberDto> matching =
        allMatching.stream()
            .filter(
                f -> {
                  if (f.getFiberName() == null) return false;
                  String fiberName = f.getFiberName().toLowerCase();
                  return fiberName.contains(lowerQuery)
                      || fiberName.contains(lowerNormalized)
                      || fiberName.contains(cleanedQuery);
                })
            .toList();

    if (matching.isEmpty()) {
      if (!normalizedQuery.equalsIgnoreCase(query)) {
        return String.format("No fiber found for '%s' (or '%s').", query, normalizedQuery);
      }
      return String.format("No fiber found for '%s'.", query);
    }

    // Performance: Limit to top 5 results for AI response
    int limit = Math.min(5, matching.size());
    StringBuilder result = new StringBuilder();
    result.append(String.format("✅ Found %d fiber(s):\n", matching.size()));
    for (int i = 0; i < limit; i++) {
      FiberDto f = matching.get(i);
      result.append(String.format("- %s (%s)\n", f.getFiberName(), f.getUid()));
    }
    if (matching.size() > limit) {
      result.append(String.format("(Showing top %d)\n", limit));
    }
    return result.toString();
  }

  /** Get detailed information about a specific fiber by ID or Name. */
  private String getFiberInfo(UUID tenantId, Map<String, Object> parameters) {
    String fiberId = (String) parameters.get("fiberId");
    String fiberName = (String) parameters.get("fiberName");

    if ((fiberId == null || fiberId.isBlank()) && (fiberName == null || fiberName.isBlank())) {
      return "❌ Either fiberId or fiberName is required.";
    }

    Optional<FiberDto> fiber = Optional.empty();

    if (fiberId != null && !fiberId.isBlank()) {
      try {
        fiber = fiberFacade.findById(UUID.fromString(fiberId));
      } catch (IllegalArgumentException e) {
        // Not a UUID, try searching as UID string
        fiber =
            fiberFacade.findAll().stream()
                .filter(f -> fiberId.equalsIgnoreCase(f.getUid()))
                .findFirst();
      }
    }

    if (fiber.isEmpty() && fiberName != null && !fiberName.isBlank()) {
      List<FiberDto> fibers =
          fiberFacade.findAll().stream()
              .limit(AI_SEARCH_LIMIT)
              .filter(
                  f ->
                      f.getFiberName() != null
                          && f.getFiberName().toLowerCase().contains(fiberName.toLowerCase()))
              .toList();

      if (fibers.isEmpty()) {
        return String.format(
            "Fiber '%s' not found. No such fiber is defined in the system.", fiberName);
      }

      if (fibers.size() > 1) {
        StringBuilder result =
            new StringBuilder(
                String.format("Found %d fiber(s) matching '%s':\n\n", fibers.size(), fiberName));
        for (FiberDto f : fibers) {
          result.append(String.format("- %s (%s)\n", f.getFiberName(), f.getStatus()));
        }
        result.append("\nPlease specify which fiber to check (UID or full name).");
        return result.toString();
      }

      fiber = Optional.of(fibers.get(0));
    }

    if (fiber.isEmpty()) {
      return String.format("Fiber not found (ID: %s)", fiberId);
    }

    FiberDto f = fiber.get();
    StringBuilder info = new StringBuilder();
    info.append(String.format("📊 Fiber details: %s\n\n", f.getFiberName()));
    info.append(String.format("UID: %s\n", f.getUid()));
    info.append(String.format("Status: %s\n", f.getStatus() != null ? f.getStatus() : "N/A"));

    if (f.getComposition() != null && !f.getComposition().isEmpty()) {
      info.append("\nComposition (Blended Fiber):\n");
      f.getComposition()
          .forEach(
              (baseFiberId, percentage) -> {
                Optional<FiberDto> baseFiber = fiberFacade.findById(baseFiberId);
                String baseName = baseFiber.map(FiberDto::getFiberName).orElse("Unknown");
                info.append(String.format("  - %s: %.2f%%\n", baseName, percentage));
              });
    }

    if (f.getRemarks() != null && !f.getRemarks().isBlank()) {
      info.append(String.format("\nRemarks: %s\n", f.getRemarks()));
    }

    return info.toString();
  }

  /** List all active fiber categories. */
  private String listFiberCategories() {
    List<FiberCategoryDto> categories = fiberFacade.listActiveCategories();

    if (categories.isEmpty()) {
      return "No fiber categories found in the system.";
    }

    StringBuilder result = new StringBuilder("Available Fiber Categories:\n\n");
    for (FiberCategoryDto category : categories) {
      result.append(
          String.format(
              "- %s (Code: %s, ID: %s)\n",
              category.getCategoryName(), category.getCategoryCode(), category.getId()));
      if (category.getDescription() != null && !category.getDescription().isBlank()) {
        result.append(String.format("  Description: %s\n", category.getDescription()));
      }
    }

    return result.toString();
  }

  /** Create a new fiber. */
  private String createFiber(UUID tenantId, Map<String, Object> parameters) {
    try {
      String fiberName = (String) parameters.get("fiberName");
      String fiberCategoryIdStr = (String) parameters.get("fiberCategoryId");
      String productIdStr = (String) parameters.get("productId");
      String unit = (String) parameters.get("unit");

      if ((productIdStr == null || productIdStr.isBlank()) && (unit == null || unit.isBlank())) {
        return "❌ Either productId or unit is required.\n\n"
            + "Option 1: Provide productId to use existing Product\n"
            + "Option 2: Provide unit (e.g. 'kg') to auto-create Product with type=FIBER";
      }

      if (fiberCategoryIdStr == null || fiberCategoryIdStr.isBlank()) {
        return "❌ Fiber Category ID is required.";
      }

      String fiberIsoCodeIdStr = (String) parameters.get("fiberIsoCodeId");
      if (fiberIsoCodeIdStr == null || fiberIsoCodeIdStr.isBlank()) {
        return "❌ Fiber ISO Code ID is required.";
      }

      if (fiberName == null || fiberName.isBlank()) {
        return "❌ Fiber Name is required.";
      }

      UUID productId = null;
      UUID fiberCategoryId;
      UUID fiberIsoCodeId;
      try {
        if (productIdStr != null && !productIdStr.isBlank()) {
          productId = UUID.fromString(productIdStr);
          // Note: existence check for product is handled inside FiberFacade/FiberService
          // consistency
        }
        fiberCategoryId = UUID.fromString(fiberCategoryIdStr);
        fiberIsoCodeId = UUID.fromString(fiberIsoCodeIdStr);
      } catch (IllegalArgumentException e) {
        return "❌ Invalid UUID format for productId, fiberCategoryId, or fiberIsoCodeId.";
      }

      String remarks = (String) parameters.get("remarks");

      // Composition for blended fibers (optional)
      Map<UUID, BigDecimal> composition = null;

      CreateFiberRequest request =
          CreateFiberRequest.builder()
              .productId(productId)
              .unit(unit)
              .fiberCategoryId(fiberCategoryId)
              .fiberIsoCodeId(fiberIsoCodeId)
              .fiberName(fiberName)
              .composition(composition)
              .remarks(remarks)
              .build();

      FiberDto created = fiberFacade.createFiber(request);

      return String.format(
          "✅ Fiber created successfully!\n\n"
              + "Fiber ID: %s\n"
              + "UID: %s\n"
              + "Name: %s\n"
              + "Status: %s",
          created.getId(),
          created.getUid(),
          created.getFiberName(),
          created.getStatus() != null ? created.getStatus() : "N/A");
    } catch (Exception e) {
      log.error("Error creating fiber", e);
      return "❌ Error creating fiber: " + e.getMessage();
    }
  }
}
