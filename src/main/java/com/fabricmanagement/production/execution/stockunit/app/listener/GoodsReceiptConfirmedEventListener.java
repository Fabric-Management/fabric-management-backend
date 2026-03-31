package com.fabricmanagement.production.execution.stockunit.app.listener;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import com.fabricmanagement.production.execution.goodsreceipt.domain.event.GoodsReceiptConfirmedEvent;
import com.fabricmanagement.production.execution.stockunit.app.StockUnitService;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to GoodsReceipt confirmation and automatically spawns physical StockUnits.
 *
 * <p><b>Idempotency:</b> Before creating any units, the listener checks whether StockUnits for the
 * first item of this receipt already exist. If they do, the event is a duplicate and is skipped.
 *
 * <p><b>Scope:</b> Currently only BATCH source type is handled. PO/SC requires a cross-module
 * Batch-creation step first (planned for Phase 4).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoodsReceiptConfirmedEventListener {

  private final StockUnitService stockUnitService;
  private final BatchRepository batchRepository;
  private final StockUnitRepository stockUnitRepository;

  /**
   * We run AFTER_COMMIT and Async so that GR confirmation is never blocked by StockUnit creation
   * errors, and so each runs in its own independent transaction.
   */
  @Async
  @Transactional
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onGoodsReceiptConfirmed(GoodsReceiptConfirmedEvent event) {
    // C2: PO/SC receipts require a prior Batch-creation step (Phase 4). Log at INFO, not WARN —
    // every PO delivery would generate a WARN otherwise, causing alert fatigue.
    if (event.getSourceType() != GoodsReceiptSourceType.BATCH) {
      log.info(
          "StockUnit auto-creation skipped for {} source type (not yet implemented). Receipt: {}. "
              + "TODO: Phase 4 — resolve Batch from PO/SC before creating StockUnits.",
          event.getSourceType(),
          event.getReceiptNumber());
      return;
    }

    if (event.getItems().isEmpty()) {
      log.warn("GoodsReceiptConfirmedEvent has no items. Receipt: {}", event.getReceiptNumber());
      return;
    }

    UUID tenantId = event.getTenantId();
    UUID batchId = event.getSourceId(); // For BATCH sourceType, sourceId is batchId

    try {
      TenantContext.executeInTenantContext(
          tenantId,
          () -> {
            // C4: Idempotency guard — check if StockUnits for the first item already exist.
            // Spring delivers AFTER_COMMIT events at-least-once; this prevents duplicate units
            // if the same event is processed twice (e.g. node restart mid-processing).
            GoodsReceiptConfirmedEvent.ReceiptItemData firstItem = event.getItems().get(0);
            boolean alreadyProcessed =
                stockUnitRepository.existsByTenantIdAndSourceTypeAndSourceId(
                    tenantId, StockUnitSourceType.GOODS_RECEIPT, firstItem.itemId());

            if (alreadyProcessed) {
              log.info(
                  "GoodsReceiptConfirmedEvent already processed (duplicate). Skipping receipt: {}",
                  event.getReceiptNumber());
              return null;
            }

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
                                null, // serial number is not carried on the event payload
                                packageType,
                                item.netWeight(),
                                item.grossWeight(),
                                batch.getUnit(),
                                batch.getLocationId(),
                                StockUnitSourceType.GOODS_RECEIPT,
                                item.itemId()))
                    .toList();

            stockUnitService.createBulk(batchId, requests, TenantContext.SYSTEM_TENANT_ID);

            log.info(
                "Auto-created {} StockUnits for GoodsReceipt {}",
                requests.size(),
                event.getReceiptNumber());
            return null;
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
