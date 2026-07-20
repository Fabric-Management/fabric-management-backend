package com.fabricmanagement.production.execution.stockunit.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.procurement.purchaseorder.api.query.PurchaseOrderQueryService;
import com.fabricmanagement.procurement.subcontract.api.query.SubcontractOrderQueryService;
import com.fabricmanagement.procurement.subcontract.api.query.SubcontractOrderQueryService.SubcontractOutputInfo;
import com.fabricmanagement.production.execution.batch.app.BatchPrimaryMeasureService;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.domain.CreateBatchCommand;
import com.fabricmanagement.production.execution.batch.domain.PrimaryMeasure;
import com.fabricmanagement.production.execution.batch.domain.event.BatchCreatedEvent;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.goodsreceipt.domain.event.GoodsReceiptConfirmedEvent;
import com.fabricmanagement.production.execution.stockunit.app.StockUnitService;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.execution.stockunit.domain.exception.StockUnitMaterializationException;
import com.fabricmanagement.production.execution.stockunit.domain.exception.StockUnitMaterializationException.Reason;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.api.facade.ProductFacade;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
 * Modulith can retry the incomplete publication. BATCH, SUBCONTRACT_ORDER, and PURCHASE_ORDER
 * receipts are materialized without re-querying the goods-receipt tables.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoodsReceiptConfirmedEventListener {

  private final StockUnitService stockUnitService;
  private final BatchRepository batchRepository;
  private final StockUnitRepository stockUnitRepository;
  private final SubcontractOrderQueryService scQueryService;
  private final PurchaseOrderQueryService poQueryService;
  private final ProductFacade productFacade;
  private final BatchPrimaryMeasureService primaryMeasureService;
  private final ApplicationEventPublisher eventPublisher;
  private final IdempotentEventHandler idempotentHandler;

  @ApplicationModuleListener
  public void onGoodsReceiptConfirmed(GoodsReceiptConfirmedEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onGoodsReceiptConfirmed",
        () -> {
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
                    case PURCHASE_ORDER -> handlePurchaseOrderReceipt(event, tenantId);
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
                        null,
                        null,
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
                        null,
                        null,
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

  private void handlePurchaseOrderReceipt(GoodsReceiptConfirmedEvent event, UUID tenantId) {
    if (event.getSourceLineId() == null) {
      throw dataTerminalFailure(
          Reason.PO_EVENT_PREDATES_CONTRACT,
          event,
          "sourceLineId is absent",
          "cancel stale publication and recreate receipt");
    }

    PurchaseMaterial material = resolvePurchaseMaterial(event, tenantId);
    var lineInfo = material.lineInfo();
    ProductType productType = material.productType();

    PrimaryMeasure primaryMeasure = primaryMeasureService.primaryMeasure(productType);
    List<BigDecimal> canonicalLengths =
        event.getItems().stream().map(item -> canonicalLength(item, event)).toList();
    if (primaryMeasure == PrimaryMeasure.LENGTH
        && canonicalLengths.stream().anyMatch(java.util.Objects::isNull)) {
      throw dataTerminalFailure(
          Reason.PO_ITEM_MEASURE_INVALID,
          event,
          "fabric receipt item is missing length",
          "fix item length data or re-seed");
    }
    BigDecimal batchQuantity =
        primaryMeasure == PrimaryMeasure.LENGTH
            ? canonicalLengths.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            : event.getItems().stream()
                .map(GoodsReceiptConfirmedEvent.ReceiptItemData::netWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    String batchUnit = primaryMeasureService.canonicalUnit(primaryMeasure);

    Batch batch =
        batchRepository
            .findFirstByTenantIdAndSourceIdAndSourceType(
                tenantId, event.getReceiptId(), BatchSourceType.PURCHASE)
            .orElseGet(
                () ->
                    createPurchaseBatch(
                        event,
                        tenantId,
                        lineInfo.productId(),
                        productType,
                        batchQuantity,
                        batchUnit));

    PackageType packageType = determineDefaultPackageType(productType);
    List<StockUnitService.CreateStockUnitRequest> requests =
        java.util.stream.IntStream.range(0, event.getItems().size())
            .mapToObj(
                index -> {
                  var item = event.getItems().get(index);
                  BigDecimal length = canonicalLengths.get(index);
                  return new StockUnitService.CreateStockUnitRequest(
                      productType,
                      item.barcode(),
                      null,
                      packageType,
                      item.netWeight(),
                      item.grossWeight(),
                      "KG",
                      length,
                      length != null ? "M" : null,
                      null,
                      StockUnitSourceType.GOODS_RECEIPT,
                      item.itemId());
                })
            .toList();

    stockUnitService.createBulk(batch.getId(), requests, TenantContext.SYSTEM_ACTOR_ID);
    log.info(
        "Auto-created {} StockUnits and purchase Batch {} for GoodsReceipt {}",
        requests.size(),
        batch.getBatchCode(),
        event.getReceiptNumber());
  }

  private PurchaseMaterial resolvePurchaseMaterial(
      GoodsReceiptConfirmedEvent event, UUID tenantId) {
    PurchaseOrderQueryService.PurchaseOrderLineInfo lineInfo;
    try {
      lineInfo =
          poQueryService.getPurchaseOrderLineInfo(
              tenantId, event.getSourceId(), event.getSourceLineId());
    } catch (NotFoundException exception) {
      throw dataTerminalFailure(
          Reason.PO_LINE_UNRESOLVED,
          event,
          "purchase-order line could not be resolved",
          "fix purchase-order line data or re-seed",
          exception);
    }
    if (lineInfo.productId() == null) {
      throw dataTerminalFailure(
          Reason.PO_LINE_UNRESOLVED,
          event,
          "purchase-order line has no productId",
          "fix purchase-order line data or re-seed");
    }
    ProductType productType =
        productFacade
            .findById(tenantId, lineInfo.productId())
            .orElseThrow(
                () ->
                    dataTerminalFailure(
                        Reason.PO_LINE_UNRESOLVED,
                        event,
                        "purchase-order line product does not exist",
                        "fix product data or re-seed"))
            .getProductType();
    if (productType == null
        || productType == ProductType.CHEMICAL
        || productType == ProductType.CONSUMABLE) {
      throw dataTerminalFailure(
          Reason.PO_LINE_UNRESOLVED,
          event,
          "unsupported purchase product type=" + productType,
          "fix product data or re-seed");
    }
    return new PurchaseMaterial(lineInfo, productType);
  }

  private record PurchaseMaterial(
      PurchaseOrderQueryService.PurchaseOrderLineInfo lineInfo, ProductType productType) {}

  private Batch createPurchaseBatch(
      GoodsReceiptConfirmedEvent event,
      UUID tenantId,
      UUID productId,
      ProductType productType,
      BigDecimal quantity,
      String unit) {
    String receiptNumber = event.getReceiptNumber();
    String batchCode =
        receiptNumber.startsWith("GR-")
            ? "PUR-" + receiptNumber.substring("GR-".length())
            : "PUR-" + receiptNumber;
    Batch batch =
        Batch.create(
            new CreateBatchCommand(
                tenantId,
                productId,
                productType,
                batchCode,
                event.getSupplierBatchCode(),
                quantity,
                unit,
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                BatchSourceType.PURCHASE,
                event.getReceiptId(),
                null));
    Batch saved = batchRepository.save(batch);
    eventPublisher.publishEvent(
        new BatchCreatedEvent(
            tenantId, saved.getId(), saved.getQuantity(), saved.getUnit(), saved.getLocationId()));
    return saved;
  }

  private BigDecimal canonicalLength(
      GoodsReceiptConfirmedEvent.ReceiptItemData item, GoodsReceiptConfirmedEvent event) {
    if (item.length() == null && (item.lengthUnit() == null || item.lengthUnit().isBlank())) {
      return null;
    }
    return primaryMeasureService
        .toCanonical(item.length(), item.lengthUnit(), PrimaryMeasure.LENGTH)
        .filter(value -> value.signum() > 0)
        .orElseThrow(
            () ->
                dataTerminalFailure(
                    Reason.PO_ITEM_MEASURE_INVALID,
                    event,
                    "invalid item length for barcode=" + item.barcode(),
                    "fix item length data or re-seed"));
  }

  private StockUnitMaterializationException dataTerminalFailure(
      Reason reason, GoodsReceiptConfirmedEvent event, String detail, String guidance) {
    log.warn(
        "StockUnit materialization data-terminal: reason={}, receipt={}, eventId={}, guidance={}",
        reason,
        event.getReceiptNumber(),
        event.getEventId(),
        guidance);
    return materializationFailure(reason, event, detail + "; guidance=" + guidance);
  }

  private StockUnitMaterializationException dataTerminalFailure(
      Reason reason,
      GoodsReceiptConfirmedEvent event,
      String detail,
      String guidance,
      Throwable cause) {
    log.warn(
        "StockUnit materialization data-terminal: reason={}, receipt={}, eventId={}, guidance={}",
        reason,
        event.getReceiptNumber(),
        event.getEventId(),
        guidance);
    return materializationFailure(reason, event, detail + "; guidance=" + guidance, cause);
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
