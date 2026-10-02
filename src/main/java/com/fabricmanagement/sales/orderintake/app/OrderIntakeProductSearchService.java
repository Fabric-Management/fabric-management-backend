package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.ProductDto;
import com.fabricmanagement.product.core.dto.ProductSalesDefinitionDto;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.OfferableStockSummary;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeProductOption;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeProductStock;
import com.fabricmanagement.sales.salesproduct.domain.SalesProduct;
import com.fabricmanagement.sales.salesproduct.infra.repository.SalesProductRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product picker for order intake (SOI K03, K17): fibre, yarn and fabric products the customer may
 * order, searchable by code or name. A product whose catalogue entries are all private to other
 * customers is not offered.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderIntakeProductSearchService {

  // Include the 52 canonical seed fibers together with ordinary tenant products.
  // Search is still bounded; the picker asks users to narrow larger catalogues by name/code.
  static final int MAX_RESULTS = 200;

  private final ProductSalesDefinitionQueryService productDefinitions;
  private final SalesProductRepository catalogue;
  private final ProposalStockQueryService stockQuery;

  public List<OrderIntakeProductOption> search(
      UUID customerId, ProductType productType, String query) {
    UUID tenantId = TenantContext.requireTenantId();
    if (productType != null && !ProductSalesDefinitionQueryService.isSellableType(productType)) {
      return List.of();
    }
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    List<ProductDto> products =
        productDefinitions.sellableProducts(tenantId, productType).stream()
            .filter(product -> matches(product, needle))
            .sorted(
                Comparator.comparing(
                        (ProductDto product) -> label(product).toLowerCase(Locale.ROOT))
                    .thenComparing(ProductDto::getId))
            .toList();
    if (products.isEmpty()) {
      return List.of();
    }
    Map<UUID, List<SalesProduct>> entries =
        catalogue
            .findAllByTenantIdAndProductIdInAndIsActiveTrue(
                tenantId, products.stream().map(ProductDto::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(SalesProduct::getProductId));
    List<ProductDto> visible =
        products.stream()
            .filter(
                product -> visible(entries.getOrDefault(product.getId(), List.of()), customerId))
            .limit(MAX_RESULTS)
            .toList();
    Map<UUID, ProductSalesDefinitionDto> definitions =
        productDefinitions.findAll(tenantId, visible);
    List<ProductDto> offered =
        visible.stream()
            .filter(
                product -> {
                  ProductSalesDefinitionDto definition = definitions.get(product.getId());
                  return definition != null && definition.active();
                })
            .toList();
    Map<UUID, OfferableStockSummary> stock =
        stockQuery.offerableSummaries(tenantId, offered.stream().map(ProductDto::getId).toList());
    return offered.stream()
        .map(
            product ->
                toOption(
                    definitions.get(product.getId()),
                    entries.getOrDefault(product.getId(), List.of()),
                    customerId,
                    stock.getOrDefault(product.getId(), OfferableStockSummary.empty())))
        .toList();
  }

  static boolean visible(List<SalesProduct> entries, UUID customerId) {
    return entries.isEmpty()
        || entries.stream()
            .anyMatch(
                entry ->
                    entry.getCustomerId() == null
                        || Objects.equals(entry.getCustomerId(), customerId));
  }

  private static boolean matches(ProductDto product, String needle) {
    if (needle.isEmpty()) {
      return true;
    }
    return label(product).toLowerCase(Locale.ROOT).contains(needle)
        || (product.getUid() != null && product.getUid().toLowerCase(Locale.ROOT).contains(needle));
  }

  private static String label(ProductDto product) {
    return product.getDisplayName() != null ? product.getDisplayName() : product.getUid();
  }

  private static OrderIntakeProductOption toOption(
      ProductSalesDefinitionDto definition,
      List<SalesProduct> entries,
      UUID customerId,
      OfferableStockSummary stock) {
    Optional<SalesProduct> customerEntry =
        entries.stream()
            .filter(entry -> customerId != null && customerId.equals(entry.getCustomerId()))
            .findFirst();
    Optional<SalesProduct> priceEntry =
        customerEntry.or(() -> entries.stream().filter(e -> e.getCustomerId() == null).findFirst());
    return new OrderIntakeProductOption(
        definition.productId(),
        definition.uid(),
        definition.displayName(),
        definition.productType(),
        definition.baseUnit(),
        definition.allowedUnits(),
        definition.finishedWidths(),
        customerEntry.isPresent(),
        priceEntry.map(SalesProduct::getListPrice).orElse(null),
        priceEntry.map(SalesProduct::getCurrency).orElse(null),
        OrderIntakeProductStock.from(stock));
  }
}
