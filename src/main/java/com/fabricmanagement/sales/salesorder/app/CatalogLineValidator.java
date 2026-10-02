package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.product.color.api.query.ColorQueryService;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.product.core.dto.ProductSalesDefinitionDto;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.salesorder.domain.CatalogLineInput;
import com.fabricmanagement.sales.salesproduct.domain.SalesProduct;
import com.fabricmanagement.sales.salesproduct.infra.repository.SalesProductRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The single validation path for catalogue order lines (SOI R02–R06, R23, TK-2). Create, full
 * replace and every later line write call this; no write path carries its own copy of the rules.
 */
@Component
@RequiredArgsConstructor
public class CatalogLineValidator {

  private final ProductSalesDefinitionQueryService productDefinitions;
  private final SalesProductRepository catalogue;
  private final ColorQueryService colors;

  /**
   * Validates the complete set of active lines an order will hold after the write.
   *
   * @param tenantId tenant of the order
   * @param customerId trading partner of the order (catalogue visibility)
   * @param lines every active line after the write, not only the changed ones
   */
  public void validate(
      UUID tenantId, UUID customerId, Collection<? extends CatalogLineInput> lines) {
    if (lines == null || lines.isEmpty()) {
      return;
    }
    Map<UUID, ProductSalesDefinitionDto> definitions = new HashMap<>();
    Set<UUID> checkedColors = new HashSet<>();
    Set<String> distributionKeys = new HashSet<>();
    Map<UUID, List<SalesProduct>> entriesByProduct = catalogueEntries(tenantId, lines);

    for (CatalogLineInput line : lines) {
      if (line.getProductId() == null) {
        throw OrderIntakeException.productRequired();
      }
      ProductSalesDefinitionDto definition =
          definitions.computeIfAbsent(
              line.getProductId(),
              productId ->
                  productDefinitions
                      .find(tenantId, productId)
                      .filter(ProductSalesDefinitionDto::active)
                      .orElseThrow(() -> OrderIntakeException.productNotAvailable(productId)));
      if (!ProductSalesDefinitionQueryService.isSellableType(definition.productType())) {
        throw OrderIntakeException.productTypeNotSellable(definition.productType());
      }
      requireVisible(
          line.getProductId(),
          customerId,
          entriesByProduct.getOrDefault(line.getProductId(), List.of()));
      if (!definition.allowsUnit(line.getUnit())) {
        throw OrderIntakeException.unitNotAllowed(line.getUnit());
      }
      if (line.getColorId() != null && checkedColors.add(line.getColorId())) {
        colors
            .findActiveReferenceById(line.getColorId())
            .orElseThrow(() -> OrderIntakeException.colorNotDefined(line.getColorId()));
      }
      requireDefinedWidth(definition, line.getFinishedWidth(), line.getFinishedWidthUnit());
      if (!distributionKeys.add(distributionKey(line))) {
        throw OrderIntakeException.duplicateDistribution();
      }
    }
  }

  /**
   * A product with only customer-specific catalogue entries is visible to those customers alone.
   * Products without catalogue entries are ordinary tenant products (IK-06).
   */
  static void requireVisible(UUID productId, UUID customerId, List<SalesProduct> entries) {
    if (entries.isEmpty()) {
      return;
    }
    boolean visible =
        entries.stream()
            .anyMatch(
                entry ->
                    entry.getCustomerId() == null
                        || Objects.equals(entry.getCustomerId(), customerId));
    if (!visible) {
      throw OrderIntakeException.productNotVisible(productId);
    }
  }

  private static void requireDefinedWidth(
      ProductSalesDefinitionDto definition, BigDecimal width, String unit) {
    boolean given = width != null || (unit != null && !unit.isBlank());
    if (!definition.definesFinishedWidths()) {
      if (given) {
        throw OrderIntakeException.widthNotDefined(width, unit);
      }
      return;
    }
    if (width == null || unit == null || unit.isBlank()) {
      throw OrderIntakeException.widthRequired();
    }
    if (!definition.allowsFinishedWidth(width, unit)) {
      throw OrderIntakeException.widthNotDefined(width, unit);
    }
  }

  static String distributionKey(CatalogLineInput line) {
    return String.join(
        "|",
        String.valueOf(line.getProductId()),
        String.valueOf(line.getColorId()),
        line.getFinishedWidth() == null
            ? ""
            : line.getFinishedWidth().stripTrailingZeros().toPlainString(),
        line.getFinishedWidthUnit() == null
            ? ""
            : line.getFinishedWidthUnit().trim().toUpperCase(Locale.ROOT),
        line.getUnit() == null ? "" : line.getUnit().trim().toUpperCase(Locale.ROOT),
        String.valueOf(line.getRequestedDeliveryDate()));
  }

  private Map<UUID, List<SalesProduct>> catalogueEntries(
      UUID tenantId, Collection<? extends CatalogLineInput> lines) {
    Set<UUID> productIds =
        lines.stream()
            .map(CatalogLineInput::getProductId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (productIds.isEmpty()) {
      return Map.of();
    }
    return catalogue.findAllByTenantIdAndProductIdInAndIsActiveTrue(tenantId, productIds).stream()
        .collect(Collectors.groupingBy(SalesProduct::getProductId));
  }
}
