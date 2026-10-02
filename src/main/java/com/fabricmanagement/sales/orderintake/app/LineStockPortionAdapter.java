package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService;
import com.fabricmanagement.production.core.stockunit.api.PieceAllocationPort;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.port.LineStockPortionPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Sums the pieces held for a line and states them in the line unit (SOI D5). */
@Component
@RequiredArgsConstructor
public class LineStockPortionAdapter implements LineStockPortionPort {

  private final PieceAllocationPort allocation;
  private final ProductSalesDefinitionQueryService products;
  private final ProposalStockQueryService stockQuery;

  @Override
  public Optional<BigDecimal> ownFinishedStock(SalesOrderLine line) {
    UUID tenantId = TenantContext.requireTenantId();
    List<PieceAllocationPort.PieceAllocationView> held =
        allocation.activeForLine(tenantId, line.getId());
    if (held.isEmpty()) {
      return Optional.of(BigDecimal.ZERO);
    }
    BigDecimal canonical =
        held.stream()
            .map(PieceAllocationPort.PieceAllocationView::quantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return products
        .find(tenantId, line.getProductId())
        .flatMap(product -> stockQuery.measureFor(product.productType()))
        .flatMap(measure -> stockQuery.fromCanonical(canonical, line.getUnit(), measure))
        .map(value -> value.setScale(3, RoundingMode.HALF_UP));
  }
}
