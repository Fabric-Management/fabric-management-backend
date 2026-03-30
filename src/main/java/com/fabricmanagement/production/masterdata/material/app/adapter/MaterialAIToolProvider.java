package com.fabricmanagement.production.masterdata.material.app.adapter;

import com.fabricmanagement.common.infrastructure.ai.AIQueryNormalizer;
import com.fabricmanagement.common.infrastructure.ai.AIToolProvider;
import com.fabricmanagement.production.masterdata.fiber.api.facade.FiberFacade;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberDto;
import com.fabricmanagement.production.masterdata.material.api.facade.MaterialFacade;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import com.fabricmanagement.production.masterdata.material.dto.CreateMaterialRequest;
import com.fabricmanagement.production.masterdata.material.dto.MaterialDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI Tool Provider for Material-specific operations. Moves material tools out of platform/ai to the
 * production/material domain.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MaterialAIToolProvider implements AIToolProvider {

  private final MaterialFacade materialFacade;
  private final FiberFacade fiberFacade;
  private final AIQueryNormalizer queryNormalizer;

  private static final int AI_SEARCH_LIMIT = 500;

  @Override
  public Set<String> getSupportedTools() {
    return Set.of(
        "check_material_stock", "create_material", "search_materials", "get_production_status");
  }

  @Override
  public String execute(UUID tenantId, String toolName, Map<String, Object> parameters) {
    return switch (toolName) {
      case "check_material_stock" -> checkMaterialStock(tenantId, parameters);
      case "create_material" -> createMaterial(tenantId, parameters);
      case "search_materials" -> searchMaterials(tenantId, parameters);
      case "get_production_status" -> getProductionStatus(tenantId);
      default -> throw new IllegalArgumentException("Unknown AI tool: " + toolName);
    };
  }

  /** Search materials by query. Also checks if there are related fibers for better context. */
  private String searchMaterials(UUID tenantId, Map<String, Object> parameters) {
    String query = (String) parameters.getOrDefault("query", "");
    String typeFilter = (String) parameters.get("type");

    List<MaterialDto> materials =
        materialFacade.findByTenant(tenantId).stream().limit(AI_SEARCH_LIMIT).toList();

    if (query.isBlank() && typeFilter == null) {
      return String.format("Total materials in system: %d", materials.size());
    }

    // Apply type filter if provided (YARN, FABRIC, etc.)
    if (typeFilter != null) {
      try {
        MaterialType filteredType = MaterialType.valueOf(typeFilter.toUpperCase());
        materials = materials.stream().filter(m -> m.getMaterialType() == filteredType).toList();
      } catch (IllegalArgumentException e) {
        log.warn("Invalid material type filter: {}", typeFilter);
      }
    }

    String normalizedQuery = queryNormalizer.normalizeFiberQuery(query);
    String lowerQuery = query.toLowerCase();
    String lowerNormalized = normalizedQuery.toLowerCase();

    // Batch load fibers for FIBER-type materials to support name searching
    Map<UUID, FiberDto> materialIdToFiber = new HashMap<>();
    List<MaterialDto> fiberMaterials =
        materials.stream()
            .filter(m -> m.getMaterialType() == MaterialType.FIBER)
            .collect(Collectors.toList());

    if (!fiberMaterials.isEmpty() && (typeFilter == null || typeFilter.equalsIgnoreCase("FIBER"))) {
      List<UUID> materialIds =
          fiberMaterials.stream().map(MaterialDto::getId).collect(Collectors.toList());

      // Use the newly added batch facade method
      fiberFacade
          .findByMaterialIds(materialIds)
          .forEach(f -> materialIdToFiber.put(f.getMaterialId(), f));
    }

    // Filter materials by UID or Fiber name
    List<MaterialDto> matching =
        materials.stream()
            .filter(
                m -> {
                  if (m.getUid() == null) return false;

                  // Search in Material UID
                  String uid = m.getUid().toLowerCase();
                  boolean matchesUid = uid.contains(lowerNormalized) || uid.contains(lowerQuery);

                  // If Material type=FIBER, also search in related Fiber name
                  if (m.getMaterialType() == MaterialType.FIBER) {
                    FiberDto fiber = materialIdToFiber.get(m.getId());
                    if (fiber != null && fiber.getFiberName() != null) {
                      String fiberName = fiber.getFiberName().toLowerCase();
                      boolean matchesFiberName =
                          fiberName.contains(lowerNormalized) || fiberName.contains(lowerQuery);
                      if (matchesFiberName) return true;
                    }
                  }

                  return matchesUid;
                })
            .collect(Collectors.toList());

    // Fallback: if no materials found, check if it's a fiber name
    if (matching.isEmpty() && !query.isBlank()) {
      List<FiberDto> matchingFibers = fiberFacade.findByNameContaining(query);

      if (!matchingFibers.isEmpty()) {
        StringBuilder res = new StringBuilder();
        res.append(String.format("⚠️ No material found for '%s', but fiber(s) found:\n\n", query));
        for (FiberDto f : matchingFibers.stream().limit(5).toList()) {
          res.append(
              String.format(
                  "- Fiber: %s (UID: %s, Status: %s)\n",
                  f.getFiberName(), f.getUid(), f.getStatus()));
        }
        res.append("\n💡 Use 'search_fibers' for detailed fiber info.");
        return res.toString();
      }

      return String.format(
          "No materials found matching '%s'. Try 'search_fibers' for fiber names.", query);
    }

    // Format output
    if (matching.size() > 5) {
      StringBuilder res =
          new StringBuilder(
              String.format("Found %d materials, showing first 5:\n\n", matching.size()));
      for (MaterialDto m : matching.subList(0, 5)) {
        res.append(String.format("- %s (%s)\n", m.getUid(), m.getMaterialType()));
      }
      res.append("\n(Please refine your search for more results.)");
      return res.toString();
    }

    StringBuilder res =
        new StringBuilder(String.format("Found %d materials:\n\n", matching.size()));
    for (MaterialDto m : matching) {
      res.append(String.format("- %s (%s)\n", m.getUid(), m.getMaterialType()));
    }
    return res.toString();
  }

  /** Provides an overview of production-related materials grouped by type. */
  private String getProductionStatus(UUID tenantId) {
    List<MaterialDto> materials =
        materialFacade.findByTenant(tenantId).stream().limit(AI_SEARCH_LIMIT).toList();

    long activeCount =
        materials.stream().filter(m -> m.getIsActive() != null && m.getIsActive()).count();

    Map<String, Long> byType =
        materials.stream()
            .filter(m -> m.getIsActive() != null && m.getIsActive())
            .collect(
                Collectors.groupingBy(
                    m -> m.getMaterialType() != null ? m.getMaterialType().toString() : "UNKNOWN",
                    Collectors.counting()));

    StringBuilder status = new StringBuilder();
    status.append("📊 Production Status Summary\n\n");
    status.append(String.format("Active Materials: %d\n", activeCount));

    if (!byType.isEmpty()) {
      status.append("\nMaterials by Type:\n");
      byType.forEach((type, count) -> status.append(String.format("  - %s: %d\n", type, count)));
    }

    status.append("\nℹ️ Operations tracking (jobs/orders) is pending implementation.");
    return status.toString();
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

  /** Create a new material. */
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
}
