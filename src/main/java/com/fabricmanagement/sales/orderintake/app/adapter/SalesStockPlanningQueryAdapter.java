package com.fabricmanagement.sales.orderintake.app.adapter;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.generator.domain.port.out.SalesStockPlanningQueryPort;
import com.fabricmanagement.production.core.stockunit.api.StockUnitSoftHoldPort;
import com.fabricmanagement.sales.common.domain.SalesQuantityPolicy;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesStockPlanningQueryAdapter implements SalesStockPlanningQueryPort {
  private final QuoteRepository quotes;
  private final SalesOrderRepository orders;
  private final SalesOrderLineRepository lines;
  private final StockUnitSoftHoldPort holds;
  private final com.fabricmanagement.production.core.stockunit.api.PieceAllocationPort allocations;
  private final com.fabricmanagement.production.core.stockunit.api.StockUnitCutPlanPort cutPlans;
  private final EntityManager entityManager;
  private final com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService
      products;

  @Override
  public UUID logicalId(UUID documentId, boolean quote) {
    if (quote) return documentId;
    return orders
        .findByTenantIdAndId(TenantContext.requireTenantId(), documentId)
        .map(order -> order.getQuoteId() == null ? documentId : order.getQuoteId())
        .orElse(documentId);
  }

  private boolean fabric(UUID product) {
    return products
        .find(TenantContext.requireTenantId(), product)
        .filter(
            value ->
                value.productType() == com.fabricmanagement.product.core.domain.ProductType.FABRIC)
        .isPresent();
  }

  @Override
  public Work current(UUID documentId, boolean quoteTarget) {
    var tenant = TenantContext.requireTenantId();
    if (quoteTarget) {
      var converted =
          orders.findFirstByTenantIdAndQuoteIdAndIsActiveTrueOrderByCreatedAtDesc(
              tenant, documentId);
      if (converted.isPresent()) return current(converted.get().getId(), false);
      var found = quotes.findByTenantIdAndIdAndIsActiveTrue(tenant, documentId);
      if (found.isEmpty())
        return new Work(documentId, "SALES_QUOTE", documentId, "", false, List.of());
      var quote = found.get();
      entityManager.refresh(quote, LockModeType.PESSIMISTIC_READ);
      boolean active =
          quote.isActive()
              && !List.of(
                      QuoteStatus.REJECTED,
                      QuoteStatus.EXPIRED,
                      QuoteStatus.SUPERSEDED,
                      QuoteStatus.CONVERTED)
                  .contains(quote.getStatus());
      var fabricLines =
          quote.getLines().stream()
              .filter(com.fabricmanagement.sales.quote.domain.QuoteLine::isActive)
              .filter(line -> fabric(line.getProductId()))
              .toList();
      return new Work(
          documentId,
          "SALES_QUOTE",
          documentId,
          quote.getQuoteNumber(),
          active && !fabricLines.isEmpty(),
          fabricLines.stream()
              .map(
                  line ->
                      requirement(
                              line.getRequestedQty(),
                              line.getUnit(),
                              !holds.heldPieceIds(line.getId()).isEmpty(),
                              false,
                              line.getQuantityPolicy())
                          + cutRequirement(line.getId()))
              .toList());
    }
    var order = orders.findByTenantIdAndId(tenant, documentId).orElseThrow();
    entityManager.refresh(order, LockModeType.PESSIMISTIC_READ);
    var activeLines =
        lines
            .findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByCreatedAtAscIdAsc(
                tenant, documentId)
            .stream()
            .filter(line -> fabric(line.getProductId()))
            .toList();
    boolean active =
        Boolean.TRUE.equals(order.getIsActive())
            && !order.getStatus().isTerminal()
            && order.getStatus() != OrderStatus.SHIPPED;
    return new Work(
        order.getQuoteId() == null ? documentId : order.getQuoteId(),
        "SALES_ORDER",
        documentId,
        order.getOrderNumber(),
        (active
                || activeLines.stream()
                    .anyMatch(
                        line ->
                            cutPlans
                                .currentForLine(line.getId())
                                .map(
                                    com.fabricmanagement.production.core.stockunit.api
                                            .StockUnitCutPlanPort.Summary
                                        ::physicalWorkPending)
                                .orElse(false)))
            && !activeLines.isEmpty(),
        activeLines.stream()
            .map(
                line ->
                    requirement(
                            line.getRequestedQty(),
                            line.getUnit(),
                            !holds.heldPieceIds(line.getId()).isEmpty(),
                            !allocations.activeForLine(tenant, line.getId()).isEmpty(),
                            line.getQuantityPolicy())
                        + cutRequirement(line.getId()))
            .toList());
  }

  private String requirement(
      java.math.BigDecimal quantity,
      String unit,
      boolean reserved,
      boolean allocated,
      SalesQuantityPolicy policy) {
    String stock =
        allocated
            ? "Review the allocated rolls"
            : reserved ? "Review the pre-reserved rolls" : "Choose the source rolls";
    return quantity.toPlainString()
        + " "
        + unit
        + ": "
        + stock
        + (policy == SalesQuantityPolicy.EXACT
            ? "; retain the exact quantity and assess any cutting"
            : "");
  }

  private String cutRequirement(UUID line) {
    return cutPlans
        .currentForLine(line)
        .map(plan -> "; cut plan " + plan.state() + ": " + plan.routesTo())
        .orElse("");
  }
}
