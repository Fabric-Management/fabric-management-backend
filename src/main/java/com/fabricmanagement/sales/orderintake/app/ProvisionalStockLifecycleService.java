package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService;
import com.fabricmanagement.production.core.stockunit.api.StockUnitLeaseKeys;
import com.fabricmanagement.production.core.stockunit.api.StockUnitOwnershipPort;
import com.fabricmanagement.sales.orderintake.domain.DraftStockSelection;
import com.fabricmanagement.sales.orderintake.infra.repository.DraftStockSelectionRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Synchronous lifecycle only; never reinterprets a user's commercial quantity. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ProvisionalStockLifecycleService {
  private final DraftStockSelectionRepository selections;
  private final StockUnitOwnershipPort ownership;
  private final LiveEditLeaseService leases;
  private final com.fabricmanagement.production.core.stockunit.api.StockUnitCutPlanLifecyclePort
      cutPlans;

  public void ownerLine(UUID lineId) {
    release(selections.findMutableTargets(TenantContext.requireTenantId(), lineId));
    cutPlans.commercialLineChanged(lineId);
  }

  public void quote(UUID quoteId) {
    cutPlans.commercialDocumentChanged("QUOTE", quoteId);
    release(
        selections.findByTenantIdAndTargetQuoteIdAndStateIn(
            TenantContext.requireTenantId(),
            quoteId,
            List.of(
                DraftStockSelection.State.OPEN,
                DraftStockSelection.State.HELD,
                DraftStockSelection.State.RELEASED)));
  }

  private void release(List<DraftStockSelection> rows) {
    for (var row : rows) {
      ownership.lockProduct(row.getProductId());
      leases.release(
          StockUnitLeaseKeys.resource(row.getProductId()),
          row.getSessionId(),
          row.getActorId(),
          row.getTokens());
      row.release();
      selections.save(row);
    }
  }
}
