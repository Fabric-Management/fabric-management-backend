package com.fabricmanagement.production.masterdata.product.app.adapter;

import com.fabricmanagement.common.infrastructure.ai.AIQueryNormalizer;
import com.fabricmanagement.common.infrastructure.ai.AIToolProvider;
import com.fabricmanagement.production.masterdata.fiber.api.facade.FiberFacade;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberDto;
import com.fabricmanagement.production.masterdata.product.api.facade.ProductFacade;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.dto.CreateProductRequest;
import com.fabricmanagement.production.masterdata.product.dto.ProductDto;
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
 * AI Tool Provider for Product-specific operations. Moves product tools out of platform/ai to the
 * production/product domain.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductAIToolProvider implements AIToolProvider {

  private final ProductFacade productFacade;
  private final FiberFacade fiberFacade;
  private final AIQueryNormalizer queryNormalizer;

  private static final int AI_SEARCH_LIMIT = 500;

  @Override
  public Set<String> getSupportedTools() {
    return Set.of(
        "check_product_stock", "create_product", "search_products", "get_production_status");
  }

  @Override
  public String execute(UUID tenantId, String toolName, Map<String, Object> parameters) {
    return switch (toolName) {
      case "check_product_stock" -> checkProductStock(tenantId, parameters);
      case "create_product" -> createProduct(tenantId, parameters);
      case "search_products" -> searchProducts(tenantId, parameters);
      case "get_production_status" -> getProductionStatus(tenantId);
      default -> throw new IllegalArgumentException("Unknown AI tool: " + toolName);
    };
  }

  /** Search products by query. Also checks if there are related fibers for better context. */
  private String searchProducts(UUID tenantId, Map<String, Object> parameters) {
    String query = (String) parameters.getOrDefault("query", "");
    String typeFilter = (String) parameters.get("type");

    List<ProductDto> products =
        productFacade.findByTenant(tenantId).stream().limit(AI_SEARCH_LIMIT).toList();

    if (query.isBlank() && typeFilter == null) {
      return String.format("Total products in system: %d", products.size());
    }

    // Apply type filter if provided (YARN, FABRIC, etc.)
    if (typeFilter != null) {
      try {
        ProductType filteredType = ProductType.valueOf(typeFilter.toUpperCase());
        products = products.stream().filter(m -> m.getProductType() == filteredType).toList();
      } catch (IllegalArgumentException e) {
        log.warn("Invalid product type filter: {}", typeFilter);
      }
    }

    String normalizedQuery = queryNormalizer.normalizeFiberQuery(query);
    String lowerQuery = query.toLowerCase();
    String lowerNormalized = normalizedQuery.toLowerCase();

    // Batch load fibers for FIBER-type products to support name searching
    Map<UUID, FiberDto> productIdToFiber = new HashMap<>();
    List<ProductDto> fiberProducts =
        products.stream()
            .filter(m -> m.getProductType() == ProductType.FIBER)
            .collect(Collectors.toList());

    if (!fiberProducts.isEmpty() && (typeFilter == null || typeFilter.equalsIgnoreCase("FIBER"))) {
      List<UUID> productIds =
          fiberProducts.stream().map(ProductDto::getId).collect(Collectors.toList());

      // Use the newly added batch facade method
      fiberFacade
          .findByProductIds(productIds)
          .forEach(f -> productIdToFiber.put(f.getProductId(), f));
    }

    // Filter products by UID or Fiber name
    List<ProductDto> matching =
        products.stream()
            .filter(
                m -> {
                  if (m.getUid() == null) return false;

                  // Search in Product UID
                  String uid = m.getUid().toLowerCase();
                  boolean matchesUid = uid.contains(lowerNormalized) || uid.contains(lowerQuery);

                  // If Product type=FIBER, also search in related Fiber name
                  if (m.getProductType() == ProductType.FIBER) {
                    FiberDto fiber = productIdToFiber.get(m.getId());
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

    // Fallback: if no products found, check if it's a fiber name
    if (matching.isEmpty() && !query.isBlank()) {
      List<FiberDto> matchingFibers = fiberFacade.findByNameContaining(query);

      if (!matchingFibers.isEmpty()) {
        StringBuilder res = new StringBuilder();
        res.append(String.format("⚠️ No product found for '%s', but fiber(s) found:\n\n", query));
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
          "No products found matching '%s'. Try 'search_fibers' for fiber names.", query);
    }

    // Format output
    if (matching.size() > 5) {
      StringBuilder res =
          new StringBuilder(
              String.format("Found %d products, showing first 5:\n\n", matching.size()));
      for (ProductDto m : matching.subList(0, 5)) {
        res.append(String.format("- %s (%s)\n", m.getUid(), m.getProductType()));
      }
      res.append("\n(Please refine your search for more results.)");
      return res.toString();
    }

    StringBuilder res = new StringBuilder(String.format("Found %d products:\n\n", matching.size()));
    for (ProductDto m : matching) {
      res.append(String.format("- %s (%s)\n", m.getUid(), m.getProductType()));
    }
    return res.toString();
  }

  /** Provides an overview of production-related products grouped by type. */
  private String getProductionStatus(UUID tenantId) {
    List<ProductDto> products =
        productFacade.findByTenant(tenantId).stream().limit(AI_SEARCH_LIMIT).toList();

    long activeCount =
        products.stream().filter(m -> m.getIsActive() != null && m.getIsActive()).count();

    Map<String, Long> byType =
        products.stream()
            .filter(m -> m.getIsActive() != null && m.getIsActive())
            .collect(
                Collectors.groupingBy(
                    m -> m.getProductType() != null ? m.getProductType().toString() : "UNKNOWN",
                    Collectors.counting()));

    StringBuilder status = new StringBuilder();
    status.append("📊 Production Status Summary\n\n");
    status.append(String.format("Active Products: %d\n", activeCount));

    if (!byType.isEmpty()) {
      status.append("\nProducts by Type:\n");
      byType.forEach((type, count) -> status.append(String.format("  - %s: %d\n", type, count)));
    }

    status.append("\nℹ️ Operations tracking (jobs/orders) is pending implementation.");
    return status.toString();
  }

  /** Check product by name; directs user to Inventory module for stock details. */
  private String checkProductStock(UUID tenantId, Map<String, Object> parameters) {
    String productName = (String) parameters.get("productName");
    if (productName == null || productName.isBlank()) {
      return "Product name is required for stock check.";
    }

    String search = productName.toLowerCase().trim();
    List<ProductDto> products =
        productFacade.findByTenant(tenantId).stream().limit(AI_SEARCH_LIMIT).toList();
    List<ProductDto> matching =
        products.stream()
            .filter(
                m ->
                    m.getUid() != null && m.getUid().toLowerCase().contains(search)
                        || (m.getProductType() != null
                            && m.getProductType().toString().toLowerCase().contains(search)))
            .toList();

    if (matching.isEmpty()) {
      return String.format(
          "Product '%s' not found. No such product/product is defined in the system.\n\n"
              + "Use the Product Management module to add this product.",
          productName);
    }

    if (matching.size() > 1) {
      StringBuilder result =
          new StringBuilder(
              String.format("Found %d products matching '%s':\n\n", matching.size(), productName));
      for (ProductDto m : matching) {
        result.append(String.format("- %s (%s)\n", m.getUid(), m.getProductType()));
      }
      result.append("\nPlease specify which product to check.");
      return result.toString();
    }

    ProductDto product = matching.get(0);
    return String.format(
        "Product '%s' is defined in the system.\n\n"
            + "Product details:\n"
            + "- Type: %s\n"
            + "- Unit: %s\n"
            + "- Status: %s\n\n"
            + "Use the Production / Inventory modules for stock quantity.",
        product.getUid(),
        product.getProductType() != null ? product.getProductType().toString() : "N/A",
        product.getUnit() != null ? product.getUnit() : "N/A",
        product.getIsActive() != null && product.getIsActive() ? "Active" : "Inactive");
  }

  /** Create a new product. */
  private String createProduct(UUID tenantId, Map<String, Object> parameters) {
    try {
      String productTypeStr = (String) parameters.get("productType");
      String unit = (String) parameters.get("unit");

      if (productTypeStr == null || productTypeStr.isBlank()) {
        return "❌ Product Type is required. Valid: FIBER, YARN, FABRIC, CHEMICAL, CONSUMABLE";
      }

      if (unit == null || unit.isBlank()) {
        return "❌ Unit is required. Examples: kg, m, piece, liter";
      }

      ProductType productType;
      try {
        productType = ProductType.valueOf(productTypeStr.toUpperCase());
      } catch (IllegalArgumentException e) {
        return String.format(
            "❌ Invalid Product Type: %s. Valid: FIBER, YARN, FABRIC, CHEMICAL, CONSUMABLE",
            productTypeStr);
      }

      CreateProductRequest request =
          CreateProductRequest.builder().productType(productType).unit(unit).build();

      ProductDto created = productFacade.createProduct(request);

      return String.format(
          "✅ Product created successfully!\n\n"
              + "Product ID: %s\n"
              + "UID: %s\n"
              + "Type: %s\n"
              + "Unit: %s\n"
              + "Status: Active",
          created.getId(), created.getUid(), created.getProductType(), created.getUnit());
    } catch (Exception e) {
      log.error("Error creating product", e);
      return "❌ Error creating product: " + e.getMessage();
    }
  }
}
