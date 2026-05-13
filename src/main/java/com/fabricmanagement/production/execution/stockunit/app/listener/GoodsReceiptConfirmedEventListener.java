package com.fabricmanagement.production.execution.stockunit.app.listener;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.procurement.subcontract.api.query.SubcontractOrderQueryService;
import com.fabricmanagement.procurement.subcontract.api.query.SubcontractOrderQueryService.SubcontractOutputInfo;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import com.fabricmanagement.production.execution.goodsreceipt.domain.event.GoodsReceiptConfirmedEvent;
import com.fabricmanagement.production.execution.stockunit.app.StockUnitService;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to GoodsReceipt confirmation and automatically spawns physical StockUnits.
 *
 * <p><b>Idempotency:</b> Before creating any units, the listener checks whether StockUnits for the
 * first item of this receipt already exist. If they do, the event is a duplicate and is skipped.
 *
 * <p><b>Scope:</b> BATCH and SUBCONTRACT_ORDER source types are handled. PO requires a cross-module
 * Batch-creation step first (planned for Phase 4).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoodsReceiptConfirmedEventListener {

  private final StockUnitService stockUnitService;
  private final BatchRepository batchRepository;
  private final StockUnitRepository stockUnitRepository;
  private final SubcontractOrderQueryService scQueryService;

  /**
   * We run AFTER_COMMIT with REQUIRES_NEW so that GR confirmation is never blocked by StockUnit
   * creation errors, and so each runs in its own independent transaction.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onGoodsReceiptConfirmed(GoodsReceiptConfirmedEvent event) {
    // C2: PO/SC receipts require a prior Batch-creation step (Phase 4). Log at INFO, not WARN —
    // every PO delivery would generate a WARN otherwise, causing alert fatigue.
    if (event.getSourceType() == GoodsReceiptSourceType.PURCHASE_ORDER) {
      log.info(
          "StockUnit auto-creation skipped for {} source type (not yet implemented). Receipt: {}. "
              + "TODO: Phase 4 — resolve Batch from PO before creating StockUnits.",
          event.getSourceType(),
          event.getReceiptNumber());
      return;
    }

    if (event.getItems().isEmpty()) {
      log.warn("GoodsReceiptConfirmedEvent has no items. Receipt: {}", event.getReceiptNumber());
      return;
    }

    UUID tenantId = event.getTenantId();

    try {
      TenantContext.executeInTenantContext(
          tenantId,
          () -> {
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

            if (event.getSourceType() == GoodsReceiptSourceType.BATCH) {
              handleBatchReceipt(event, tenantId);
            } else if (event.getSourceType() == GoodsReceiptSourceType.SUBCONTRACT_ORDER) {
              handleSubcontractReceipt(event, tenantId);
            }
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

  private void handleBatchReceipt(GoodsReceiptConfirmedEvent event, UUID tenantId) {
    UUID batchId = event.getSourceId();
    Batch batch =
        batchRepository
            .findByIdAndTenantId(batchId, tenantId)
            .orElseThrow(
                () -> new IllegalStateException("Batch not found for GR confirm: " + batchId));

    PackageType packageType = determineDefaultPackageType(batch.getProductType());

    List<StockUnitService.CreateStockUnitRequest> requests =
        event.getItems().stream()
            .map(
                item ->
                    new StockUnitService.CreateStockUnitRequest(
                        batch.getProductType(),
                        item.barcode(),
                        null,
                        packageType,
                        item.netWeight(),
                        item.grossWeight(),
                        batch.getUnit(),
                        batch.getLocationId(),
                        StockUnitSourceType.GOODS_RECEIPT,
                        item.itemId()))
            .toList();

    stockUnitService.createBulk(batchId, requests, TenantContext.SYSTEM_ACTOR_ID);

    log.info(
        "Auto-created {} StockUnits for GoodsReceipt {}",
        requests.size(),
        event.getReceiptNumber());
  }

  private void handleSubcontractReceipt(GoodsReceiptConfirmedEvent event, UUID tenantId) {
    SubcontractOutputInfo outputInfo =
        scQueryService.getSubcontractOutputInfo(tenantId, event.getSourceId());

    if (outputInfo.outputProductType() == null) {
      log.warn(
          "SubcontractOrder {} has no output product type, skipping StockUnit creation",
          outputInfo.scNumber());
      return;
    }

    PackageType packageType = determineDefaultPackageType(outputInfo.outputProductType());

    List<StockUnitService.CreateStockUnitRequest> requests =
        event.getItems().stream()
            .map(
                item ->
                    new StockUnitService.CreateStockUnitRequest(
                        outputInfo.outputProductType(),
                        item.barcode(),
                        null,
                        packageType,
                        item.netWeight(),
                        item.grossWeight(),
                        outputInfo.outputUnit(),
                        null, // locationId not available in SC yet
                        StockUnitSourceType.GOODS_RECEIPT,
                        item.itemId()))
            .toList();

    stockUnitService.createBulk(outputInfo.batchId(), requests, TenantContext.SYSTEM_ACTOR_ID);

    log.info(
        "Auto-created {} StockUnits for SC GoodsReceipt {}",
        requests.size(),
        event.getReceiptNumber());
  }

  private PackageType determineDefaultPackageType(ProductType productType) {
    return switch (productType) {
      case FIBER -> PackageType.BALE;
      case YARN -> PackageType.BOBBIN;
      case FABRIC -> PackageType.ROLL;
      case CHEMICAL -> PackageType.DRUM;
      default -> PackageType.CARTON;
    };
  }
}
