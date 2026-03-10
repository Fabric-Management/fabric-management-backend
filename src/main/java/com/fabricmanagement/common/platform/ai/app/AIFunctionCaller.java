package com.fabricmanagement.common.platform.ai.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.masterdata.fiber.api.facade.FiberFacade;
import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.production.masterdata.fiber.dto.CreateFiberRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberDto;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberCategoryRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
import com.fabricmanagement.production.masterdata.material.api.facade.MaterialFacade;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import com.fabricmanagement.production.masterdata.material.dto.CreateMaterialRequest;
import com.fabricmanagement.production.masterdata.material.dto.MaterialDto;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI Function Caller - Executes backend actions for FabricAI.
 *
 * <p>Handles function calls from AI assistant and executes corresponding backend operations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AIFunctionCaller {

  /**
   * Maximum number of fibers/materials to load for AI search operations.
   *
   * <p><b>Performance:</b> Limits memory usage for AI searches. Even if a tenant has thousands of
   * fibers/materials, AI searches only need to check the most relevant ones. This prevents memory
   * overflow and improves response times.
   */
  private static final int AI_SEARCH_LIMIT = 500;

  private final MaterialFacade materialFacade;
  private final FiberFacade fiberFacade;
  private final FiberRepository fiberRepository;
  private final FiberCategoryRepository fiberCategoryRepository;

  /**
   * Function call result cache - reduces redundant database queries and token costs.
   *
   * <p><b>Cache Key:</b> functionName:tenantId:normalizedParams
   *
   * <p><b>TTL:</b> 60 seconds (short TTL for data freshness)
   *
   * <p><b>Purpose:</b> Prevent same function call from executing multiple times in same
   * conversation
   */
  private final Map<String, CacheEntry> functionResultCache = new ConcurrentHashMap<>();

  private record CacheEntry(String result, long expiresAt) {
    boolean isExpired() {
      return System.currentTimeMillis() > expiresAt;
    }
  }

  /**
   * Execute function call from AI with result caching.
   *
   * <p><b>Performance:</b> Caches function results for 60 seconds to prevent redundant database
   * queries and reduce token costs.
   *
   * @param functionName function name (e.g., "check_material_stock")
   * @param parameters function parameters
   * @return function result as string (will be sent back to AI)
   */
  public String executeFunction(String functionName, Map<String, Object> parameters) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    // ✅ Performance: Check cache first (prevents redundant DB queries)
    String cacheKey = buildFunctionCacheKey(functionName, tenantId, parameters);
    CacheEntry cached = functionResultCache.get(cacheKey);
    if (cached != null && !cached.isExpired()) {
      log.debug("Function call cache HIT: functionName={}, tenantId={}", functionName, tenantId);
      return cached.result();
    }

    log.info(
        "Executing AI function: functionName={}, parameters={}, tenantId={}",
        functionName,
        parameters,
        tenantId);

    String result =
        switch (functionName) {
          case "check_material_stock" -> checkMaterialStock(tenantId, parameters);
          case "search_materials" -> searchMaterials(tenantId, parameters);
          case "smart_search" -> smartSearch(tenantId, parameters);
          case "get_production_status" -> getProductionStatus(tenantId);
          case "get_fiber_info" -> getFiberInfo(tenantId, parameters);
          case "search_fibers" -> searchFibers(tenantId, parameters);
          case "list_fiber_categories" -> listFiberCategories(tenantId);
          case "create_material" -> createMaterial(tenantId, parameters);
          case "create_fiber" -> createFiber(tenantId, parameters);
          default -> "Unknown function: " + functionName;
        };

    // ✅ Performance: Cache result (60 seconds TTL)
    // Note: create_* functions are NOT cached (they modify state)
    if (!functionName.startsWith("create_")) {
      long expiresAt = System.currentTimeMillis() + 60_000; // 60 seconds
      functionResultCache.put(cacheKey, new CacheEntry(result, expiresAt));
      log.debug(
          "Function call result cached: functionName={}, cacheKey={}", functionName, cacheKey);
    }

    // Cleanup expired entries (simple cleanup)
    if (functionResultCache.size() % 20 == 0) {
      functionResultCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    return result;
  }

  /**
   * Build cache key for function call result.
   *
   * <p>Format: functionName:tenantId:normalizedParams
   */
  private String buildFunctionCacheKey(
      String functionName, UUID tenantId, Map<String, Object> parameters) {
    // Normalize parameters for cache key (sort keys, normalize values)
    String normalizedParams =
        parameters.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> e.getKey() + "=" + normalizeParameterValue(e.getValue()))
            .collect(java.util.stream.Collectors.joining("&"));

    return String.format("%s:%s:%s", functionName, tenantId, normalizedParams);
  }

  /** Normalize parameter value for cache key (lowercase, trim). */
  private String normalizeParameterValue(Object value) {
    if (value == null) return "null";
    String str = value.toString().toLowerCase().trim();
    return str.length() > 100 ? str.substring(0, 100) : str; // Limit length
  }

  /** Check material by name; directs user to Inventory module for stock details. */
  private String checkMaterialStock(UUID tenantId, Map<String, Object> parameters) {
    String materialName = (String) parameters.get("materialName");
    if (materialName == null || materialName.isBlank()) {
      return "Material name is required for stock check.";
    }

    String search = materialName.toLowerCase().trim();
    List<MaterialDto> materials =
        materialFacade.findByTenant(tenantId).stream().limit(AI_SEARCH_LIMIT).toList();
    List<MaterialDto> matching =
        materials.stream()
            .filter(
                m ->
                    m.getUid() != null && m.getUid().toLowerCase().contains(search)
                        || (m.getMaterialType() != null
                            && m.getMaterialType().toString().toLowerCase().contains(search)))
            .toList();

    if (matching.isEmpty()) {
      return String.format(
          "Material '%s' not found. No such material/product is defined in the system.\n\n"
              + "Use the Material Management module to add this material.",
          materialName);
    }

    if (matching.size() > 1) {
      StringBuilder result =
          new StringBuilder(
              String.format(
                  "Found %d materials matching '%s':\n\n", matching.size(), materialName));
      for (MaterialDto m : matching) {
        result.append(String.format("- %s (%s)\n", m.getUid(), m.getMaterialType()));
      }
      result.append("\nPlease specify which material to check.");
      return result.toString();
    }

    MaterialDto material = matching.get(0);
    return String.format(
        "Material '%s' is defined in the system.\n\n"
            + "Material details:\n"
            + "- Type: %s\n"
            + "- Unit: %s\n"
            + "- Status: %s\n\n"
            + "Use the Production / Inventory modules for stock quantity.",
        material.getUid(),
        material.getMaterialType() != null ? material.getMaterialType().toString() : "N/A",
        material.getUnit() != null ? material.getUnit() : "N/A",
        material.getIsActive() != null && material.getIsActive() ? "Active" : "Inactive");
  }

  /**
   * Smart search that automatically detects entity type from query and searches accordingly.
   *
   * <p>Entity Type Detection Rules:
   *
   * <ul>
   *   <li>"cotton" (without suffix) → FIBER
   *   <li>"cotton yarn" → YARN
   *   <li>"gabardine" / "fabric" → FABRIC
   *   <li>"yarn" → YARN
   *   <li>Technical specs (30/1, GSM) → Usually FABRIC or YARN
   * </ul>
   *
   * <p>This is more efficient than separate searches - one function call, smart detection.
   */
  private String smartSearch(UUID tenantId, Map<String, Object> parameters) {
    String query = (String) parameters.getOrDefault("query", "");

    if (query.isBlank()) {
      return "Search query is required. Examples: 'cotton', 'cotton yarn', 'gabardine'";
    }

    // Detect entity type from query
    EntityType entityType = detectEntityType(query);

    // Normalize query (Turkish → English)
    String normalizedQuery = normalizeFiberQuery(query);

    StringBuilder result = new StringBuilder();
    result.append(String.format("🔍 Searching for '%s'...\n", query));
    result.append(String.format("📌 Detected type: %s\n\n", entityType.displayName));

    boolean found = false;

    // Improved matching: remove suffixes and try multiple strategies
    String lowerQuery = query.toLowerCase();
    String lowerNormalized = normalizedQuery.toLowerCase();

    // Clean query: remove common suffixes
    String cleanedQuery =
        lowerQuery
            .replace("elyaf", "")
            .replace("elyafı", "")
            .replace("fiber", "")
            .replace("materyal", "")
            .replace("materyali", "")
            .replace("yun", "")
            .replace("yün", "")
            .replace("var mı", "")
            .replace("var mi", "")
            .trim();
    String cleanedNormalized = lowerNormalized.replace("fiber", "").replace("material", "").trim();

    // Search based on detected type
    switch (entityType) {
      case FIBER -> {
        // ✅ Performance: Use filtered query instead of findAll (reduces DB load + token usage)
        // Only load fibers matching the query, not all 75+ fibers
        String searchQuery = cleanedQuery.isBlank() ? normalizedQuery : cleanedQuery;
        if (searchQuery.isBlank()) {
          searchQuery = query; // Fallback to original query
        }

        // Tenant fibers (filtered, max 50)
        List<FiberDto> tenantFibers =
            fiberRepository
                .findByTenantIdAndFiberNameContainingIgnoreCase(tenantId, searchQuery)
                .stream()
                .limit(50)
                .map(FiberDto::from)
                .toList();

        // System tenant fibers (filtered, max 25)
        UUID systemTenantId = TenantContext.SYSTEM_TENANT_ID;
        List<FiberDto> systemFibers =
            fiberRepository
                .findByTenantIdAndFiberNameContainingIgnoreCase(systemTenantId, searchQuery)
                .stream()
                .limit(25)
                .map(FiberDto::from)
                .toList();

        // Combine both lists
        List<FiberDto> allFibers = new java.util.ArrayList<>(tenantFibers);
        allFibers.addAll(systemFibers);

        // Debug log for troubleshooting
        log.debug(
            "FIBER search - query: '{}', norm: '{}', cleaned: '{}', total: {}",
            query,
            normalizedQuery,
            cleanedQuery,
            allFibers.size());

        List<FiberDto> matching =
            allFibers.stream()
                .filter(
                    f -> {
                      if (f.getFiberName() == null) return false;
                      String fiberName = f.getFiberName().toLowerCase();

                      // Multiple matching strategies for robust search
                      boolean matches =
                          fiberName.contains(lowerQuery)
                              || fiberName.contains(lowerNormalized)
                              || fiberName.contains(cleanedQuery)
                              || fiberName.contains(cleanedNormalized)
                              || fiberName.matches(
                                  ".*\\b" + java.util.regex.Pattern.quote(cleanedQuery) + "\\b.*")
                              || fiberName.matches(
                                  ".*\\b"
                                      + java.util.regex.Pattern.quote(cleanedNormalized)
                                      + "\\b.*");

                      if (matches) {
                        log.debug(
                            "✅ Matched fiber: '{}' with query '{}' (normalized: '{}')",
                            fiberName,
                            query,
                            normalizedQuery);
                      }

                      return matches;
                    })
                .toList();

        if (!matching.isEmpty()) {
          found = true;
          // ✅ Performance: Limit to top 5 results to reduce token usage
          int limit = Math.min(5, matching.size());
          result.append(String.format("✅ %d fiber bulundu:\n", matching.size()));
          for (int i = 0; i < limit; i++) {
            FiberDto f = matching.get(i);
            result.append(String.format("- %s (%s)\n", f.getFiberName(), f.getUid()));
          }
          if (matching.size() > limit) {
            result.append(String.format("(Showing top %d of %d)\n", limit, matching.size()));
          }
        }
      }
      case YARN -> {
        // ✅ Performance: Limit material loading to prevent memory overflow
        // Search Materials with type=YARN
        List<MaterialDto> materials =
            materialFacade.findByTenant(tenantId).stream().limit(AI_SEARCH_LIMIT).toList();
        List<MaterialDto> matching =
            materials.stream()
                .filter(
                    m ->
                        m.getMaterialType() == MaterialType.YARN
                            && (m.getUid() != null
                                && (m.getUid().toLowerCase().contains(normalizedQuery.toLowerCase())
                                    || m.getUid().toLowerCase().contains(query.toLowerCase()))))
                .toList();

        if (!matching.isEmpty()) {
          found = true;
          // ✅ Performance: Limit to top 5 results
          int limit = Math.min(5, matching.size());
          result.append(String.format("✅ Found %d yarn(s):\n", matching.size()));
          for (int i = 0; i < limit; i++) {
            MaterialDto m = matching.get(i);
            result.append(String.format("- %s (%s)\n", m.getUid(), m.getMaterialType()));
          }
          if (matching.size() > limit) {
            result.append(String.format("(Showing top %d)\n", limit));
          }
        }
      }
      case FABRIC -> {
        // ✅ Performance: Limit material loading to prevent memory overflow
        // Search Materials with type=FABRIC
        List<MaterialDto> materials =
            materialFacade.findByTenant(tenantId).stream().limit(AI_SEARCH_LIMIT).toList();
        List<MaterialDto> matching =
            materials.stream()
                .filter(
                    m ->
                        m.getMaterialType() == MaterialType.FABRIC
                            && (m.getUid() != null
                                && (m.getUid().toLowerCase().contains(normalizedQuery.toLowerCase())
                                    || m.getUid().toLowerCase().contains(query.toLowerCase()))))
                .toList();

        if (!matching.isEmpty()) {
          found = true;
          // ✅ Performance: Limit to top 5 results
          int limit = Math.min(5, matching.size());
          result.append(String.format("✅ Found %d fabric(s):\n", matching.size()));
          for (int i = 0; i < limit; i++) {
            MaterialDto m = matching.get(i);
            result.append(String.format("- %s (%s)\n", m.getUid(), m.getMaterialType()));
          }
          if (matching.size() > limit) {
            result.append(String.format("(Showing top %d)\n", limit));
          }
        }
      }
      case UNKNOWN -> {
        // Try all entity types
        result.append("⚠️ Type unclear, searching all entity types...\n\n");

        // ✅ Performance: Use filtered query instead of findAll
        String searchQuery = cleanedQuery.isBlank() ? normalizedQuery : cleanedQuery;
        if (searchQuery.isBlank()) {
          searchQuery = query;
        }

        // Try FIBER (filtered, max 50)
        List<FiberDto> tenantFibers =
            fiberRepository
                .findByTenantIdAndFiberNameContainingIgnoreCase(tenantId, searchQuery)
                .stream()
                .limit(50)
                .map(FiberDto::from)
                .toList();

        // System tenant fibers (filtered, max 25)
        UUID systemTenantId = TenantContext.SYSTEM_TENANT_ID;
        List<FiberDto> systemFibers =
            fiberRepository
                .findByTenantIdAndFiberNameContainingIgnoreCase(systemTenantId, searchQuery)
                .stream()
                .limit(25)
                .map(FiberDto::from)
                .toList();
        List<FiberDto> allFibers = new java.util.ArrayList<>(tenantFibers);
        allFibers.addAll(systemFibers);

        // Use already-defined variables (lowerQuery, lowerNormalized, cleanedQuery,
        // cleanedNormalized)

        List<FiberDto> matchingFibers =
            allFibers.stream()
                .filter(
                    f -> {
                      if (f.getFiberName() == null) return false;
                      String fiberName = f.getFiberName().toLowerCase();

                      // Multiple matching strategies
                      return fiberName.contains(lowerQuery)
                          || fiberName.contains(lowerNormalized)
                          || fiberName.contains(cleanedQuery)
                          || fiberName.contains(cleanedNormalized)
                          || fiberName.matches(
                              ".*\\b" + java.util.regex.Pattern.quote(cleanedQuery) + "\\b.*")
                          || fiberName.matches(
                              ".*\\b" + java.util.regex.Pattern.quote(cleanedNormalized) + "\\b.*");
                    })
                .toList();

        // ✅ Performance: Limit material loading to prevent memory overflow
        // Try Materials (YARN, FABRIC)
        List<MaterialDto> materials =
            materialFacade.findByTenant(tenantId).stream().limit(AI_SEARCH_LIMIT).toList();
        List<MaterialDto> matchingMaterials =
            materials.stream()
                .filter(
                    m ->
                        m.getUid() != null
                            && (m.getUid().toLowerCase().contains(normalizedQuery.toLowerCase())
                                || m.getUid().toLowerCase().contains(query.toLowerCase())))
                .toList();

        if (!matchingFibers.isEmpty()) {
          found = true;
          result.append("✅ Fiber(s) found:\n");
          for (FiberDto f :
              matchingFibers.size() > 3 ? matchingFibers.subList(0, 3) : matchingFibers) {
            result.append(String.format("- Fiber: %s (UID: %s)\n", f.getFiberName(), f.getUid()));
          }
          if (matchingFibers.size() > 3) {
            result.append(String.format("(%d fiber(s) found)\n", matchingFibers.size()));
          }
        }

        if (!matchingMaterials.isEmpty()) {
          found = true;
          result.append("\n✅ Material(s) found:\n");
          for (MaterialDto m :
              matchingMaterials.size() > 3 ? matchingMaterials.subList(0, 3) : matchingMaterials) {
            result.append(
                String.format(
                    "- Material: %s (Type: %s, UID: %s)\n",
                    m.getUid(), m.getMaterialType(), m.getUid()));
          }
          if (matchingMaterials.size() > 3) {
            result.append(String.format("(%d material(s) found)\n", matchingMaterials.size()));
          }
        }
      }
    }

    if (!found) {
      result.append(String.format("\n❌ No results found for '%s'.\n", query));
      result.append("💡 Tips:\n");
      result.append("- 'cotton' → search FIBER\n");
      result.append("- 'cotton yarn' → search YARN\n");
      result.append("- 'gabardine' → search FABRIC\n");
    }

    return result.toString();
  }

  /** Detect entity type from query string using intelligent pattern matching. */
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
        ||
        // Technical specs often indicate fabric
        lowerQuery.matches(".*\\d+/\\d+.*")
        || lowerQuery.contains("gsm")) {
      return EntityType.FABRIC;
    }

    // FIBER indicators (base materials, but not yarn/fabric)
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

    // Default to unknown - will search all
    return EntityType.UNKNOWN;
  }

  /** Entity type enum for smart search. */
  private enum EntityType {
    FIBER("Fiber (Cotton, Polyester, Wool, etc.)"),
    YARN("Yarn"),
    FABRIC("Fabric"),
    UNKNOWN("Unknown (search all types)");

    private final String displayName;

    EntityType(String displayName) {
      this.displayName = displayName;
    }
  }

  /** Search materials by query. Also checks if there are related fibers for better context. */
  private String searchMaterials(UUID tenantId, Map<String, Object> parameters) {
    String query = (String) parameters.getOrDefault("query", "");

    // ✅ Performance: Limit material loading to prevent memory overflow
    List<MaterialDto> materials =
        materialFacade.findByTenant(tenantId).stream().limit(AI_SEARCH_LIMIT).toList();

    if (query.isBlank()) {
      return String.format("Total materials in system: %d", materials.size());
    }

    // Turkish-to-English translation for material/fiber names
    String normalizedQuery = normalizeFiberQuery(query);
    String lowerQuery = query.toLowerCase();
    String lowerNormalized = normalizedQuery.toLowerCase();

    // ⚠️ CRITICAL FIX: Search in both Material UID AND related Fiber fiberName
    // Problem: Material UID (e.g., "SYS-FIB-000068") doesn't contain "cotton"
    // Solution: For Material type=FIBER, also search in Fiber.fiberName (e.g., "Cotton (100%)")

    // ✅ Performance: Batch load all FIBER-type materials' fibers in one SQL query
    // Material UID may not contain "cotton"; search in related Fiber.fiberName as well.

    List<MaterialDto> fiberMaterials =
        materials.stream()
            .filter(m -> m.getMaterialType() == MaterialType.FIBER)
            .collect(Collectors.toList());

    // ✅ Performance: Batch SQL query (NOT Java filtering)
    // Flow: Repository method → SQL query → Database → Java filtering
    // SQL: SELECT * FROM prod_fiber WHERE material_id IN (?, ?, ...)
    // This is ONE database query, not N queries
    Map<UUID, FiberDto> materialIdToFiber = new HashMap<>();
    if (!fiberMaterials.isEmpty()) {
      List<UUID> materialIds =
          fiberMaterials.stream().map(MaterialDto::getId).collect(Collectors.toList());

      // ✅ Step 1: SQL Query → Database (batch load current tenant)
      // Repository method → SQL: SELECT f.* FROM prod_fiber f WHERE f.material_id IN (?, ?, ...)
      // Database returns: List<Fiber> entities
      List<Fiber> tenantFibers = fiberRepository.findByMaterialIdIn(materialIds);

      // ✅ Step 2: Also load system tenant fibers (shared/global fibers)
      // System tenant fibers are available to all tenants
      UUID systemTenantId = TenantContext.SYSTEM_TENANT_ID;
      List<Fiber> systemFibers =
          fiberRepository.findByTenantIdAndIsActiveTrue(systemTenantId).stream()
              .filter(f -> f.getMaterial() != null && materialIds.contains(f.getMaterial().getId()))
              .collect(Collectors.toList());

      // ✅ Step 3: Java processing (convert to DTO, build map)
      // Combine tenant + system fibers, convert to DTO, build map
      // This is in-memory processing, no additional DB queries
      tenantFibers.stream()
          .map(FiberDto::from)
          .forEach(f -> materialIdToFiber.put(f.getMaterialId(), f));

      systemFibers.stream()
          .map(FiberDto::from)
          .forEach(f -> materialIdToFiber.put(f.getMaterialId(), f));

      log.debug(
          "Loaded {} fibers for {} FIBER-type materials (tenant: {}, system: {})",
          materialIdToFiber.size(),
          materialIds.size(),
          tenantFibers.size(),
          systemFibers.size());
    }

    // Now filter materials with both UID and Fiber fiberName search
    List<MaterialDto> matching =
        materials.stream()
            .filter(
                m -> {
                  if (m.getUid() == null) return false;

                  // Search in Material UID
                  String uid = m.getUid().toLowerCase();
                  boolean matchesUid = uid.contains(lowerNormalized) || uid.contains(lowerQuery);

                  // If Material type=FIBER, also search in related Fiber fiberName
                  if (m.getMaterialType() == MaterialType.FIBER) {
                    FiberDto fiber = materialIdToFiber.get(m.getId());
                    if (fiber != null && fiber.getFiberName() != null) {
                      String fiberName = fiber.getFiberName().toLowerCase();
                      boolean matchesFiberName =
                          fiberName.contains(lowerNormalized) || fiberName.contains(lowerQuery);
                      if (matchesFiberName) {
                        log.debug(
                            "Material match via Fiber: UID={}, FiberName={}, Query={}",
                            m.getUid(),
                            fiber.getFiberName(),
                            query);
                        return true;
                      }
                    }
                  }

                  return matchesUid;
                })
            .collect(Collectors.toList());

    // If no materials found, check if it might be a fiber name
    if (matching.isEmpty()) {
      // ✅ Performance: Limit fiber loading to prevent memory overflow
      // Try searching fibers instead
      List<FiberDto> fibers = fiberFacade.findAll().stream().limit(AI_SEARCH_LIMIT).toList();
      List<FiberDto> matchingFibers =
          fibers.stream()
              .filter(
                  f -> {
                    if (f.getFiberName() == null) return false;
                    String fiberName = f.getFiberName().toLowerCase();
                    String searchTerm = normalizedQuery.toLowerCase();
                    return fiberName.contains(searchTerm)
                        || fiberName.contains(query.toLowerCase());
                  })
              .toList();

      if (!matchingFibers.isEmpty()) {
        StringBuilder result = new StringBuilder();
        result.append(
            String.format("⚠️ No material found for '%s', but fiber(s) found:\n\n", query));
        for (FiberDto f : matchingFibers) {
          result.append(
              String.format(
                  "- Fiber: %s (UID: %s, Status: %s)\n",
                  f.getFiberName(), f.getUid(), f.getStatus()));
          if (f.getMaterialId() != null) {
            materialFacade
                .findById(tenantId, f.getMaterialId())
                .ifPresent(
                    m -> {
                      result.append(
                          String.format(
                              "  → Related Material: %s (UID: %s)\n",
                              m.getMaterialType(), m.getUid()));
                    });
          }
        }
        result.append(
            "\n💡 Note: Material and Fiber are different entities. "
                + "Use 'search_fibers' to search fibers.");
        return result.toString();
      }

      return String.format(
          "No materials found matching '%s'. Try 'search_fibers' for names like 'cotton'.", query);
    }

    // Summarize if too many results (reduce token usage)
    // Aggressive summarization: limit to 5 results to save tokens
    if (matching.size() > 5) {
      StringBuilder result =
          new StringBuilder(
              String.format("Found %d materials, showing first 5:\n\n", matching.size()));
      for (MaterialDto m : matching.subList(0, 5)) {
        result.append(String.format("- %s (%s)\n", m.getUid(), m.getMaterialType()));
      }
      result.append("\n(Please refine your search for more specific results.)");
      return result.toString();
    }

    StringBuilder result =
        new StringBuilder(String.format("Found %d materials:\n\n", matching.size()));
    for (MaterialDto m : matching) {
      result.append(String.format("- %s (%s)\n", m.getUid(), m.getMaterialType()));
    }
    return result.toString();
  }

  /**
   * Get production status summary.
   *
   * <p>Provides overview of production-related entities (materials, fibers). When operations module
   * is available, will include job/work order statistics.
   */
  private String getProductionStatus(UUID tenantId) {
    // ✅ Performance: Limit material loading to prevent memory overflow
    List<MaterialDto> materials =
        materialFacade.findByTenant(tenantId).stream().limit(AI_SEARCH_LIMIT).toList();
    long activeMaterials =
        materials.stream().filter(m -> m.getIsActive() != null && m.getIsActive()).count();

    Map<String, Long> materialsByType =
        materials.stream()
            .filter(m -> m.getIsActive() != null && m.getIsActive())
            .collect(
                Collectors.groupingBy(
                    m -> m.getMaterialType() != null ? m.getMaterialType().toString() : "UNKNOWN",
                    Collectors.counting()));

    StringBuilder status = new StringBuilder();
    status.append("📊 Production Status Summary\n\n");
    status.append(String.format("Active Materials: %d\n", activeMaterials));

    if (!materialsByType.isEmpty()) {
      status.append("\nMaterials by Type:\n");
      materialsByType.forEach(
          (type, count) -> status.append(String.format("  - %s: %d\n", type, count)));
    }

    status.append("\n");
    status.append("ℹ️ Operations module (jobs, work orders) not yet implemented.\n");
    status.append("For detailed production tracking, please use the production dashboard.");

    return status.toString();
  }

  /** Get detailed fiber information including composition and technical specs. */
  private String getFiberInfo(UUID tenantId, Map<String, Object> parameters) {
    String fiberName = (String) parameters.get("fiberName");
    UUID fiberId =
        parameters.get("fiberId") != null
            ? UUID.fromString(parameters.get("fiberId").toString())
            : null;

    if (fiberId == null && (fiberName == null || fiberName.isBlank())) {
      return "Fiber ID or name is required. Please provide either 'fiberId' or 'fiberName'.";
    }

    Optional<FiberDto> fiber;
    if (fiberId != null) {
      fiber = fiberFacade.findById(fiberId);
    } else {
      // ✅ Performance: Limit fiber loading and apply search filter
      // Search by name
      List<FiberDto> fibers =
          fiberFacade.findAll().stream()
              .limit(AI_SEARCH_LIMIT) // Limit before filtering for better performance
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
    info.append(
        String.format("Grade: %s\n", f.getFiberGrade() != null ? f.getFiberGrade() : "N/A"));

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

  /**
   * Search fibers by name or other criteria. Supports Turkish-to-English translation for common
   * fiber names. Also includes system tenant fibers (shared/global fibers).
   */
  private String searchFibers(UUID tenantId, Map<String, Object> parameters) {
    String query = (String) parameters.getOrDefault("query", "");

    if (query.isBlank()) {
      long count = fiberFacade.findAll().size();
      return String.format("Total fiber count: %d", count);
    }

    // Turkish-to-English translation
    String normalizedQuery = normalizeFiberQuery(query);
    String lowerQuery = query.toLowerCase();
    String lowerNormalized = normalizedQuery.toLowerCase();

    // Clean query
    String cleanedQuery =
        lowerQuery.replace("elyaf", "").replace("fiber", "").replace("materyal", "").trim();

    // ✅ Performance: Use filtered query instead of findAll
    String searchQuery = cleanedQuery.isBlank() ? normalizedQuery : cleanedQuery;
    if (searchQuery.isBlank()) {
      searchQuery = query;
    }

    // Tenant fibers (filtered, max 50)
    List<FiberDto> tenantFibers =
        fiberRepository
            .findByTenantIdAndFiberNameContainingIgnoreCase(tenantId, searchQuery)
            .stream()
            .limit(50)
            .map(FiberDto::from)
            .toList();

    // System tenant fibers (filtered, max 25)
    UUID systemTenantId = TenantContext.SYSTEM_TENANT_ID;
    List<FiberDto> systemFibers =
        fiberRepository
            .findByTenantIdAndFiberNameContainingIgnoreCase(systemTenantId, searchQuery)
            .stream()
            .limit(25)
            .map(FiberDto::from)
            .toList();

    // Combine and filter
    List<FiberDto> allFibers = new java.util.ArrayList<>(tenantFibers);
    allFibers.addAll(systemFibers);

    List<FiberDto> matching =
        allFibers.stream()
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

    // ✅ Performance: Limit to top 5 results
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

  /**
   * List all active fiber categories.
   *
   * <p>Helps AI find the right category when creating fibers.
   */
  private String listFiberCategories(UUID tenantId) {
    List<FiberCategory> categories = fiberCategoryRepository.findByIsActiveTrue();

    if (categories.isEmpty()) {
      return "No fiber categories found in the system.";
    }

    StringBuilder result = new StringBuilder("Available Fiber Categories:\n\n");
    for (FiberCategory category : categories) {
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

  /**
   * Create a new material.
   *
   * <p>Required: materialType (FIBER, YARN, FABRIC, CHEMICAL, CONSUMABLE), unit (kg, m, piece,
   * etc.)
   */
  private String createMaterial(UUID tenantId, Map<String, Object> parameters) {
    try {
      String materialTypeStr = (String) parameters.get("materialType");
      String unit = (String) parameters.get("unit");

      if (materialTypeStr == null || materialTypeStr.isBlank()) {
        return "❌ Material Type is required. Valid: FIBER, YARN, FABRIC, CHEMICAL, CONSUMABLE";
      }

      if (unit == null || unit.isBlank()) {
        return "❌ Unit is required. Examples: kg, m, piece, liter";
      }

      MaterialType materialType;
      try {
        materialType = MaterialType.valueOf(materialTypeStr.toUpperCase());
      } catch (IllegalArgumentException e) {
        return String.format(
            "❌ Invalid Material Type: %s. Valid: FIBER, YARN, FABRIC, CHEMICAL, CONSUMABLE",
            materialTypeStr);
      }

      CreateMaterialRequest request =
          CreateMaterialRequest.builder().materialType(materialType).unit(unit).build();

      MaterialDto created = materialFacade.createMaterial(request);

      return String.format(
          "✅ Material created successfully!\n\n"
              + "Material ID: %s\n"
              + "UID: %s\n"
              + "Type: %s\n"
              + "Unit: %s\n"
              + "Status: Active",
          created.getId(), created.getUid(), created.getMaterialType(), created.getUnit());
    } catch (Exception e) {
      log.error("Error creating material", e);
      return "❌ Error creating material: " + e.getMessage();
    }
  }

  /**
   * Create a new fiber.
   *
   * <p><b>USER-FRIENDLY:</b> Material can be auto-created if materialId is not provided.
   *
   * <p>Required: fiberCategoryId (UUID), fiberName, unit (if materialId is null)
   *
   * <p>Optional: materialId (if provided, existing Material will be used), fiberGrade, composition
   * (for blended fibers), remarks
   */
  private String createFiber(UUID tenantId, Map<String, Object> parameters) {
    try {
      String materialIdStr = (String) parameters.get("materialId");
      String unit = (String) parameters.get("unit");
      String fiberCategoryIdStr = (String) parameters.get("fiberCategoryId");
      String fiberName = (String) parameters.get("fiberName");

      // USER-FRIENDLY: If materialId is not provided, unit is required (Material will be
      // auto-created)
      if ((materialIdStr == null || materialIdStr.isBlank()) && (unit == null || unit.isBlank())) {
        return "❌ Either materialId or unit is required.\n\n"
            + "Option 1: Provide materialId to use existing Material\n"
            + "Option 2: Provide unit (e.g. 'kg') to auto-create Material with type=FIBER";
      }

      if (fiberCategoryIdStr == null || fiberCategoryIdStr.isBlank()) {
        return "❌ Fiber Category ID is required.";
      }

      if (fiberName == null || fiberName.isBlank()) {
        return "❌ Fiber Name is required.";
      }

      UUID materialId = null;
      UUID fiberCategoryId;
      try {
        if (materialIdStr != null && !materialIdStr.isBlank()) {
          materialId = UUID.fromString(materialIdStr);

          // Validate Material exists and belongs to current tenant if provided
          if (!materialFacade.exists(tenantId, materialId)) {
            return String.format(
                "❌ Material not found: %s\n\n"
                    + "Possible reasons:\n"
                    + "- Material ID is incorrect\n"
                    + "- Material belongs to another tenant\n"
                    + "- Material was deleted\n\n"
                    + "Use search_materials for ID, or provide unit to auto-create.",
                materialId);
          }
        }

        fiberCategoryId = UUID.fromString(fiberCategoryIdStr);
      } catch (IllegalArgumentException e) {
        return "❌ Invalid UUID format for materialId or fiberCategoryId.";
      }

      // Optional fields
      UUID fiberIsoCodeId =
          parameters.get("fiberIsoCodeId") != null
              ? UUID.fromString(parameters.get("fiberIsoCodeId").toString())
              : null;
      String fiberGrade = (String) parameters.get("fiberGrade");
      String remarks = (String) parameters.get("remarks");

      // Composition for blended fibers (optional)
      Map<UUID, BigDecimal> composition = null;
      // Note: Composition parsing would need custom logic if provided via AI

      CreateFiberRequest request =
          CreateFiberRequest.builder()
              .materialId(materialId) // Optional - if null, Material will be auto-created
              .unit(unit) // Required if materialId is null
              .fiberCategoryId(fiberCategoryId)
              .fiberIsoCodeId(fiberIsoCodeId)
              .fiberName(fiberName)
              .fiberGrade(fiberGrade)
              .composition(composition)
              .remarks(remarks)
              .build();

      FiberDto created = fiberFacade.createFiber(request);

      return String.format(
          "✅ Fiber created successfully!\n\n"
              + "Fiber ID: %s\n"
              + "UID: %s\n"
              + "Name: %s\n"
              + "Grade: %s\n"
              + "Status: %s",
          created.getId(),
          created.getUid(),
          created.getFiberName(),
          created.getFiberGrade() != null ? created.getFiberGrade() : "N/A",
          created.getStatus() != null ? created.getStatus() : "N/A");
    } catch (Exception e) {
      log.error("Error creating fiber", e);
      return "❌ Error creating fiber: " + e.getMessage();
    }
  }

  /**
   * Normalize fiber query by translating Turkish fiber names to English.
   *
   * <p><b>Purpose:</b> AI searches often use Turkish fiber names (e.g., "pamuk", "viskoz"), but
   * database stores English names (e.g., "cotton", "viscose"). This method translates Turkish
   * queries to English for better matching.
   *
   * <p><b>Examples:</b>
   *
   * <ul>
   *   <li>"pamuk" → "cotton"
   *   <li>"viskoz" → "viscose"
   *   <li>"polyester cotton blend" → normalized for matching
   * </ul>
   *
   * @param query Original query (may contain Turkish fiber names)
   * @return Normalized query (Turkish names replaced with English)
   */
  private String normalizeFiberQuery(String query) {
    if (query == null || query.isBlank()) {
      return query;
    }

    String lowerQuery = query.toLowerCase().trim();

    // Turkish-to-English fiber name mapping
    // Using HashMap to avoid Map.of() 10-pair limit
    Map<String, String> translations = new HashMap<>();

    // Natural fibers
    translations.put("pamuk", "cotton");
    translations.put("cotton", "cotton");
    translations.put("yün", "wool");
    translations.put("wool", "wool");
    translations.put("keten", "linen");
    translations.put("linen", "linen");
    translations.put("ipek", "silk");
    translations.put("silk", "silk");
    translations.put("kenevir", "hemp");
    translations.put("hemp", "hemp");
    translations.put("bambu", "bamboo");
    translations.put("bamboo", "bamboo");
    translations.put("jüt", "jute");
    translations.put("jute", "jute");

    // Synthetic fibers
    translations.put("polyester", "polyester");
    translations.put("poliester", "polyester");
    translations.put("naylon", "nylon");
    translations.put("nylon", "nylon");
    translations.put("viscose", "viscose");
    translations.put("viskoz", "viscose");
    translations.put("viskon", "viscose");
    translations.put("rayon", "rayon");
    translations.put("modal", "modal");
    translations.put("lyocell", "lyocell");
    translations.put("tencel", "lyocell");
    translations.put("elastan", "elastane");
    translations.put("spandeks", "elastane");
    translations.put("elastane", "elastane");
    translations.put("polypropilen", "polypropylene");
    translations.put("polypropylene", "polypropylene");
    translations.put("polyetilen", "polyethylene");
    translations.put("polyethylene", "polyethylene");
    translations.put("akrilik", "acrylic");
    translations.put("acrylic", "acrylic");
    translations.put("polyamid", "polyamide");
    translations.put("polyamide", "polyamide");

    // Generic terms
    translations.put("materyal", "material");
    translations.put("materyali", "material");
    translations.put("elyaf", "fiber");
    translations.put("fiber", "fiber");

    // Check for exact matches first (longest match wins)
    String result = lowerQuery;
    for (Map.Entry<String, String> entry : translations.entrySet()) {
      if (result.contains(entry.getKey())) {
        // Replace Turkish word with English, but keep other parts of query
        result = result.replace(entry.getKey(), entry.getValue());
      }
    }

    // If translation occurred, return normalized; otherwise return original
    return result.equals(lowerQuery) ? query : result;
  }
}
