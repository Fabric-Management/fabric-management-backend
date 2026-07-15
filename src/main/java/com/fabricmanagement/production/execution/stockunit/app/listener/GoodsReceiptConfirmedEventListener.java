package com.fabricmanagement.production.execution.stockunit.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
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
import com.fabricmanagement.production.execution.stockunit.domain.exception.StockUnitMaterializationException;
import com.fabricmanagement.production.execution.stockunit.domain.exception.StockUnitMaterializationException.Reason;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Listens to GoodsReceipt confirmation and automatically spawns physical StockUnits.
 *
 * <p><b>Idempotency:</b> Before creating any units, the listener checks whether StockUnits for the
 * first item of this receipt already exist. If they do, the event is a duplicate and is skipped.
 *
 * <p><b>Failure contract:</b> Every non-duplicate receipt either creates its StockUnits or fails
 * closed. The exception is allowed to escape so the idempotency record rolls back and Spring
 * Modulith can retry the incomplete publication. BATCH and SUBCONTRACT_ORDER source types are
 * handled; PURCHASE_ORDER remains incomplete until its cross-module Batch creation flow exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoodsReceiptConfirmedEventListener {

  private final StockUnitService stockUnitService;
  private final BatchRepository batchRepository;
  private final StockUnitRepository stockUnitRepository;
  private final SubcontractOrderQueryService scQueryService;
  private final IdempotentEventHandler idempotentHandler;

  @ApplicationModuleListener
  public void onGoodsReceiptConfirmed(GoodsReceiptConfirmedEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onGoodsReceiptConfirmed",
        () -> {
          if (event.getSourceType() == GoodsReceiptSourceType.PURCHASE_ORDER) {
            log.info(
                "StockUnit materialization pending: reason={}, receipt={}, sourceType={}, eventId={}",
                Reason.PO_MATERIALIZATION_PENDING,
                event.getReceiptNumber(),
                event.getSourceType(),
                event.getEventId());
            throw materializationFailure(
                Reason.PO_MATERIALIZATION_PENDING,
                event,
                "purchase-order materialization is not implemented");
          }

          if (event.getItems() == null || event.getItems().isEmpty()) {
            throw materializationFailure(
                Reason.EMPTY_RECEIPT_ITEMS, event, "confirmed receipt contains no items");
          }

          UUID tenantId = event.getTenantId();

          try {
            TenantContext.executeInTenantContext(
                tenantId,
                () -> {
                  GoodsReceiptConfirmedEvent.ReceiptItemData firstItem = event.getItems().get(0);
                  // TODO: Defense-in-depth: IdempotentEventHandler primary dedup sağlar, bu
                  // secondary guard orijinal tasarımdan kalmıştır.
                  boolean alreadyProcessed =
                      stockUnitRepository.existsByTenantIdAndSourceTypeAndSourceId(
                          tenantId, StockUnitSourceType.GOODS_RECEIPT, firstItem.itemId());

                  if (alreadyProcessed) {
                    log.info(
                        "GoodsReceiptConfirmedEvent already processed (duplicate). Skipping receipt: {}",
                        event.getReceiptNumber());
                    return null;
                  }

                  switch (event.getSourceType()) {
                    case BATCH -> handleBatchReceipt(event, tenantId);
                    case SUBCONTRACT_ORDER -> handleSubcontractReceipt(event, tenantId);
                    case PURCHASE_ORDER ->
                        throw new IllegalStateException(
                            "PURCHASE_ORDER must be rejected before tenant processing");
                  }
                  return null;
                });
          } catch (StockUnitMaterializationException e) {
            throw e;
          } catch (Exception e) {
            throw materializationFailure(
                Reason.PROCESSING_FAILED, event, "unexpected processing failure", e);
          }
        });
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
      throw materializationFailure(
          Reason.MISSING_SC_OUTPUT_TYPE,
          event,
          "subcontractOrder=" + outputInfo.scNumber() + " has no output product type");
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

  private StockUnitMaterializationException materializationFailure(
      Reason reason, GoodsReceiptConfirmedEvent event, String detail) {
    return new StockUnitMaterializationException(reason, failureContext(event, detail));
  }

  private StockUnitMaterializationException materializationFailure(
      Reason reason, GoodsReceiptConfirmedEvent event, String detail, Throwable cause) {
    return new StockUnitMaterializationException(reason, failureContext(event, detail), cause);
  }

  private String failureContext(GoodsReceiptConfirmedEvent event, String detail) {
    return "receiptNumber="
        + event.getReceiptNumber()
        + ", sourceType="
        + event.getSourceType()
        + ", eventId="
        + event.getEventId()
        + ", detail="
        + detail;
  }
}
