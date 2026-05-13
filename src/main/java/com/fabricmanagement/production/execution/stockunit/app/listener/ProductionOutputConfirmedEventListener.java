package com.fabricmanagement.production.execution.stockunit.app.listener;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.output.domain.event.ProductionOutputConfirmedEvent;
import com.fabricmanagement.production.execution.stockunit.app.StockUnitService;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to ProductionOutput confirmation and automatically spawns physical StockUnits.
 *
 * <p>Uses Idempotency guard for at-least-once delivery guarantees.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductionOutputConfirmedEventListener {

  private final StockUnitService stockUnitService;
  private final StockUnitRepository stockUnitRepository;

  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onProductionOutputConfirmed(ProductionOutputConfirmedEvent event) {
    if (event.getItems().isEmpty()) {
      log.warn("ProductionOutputConfirmedEvent has no items. Record: {}", event.getRecordId());
      return;
    }

    UUID tenantId = event.getTenantId();

    try {
      TenantContext.executeInTenantContext(
          tenantId,
          () -> {
            ProductionOutputConfirmedEvent.OutputItemData firstItem = event.getItems().get(0);
            boolean alreadyProcessed =
                stockUnitRepository.existsByTenantIdAndSourceTypeAndSourceId(
                    tenantId, StockUnitSourceType.PRODUCTION, firstItem.itemId());

            if (alreadyProcessed) {
              log.info(
                  "ProductionOutputConfirmedEvent already processed. Skipping record: {}",
                  event.getRecordId());
              return null;
            }

            List<StockUnitService.CreateStockUnitRequest> requests =
                event.getItems().stream()
                    .map(
                        item ->
                            new StockUnitService.CreateStockUnitRequest(
                                event.getOutputProductType(),
                                item.barcode(),
                                null, // serial number
                                item.packageType(),
                                item.netWeight(),
                                item.grossWeight(),
                                event.getUnit(),
                                item.locationId(),
                                StockUnitSourceType.PRODUCTION,
                                item.itemId()))
                    .toList();

            stockUnitService.createBulk(
                event.getBatchId(), requests, TenantContext.SYSTEM_ACTOR_ID);

            log.info(
                "Auto-created {} StockUnits for Production Output {}",
                requests.size(),
                event.getRecordId());
            return null;
          });
    } catch (Exception e) {
      log.error(
          "Failed to auto-create StockUnits for Production Output {}: {}",
          event.getRecordId(),
          e.getMessage(),
          e);
    }
  }
}
