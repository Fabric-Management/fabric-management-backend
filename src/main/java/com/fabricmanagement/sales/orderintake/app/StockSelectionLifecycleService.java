package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.stockunit.api.StockUnitOwnershipPort;
import com.fabricmanagement.production.core.stockunit.api.StockUnitSoftHoldPort;
import com.fabricmanagement.sales.orderintake.domain.DraftStockSelection;
import com.fabricmanagement.sales.orderintake.domain.StockSelectionTerms;
import com.fabricmanagement.sales.orderintake.infra.repository.DraftStockSelectionRepository;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Synchronous invalidation joins the writer; no asynchronous listener can leave stock orphaned. */
@Component
@RequiredArgsConstructor
public class StockSelectionLifecycleService {
  private final DraftStockSelectionRepository selections;
  private final SalesOrderLineRepository lines;
  private final StockUnitSoftHoldPort holds;
  private final StockUnitOwnershipPort ownership;
  private final ProvisionalStockLifecycleService provisional;
  private final com.fabricmanagement.production.core.stockunit.api.StockUnitCutPlanLifecyclePort
      cutPlans;

  @Transactional(propagation = Propagation.MANDATORY)
  public void releaseChanged(SalesOrder order) {
    cutPlans.reconcileDocument(
        "SALES_ORDER",
        order.getId(),
        lines
            .findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByCreatedAtAscIdAsc(
                TenantContext.requireTenantId(), order.getId())
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    com.fabricmanagement.sales.salesorder.domain.SalesOrderLine::getId,
                    line -> StockSelectionTerms.fingerprint(order, line))));
    selections
        .findByTenantIdAndTargetOrderIdAndState(
            TenantContext.requireTenantId(), order.getId(), DraftStockSelection.State.HELD)
        .stream()
        .filter(
            selected ->
                lines
                    .findByTenantIdAndId(
                        TenantContext.requireTenantId(), selected.getTargetLineId())
                    .filter(line -> Boolean.TRUE.equals(line.getIsActive()))
                    .filter(
                        line ->
                            StockSelectionTerms.matchesRequest(order, line, selected.getRequest()))
                    .isEmpty())
        .map(DraftStockSelection::getTargetLineId)
        .distinct()
        .forEach(provisional::ownerLine);
    var active =
        selections.findByTenantIdAndSalesOrderIdAndState(
            TenantContext.requireTenantId(), order.getId(), DraftStockSelection.State.TRANSFERRED);
    for (var selected : active) {
      var current =
          lines.findByTenantIdAndId(
              TenantContext.requireTenantId(), selected.getSalesOrderLineId());
      if (current.isEmpty()
          || !Boolean.TRUE.equals(current.get().getIsActive())
          || !Objects.equals(
              selected.getTermsFingerprint(),
              StockSelectionTerms.fingerprint(order, current.get()))) {
        ownership.lockProduct(selected.getProductId());
        holds.releaseHolds(selected.getSalesOrderLineId());
        selected.release();
        selections.save(selected);
        // A lost stock choice never silently renegotiates the commercial quantity.
        // A deliberate quantity edit already updated the customer request in its own command.
      }
    }
  }
}
