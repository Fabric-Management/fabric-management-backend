package com.fabricmanagement.production.execution.stockunit.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.output.domain.event.ProductionOutputConfirmedEvent;
import com.fabricmanagement.production.execution.stockunit.app.StockUnitService;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

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
  private final IdempotentEventHandler idempotentHandler;

  @ApplicationModuleListener
  public void onProductionOutputConfirmed(ProductionOutputConfirmedEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onProductionOutputConfirmed",
        () -> {
          if (event.getItems().isEmpty()) {
            log.warn(
                "ProductionOutputConfirmedEvent has no items. Record: {}", event.getRecordId());
            return;
          }

          UUID tenantId = event.getTenantId();

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
                                    null,
                                    null,
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
        });
  }
}
