package com.fabricmanagement.product.core.api.query;

import com.fabricmanagement.product.core.api.facade.ProductFacade;
import com.fabricmanagement.product.core.domain.ProductFinishedWidth;
import com.fabricmanagement.product.core.domain.ProductSalesUnit;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.ProductDto;
import com.fabricmanagement.product.core.dto.ProductSalesDefinitionDto;
import com.fabricmanagement.product.core.infra.repository.ProductFinishedWidthRepository;
import com.fabricmanagement.product.core.infra.repository.ProductSalesUnitRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public product read contract for order intake: which units and finished widths a product may be
 * sold in (SOI R05/R06). Other modules read product sales definitions only through this service.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductSalesDefinitionQueryService {

  private final ProductFacade productFacade;
  private final ProductFinishedWidthRepository widthRepository;
  private final ProductSalesUnitRepository unitRepository;

  public Optional<ProductSalesDefinitionDto> find(UUID tenantId, UUID productId) {
    if (tenantId == null || productId == null) {
      return Optional.empty();
    }
    return productFacade
        .findById(tenantId, productId)
        .map(
            product ->
                toDefinition(
                    product,
                    widthRepository
                        .findByTenantIdAndProductIdAndIsActiveTrueOrderByWidthUnitAscWidthValueAsc(
                            tenantId, productId),
                    unitRepository.findByTenantIdAndProductIdAndIsActiveTrueOrderByUnitAsc(
                        tenantId, productId)));
  }

  /** Definitions for the given products that exist in the tenant scope; unknown ids are absent. */
  public Map<UUID, ProductSalesDefinitionDto> findAll(
      UUID tenantId, Collection<ProductDto> products) {
    if (tenantId == null || products == null || products.isEmpty()) {
      return Map.of();
    }
    List<UUID> ids = products.stream().map(ProductDto::getId).toList();
    Map<UUID, List<ProductFinishedWidth>> widths =
        widthRepository.findByTenantIdAndProductIdInAndIsActiveTrue(tenantId, ids).stream()
            .collect(Collectors.groupingBy(ProductFinishedWidth::getProductId));
    Map<UUID, List<ProductSalesUnit>> units =
        unitRepository.findByTenantIdAndProductIdInAndIsActiveTrue(tenantId, ids).stream()
            .collect(Collectors.groupingBy(ProductSalesUnit::getProductId));
    Map<UUID, ProductSalesDefinitionDto> result = new LinkedHashMap<>();
    for (ProductDto product : products) {
      result.put(
          product.getId(),
          toDefinition(
              product,
              widths.getOrDefault(product.getId(), List.of()),
              units.getOrDefault(product.getId(), List.of())));
    }
    return result;
  }

  /** Active products of the sellable types in the tenant scope (SOI K03: Fiber, Yarn, Fabric). */
  public List<ProductDto> sellableProducts(UUID tenantId, ProductType type) {
    List<ProductType> types =
        type == null
            ? List.of(ProductType.FIBER, ProductType.YARN, ProductType.FABRIC)
            : List.of(type);
    List<ProductDto> products = new ArrayList<>();
    for (ProductType productType : types) {
      if (!isSellableType(productType)) {
        continue;
      }
      products.addAll(productFacade.findByType(tenantId, productType));
    }
    return products;
  }

  public static boolean isSellableType(ProductType type) {
    return type == ProductType.FIBER || type == ProductType.YARN || type == ProductType.FABRIC;
  }

  private static ProductSalesDefinitionDto toDefinition(
      ProductDto product, List<ProductFinishedWidth> widths, List<ProductSalesUnit> units) {
    return new ProductSalesDefinitionDto(
        product.getId(),
        product.getUid(),
        product.getDisplayName() != null ? product.getDisplayName() : product.getUid(),
        product.getProductType(),
        product.getUnit(),
        Boolean.TRUE.equals(product.getIsActive()),
        units.stream().map(ProductSalesUnit::getUnit).sorted().toList(),
        widths.stream()
            .sorted(
                Comparator.comparing(ProductFinishedWidth::getWidthUnit)
                    .thenComparing(ProductFinishedWidth::getWidthValue))
            .map(
                width ->
                    new ProductSalesDefinitionDto.FinishedWidthOption(
                        width.getWidthValue(), width.getWidthUnit()))
            .toList());
  }
}
