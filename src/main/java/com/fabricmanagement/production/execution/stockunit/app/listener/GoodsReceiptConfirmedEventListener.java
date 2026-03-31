package com.fabricmanagement.production.execution.stockunit.app.listener;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import com.fabricmanagement.production.execution.goodsreceipt.domain.event.GoodsReceiptConfirmedEvent;
import com.fabricmanagement.production.execution.stockunit.app.StockUnitService;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Listens to GoodsReceipt confirmation and automatically spawns physical StockUnits. */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoodsReceiptConfirmedEventListener {

  private final StockUnitService stockUnitService;
  private final BatchRepository batchRepository;

  /**
   * We run AFTER_COMMIT and Async because we don't want to fail the GoodsReceipt confirmation if
   * StockUnit creation hits a transient issue, and we want it to run in its own transaction.
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onGoodsReceiptConfirmed(GoodsReceiptConfirmedEvent event) {
    if (event.getSourceType() != GoodsReceiptSourceType.BATCH) {
      log.warn(
          "StockUnit auto-creation is currently only implemented for BATCH source type. Skipped for {} (Receipt: {})",
          event.getSourceType(),
          event.getReceiptNumber());
      // TODO: Implement PO/SC batch resolution (cross-module procurement integration) in Phase 4
      return;
    }

    UUID tenantId = event.getTenantId();
    UUID batchId = event.getSourceId(); // For BATCH sourceType, sourceId is batchId

    try {
      TenantContext.executeInTenantContext(
          tenantId,
          () -> {
            Batch batch =
                batchRepository
                    .findByIdAndTenantId(batchId, tenantId)
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Batch not found for GR confirm: " + batchId));

            PackageType packageType = determineDefaultPackageType(batch.getMaterialType());

            List<StockUnitService.CreateStockUnitRequest> requests =
                event.getItems().stream()
                    .map(
                        item ->
                            new StockUnitService.CreateStockUnitRequest(
                                batch.getMaterialType(),
                                item.barcode(),
                                null, // Serial number not on basic event. Can be fetched if needed.
                                packageType,
                                item.netWeight(),
                                item.grossWeight(),
                                batch.getUnit(),
                                batch.getLocationId(), // Put units in batch's location
                                StockUnitSourceType.GOODS_RECEIPT,
                                item.itemId()))
                    .toList();

            // Using system actor for automated listener actions
            UUID systemActor = TenantContext.SYSTEM_TENANT_ID;

            stockUnitService.createBulk(batchId, requests, systemActor);

            log.info(
                "Auto-created {} StockUnits for GoodsReceipt {}",
                requests.size(),
                event.getReceiptNumber());
          });
    } catch (Exception e) {
      log.error(
          "Failed to auto-create StockUnits for GoodsReceipt {}: {}",
          event.getReceiptNumber(),
          e.getMessage(),
          e);
    }
  }

  private PackageType determineDefaultPackageType(MaterialType materialType) {
    return switch (materialType) {
      case FIBER -> PackageType.BALE;
      case YARN -> PackageType.BOBBIN;
      case FABRIC -> PackageType.ROLL;
      case CHEMICAL -> PackageType.DRUM;
      default -> PackageType.CARTON;
    };
  }
}
