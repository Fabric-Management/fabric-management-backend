package com.fabricmanagement.product.core.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.product.core.domain.ProductFinishedWidth;
import com.fabricmanagement.product.core.domain.ProductSalesUnit;
import com.fabricmanagement.product.core.dto.ProductSalesDefinitionDto;
import com.fabricmanagement.product.core.dto.UpdateProductSalesDefinitionRequest;
import com.fabricmanagement.product.core.infra.repository.ProductFinishedWidthRepository;
import com.fabricmanagement.product.core.infra.repository.ProductSalesUnitRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maintains the finished widths and extra sales units a product may be ordered in. */
@Service
@RequiredArgsConstructor
public class ProductSalesDefinitionService {

  private final ProductSalesDefinitionQueryService queries;
  private final ProductFinishedWidthRepository widthRepository;
  private final ProductSalesUnitRepository unitRepository;

  @Transactional(readOnly = true)
  public ProductSalesDefinitionDto get(UUID productId) {
    UUID tenantId = TenantContext.requireTenantId();
    return queries
        .find(tenantId, productId)
        .orElseThrow(() -> new NotFoundException("Product not found: " + productId));
  }

  /**
   * Replaces the product's extra units and finished widths. Removed options are soft-deleted so
   * existing order lines keep a readable history; new orders can no longer choose them.
   */
  @Transactional
  public ProductSalesDefinitionDto replace(
      UUID productId, UpdateProductSalesDefinitionRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    ProductSalesDefinitionDto current =
        queries
            .find(tenantId, productId)
            .orElseThrow(() -> new NotFoundException("Product not found: " + productId));

    replaceUnits(tenantId, productId, current.baseUnit(), request.extraSalesUnits());
    replaceWidths(tenantId, productId, request.finishedWidths());
    return queries.find(tenantId, productId).orElseThrow();
  }

  private void replaceUnits(
      UUID tenantId, UUID productId, String baseUnit, List<String> requestedUnits) {
    String baseKey = baseUnit == null ? "" : baseUnit.trim().toUpperCase(Locale.ROOT);
    Map<String, String> requested = new LinkedHashMap<>();
    for (String unit : requestedUnits) {
      String key = unit.trim().toUpperCase(Locale.ROOT);
      if (!key.isEmpty() && !key.equals(baseKey)) {
        requested.putIfAbsent(key, unit.trim());
      }
    }
    List<ProductSalesUnit> existing =
        unitRepository.findByTenantIdAndProductIdAndIsActiveTrueOrderByUnitAsc(tenantId, productId);
    for (ProductSalesUnit unit : existing) {
      String key = unit.getUnit().toUpperCase(Locale.ROOT);
      if (requested.remove(key) == null) {
        unit.delete();
        unitRepository.save(unit);
      }
    }
    requested.values().forEach(unit -> unitRepository.save(ProductSalesUnit.of(productId, unit)));
  }

  private void replaceWidths(
      UUID tenantId,
      UUID productId,
      List<UpdateProductSalesDefinitionRequest.FinishedWidthInput> requestedWidths) {
    Map<String, ProductFinishedWidth> requested = new LinkedHashMap<>();
    for (var input : requestedWidths) {
      BigDecimal value = ProductFinishedWidth.normaliseValue(input.value());
      String unit = ProductFinishedWidth.normaliseUnit(input.unit());
      requested.putIfAbsent(key(value, unit), ProductFinishedWidth.of(productId, value, unit));
    }
    List<ProductFinishedWidth> existing =
        widthRepository.findByTenantIdAndProductIdAndIsActiveTrueOrderByWidthUnitAscWidthValueAsc(
            tenantId, productId);
    for (ProductFinishedWidth width : existing) {
      if (requested.remove(key(width.getWidthValue(), width.getWidthUnit())) == null) {
        width.delete();
        widthRepository.save(width);
      }
    }
    requested.values().forEach(widthRepository::save);
  }

  private static String key(BigDecimal value, String unit) {
    return value.stripTrailingZeros().toPlainString() + "|" + unit;
  }
}
