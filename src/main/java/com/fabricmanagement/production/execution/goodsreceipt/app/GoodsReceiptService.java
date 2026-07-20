package com.fabricmanagement.production.execution.goodsreceipt.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.procurement.purchaseorder.api.query.PurchaseOrderQueryService;
import com.fabricmanagement.procurement.subcontract.api.query.SubcontractOrderQueryService;
import com.fabricmanagement.production.execution.batch.api.query.BatchQueryService;
import com.fabricmanagement.production.execution.batch.app.BatchPrimaryMeasureService;
import com.fabricmanagement.production.execution.batch.domain.PrimaryMeasure;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceipt;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptItem;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptStatus;
import com.fabricmanagement.production.execution.goodsreceipt.domain.event.GoodsReceiptConfirmedEvent;
import com.fabricmanagement.production.execution.goodsreceipt.domain.exception.GoodsReceiptDomainException;
import com.fabricmanagement.production.execution.goodsreceipt.dto.CreateGoodsReceiptRequest;
import com.fabricmanagement.production.execution.goodsreceipt.dto.GoodsReceiptResponse;
import com.fabricmanagement.production.execution.goodsreceipt.infra.repository.GoodsReceiptItemRepository;
import com.fabricmanagement.production.execution.goodsreceipt.infra.repository.GoodsReceiptRepository;
import com.fabricmanagement.production.masterdata.product.api.facade.ProductFacade;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GoodsReceiptService {

  private final GoodsReceiptRepository receiptRepository;
  private final GoodsReceiptItemRepository itemRepository;
  private final BatchQueryService batchQueryService;
  private final PurchaseOrderQueryService poQueryService;
  private final SubcontractOrderQueryService scQueryService;
  private final ProductFacade productFacade;
  private final BatchPrimaryMeasureService primaryMeasureService;
  private final ApplicationEventPublisher eventPublisher;

  /** Retrieves a GoodsReceipt with its items by ID. */
  public GoodsReceiptResponse getGoodsReceipt(UUID id) {
    GoodsReceipt receipt = findEntityById(id);
    List<GoodsReceiptItem> items =
        itemRepository.findByGoodsReceiptIdAndIsActiveTrueOrderBySequenceNoAsc(id);
    return mapToResponse(receipt, items);
  }

  /**
   * Creates a GoodsReceipt in DRAFT state with all items and auto-generated barcodes. Items receive
   * sequential barcodes based on sourceType.
   */
  @Transactional
  public GoodsReceiptResponse createGoodsReceipt(CreateGoodsReceiptRequest request) {
    ProductType purchaseProductType =
        resolvePurchaseProductType(
            request.getSourceType(), request.getSourceId(), request.getSourceLineId());
    validatePurchaseRequestItemLengths(purchaseProductType, request.getItems());

    GoodsReceipt receipt =
        GoodsReceipt.builder()
            .receiptNumber(generateReceiptNumber())
            .sourceType(request.getSourceType())
            .sourceId(request.getSourceId())
            .sourceLineId(purchaseProductType != null ? request.getSourceLineId() : null)
            .supplierBatchCode(request.getSupplierBatchCode())
            .receivedById(request.getReceivedById())
            .receivedAt(request.getReceivedAt() != null ? request.getReceivedAt() : Instant.now())
            .packageCount(request.getPackageCount())
            .vehicleInfo(request.getVehicleInfo())
            .damageNotes(request.getDamageNotes())
            .status(GoodsReceiptStatus.DRAFT)
            .build();

    GoodsReceipt saved = receiptRepository.save(receipt);

    // Look up the source identifier (Batch code, PO number, SC number)
    String sourceIdentifier = getSourceIdentifier(request.getSourceType(), request.getSourceId());

    // Generate items with auto-barcodes
    List<GoodsReceiptItem> items = new ArrayList<>();
    AtomicInteger seq = new AtomicInteger(1);
    for (CreateGoodsReceiptRequest.GoodsReceiptItemRequest itemReq : request.getItems()) {
      int seqNo = seq.getAndIncrement();
      String barcode =
          generateBarcode(
              request.getSourceType(), sourceIdentifier, saved.getReceiptNumber(), seqNo);
      GoodsReceiptItem item =
          GoodsReceiptItem.builder()
              .goodsReceiptId(saved.getId())
              .sequenceNo(seqNo)
              .barcode(barcode)
              .serialNumber(itemReq.getSerialNumber())
              .netWeight(itemReq.getNetWeight())
              .grossWeight(itemReq.getGrossWeight())
              .length(itemReq.getLength())
              .lengthUnit(primaryMeasureService.normalizeUnit(itemReq.getLengthUnit()))
              .notes(itemReq.getNotes())
              .build();
      items.add(item);
    }
    List<GoodsReceiptItem> savedItems = itemRepository.saveAll(items);

    java.math.BigDecimal totalNet =
        savedItems.stream()
            .map(GoodsReceiptItem::getNetWeight)
            .filter(java.util.Objects::nonNull)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

    java.math.BigDecimal totalGross =
        savedItems.stream()
            .map(GoodsReceiptItem::getGrossWeight)
            .filter(java.util.Objects::nonNull)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

    receipt.setNetWeight(totalNet);
    receipt.setGrossWeight(totalGross.compareTo(java.math.BigDecimal.ZERO) > 0 ? totalGross : null);
    saved = receiptRepository.save(receipt);

    log.info(
        "GoodsReceipt created: {} [source={}/{}] with {} items",
        saved.getReceiptNumber(),
        saved.getSourceType(),
        saved.getSourceId(),
        savedItems.size());

    return mapToResponse(saved, savedItems);
  }

  /**
   * Confirms a GoodsReceipt — transitions from DRAFT → CONFIRMED.
   *
   * <p>Post-confirmation side effects (IWM stock entry, source order update) are handled via the
   * GoodsReceiptConfirmed domain event in a future phase. For Phase 3.1 the transition itself is
   * implemented here synchronously.
   */
  @Transactional
  public GoodsReceiptResponse confirmGoodsReceipt(UUID id) {
    GoodsReceipt receipt = findEntityById(id);

    if (!receipt.getStatus().canTransitionTo(GoodsReceiptStatus.CONFIRMED)) {
      throw new GoodsReceiptDomainException(
          String.format(
              "Cannot confirm GoodsReceipt in status: %s (receipt: %s)",
              receipt.getStatus(), receipt.getReceiptNumber()));
    }

    List<GoodsReceiptItem> items =
        itemRepository.findByGoodsReceiptIdAndIsActiveTrueOrderBySequenceNoAsc(id);

    if (items.isEmpty()) {
      throw new GoodsReceiptDomainException(
          "Cannot confirm a GoodsReceipt with no items: " + receipt.getReceiptNumber());
    }

    ProductType purchaseProductType =
        resolvePurchaseProductType(
            receipt.getSourceType(), receipt.getSourceId(), receipt.getSourceLineId());
    validatePurchaseEntityItemLengths(purchaseProductType, items);

    receipt.setStatus(GoodsReceiptStatus.CONFIRMED);
    GoodsReceipt saved = receiptRepository.save(receipt);

    List<GoodsReceiptConfirmedEvent.ReceiptItemData> eventItems =
        items.stream()
            .map(
                i ->
                    GoodsReceiptConfirmedEvent.ReceiptItemData.builder()
                        .itemId(i.getId())
                        .barcode(i.getBarcode())
                        .netWeight(i.getNetWeight())
                        .grossWeight(i.getGrossWeight())
                        .length(i.getLength())
                        .lengthUnit(i.getLengthUnit())
                        .build())
            .toList();

    eventPublisher.publishEvent(
        GoodsReceiptConfirmedEvent.builder()
            .tenantId(TenantContext.requireTenantId())
            .receiptId(saved.getId())
            .receiptNumber(saved.getReceiptNumber())
            .sourceType(saved.getSourceType())
            .sourceId(saved.getSourceId())
            .sourceLineId(saved.getSourceLineId())
            .supplierBatchCode(saved.getSupplierBatchCode())
            .confirmedAt(Instant.now())
            .items(eventItems)
            .build());

    log.info(
        "GoodsReceipt confirmed and event published: {} [source={}/{}]",
        saved.getReceiptNumber(),
        saved.getSourceType(),
        saved.getSourceId());

    return mapToResponse(saved, items);
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private GoodsReceipt findEntityById(UUID id) {
    return receiptRepository
        .findById(id)
        .orElseThrow(
            () -> new GoodsReceiptDomainException("GoodsReceipt not found with id: " + id));
  }

  /**
   * Generates receipt number: GR-{YEAR}-{8-char random suffix}. DB unique index provides collision
   * safety.
   */
  private String generateReceiptNumber() {
    String year = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy"));
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    return String.format("GR-%s-%s", year, suffix);
  }

  /**
   * Retrieves the logical identifier (code/number) of the source entity to be used as the prefix
   * for item barcodes.
   *
   * <p>Rule:
   *
   * <ul>
   *   <li>BATCH → batchCode (BCH-...)
   *   <li>PURCHASE_ORDER → poNumber (PO-...)
   *   <li>SUBCONTRACT_ORDER → scNumber (SC-...)
   * </ul>
   */
  private String getSourceIdentifier(
      com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType
          sourceType,
      UUID sourceId) {
    UUID tenantId = TenantContext.requireTenantId();
    return switch (sourceType) {
      case BATCH -> batchQueryService.getBatchCode(sourceId);
      case PURCHASE_ORDER -> poQueryService.getPurchaseOrderNumber(sourceId);
      case SUBCONTRACT_ORDER -> scQueryService.getSubcontractOrderNumber(tenantId, sourceId);
    };
  }

  private ProductType resolvePurchaseProductType(
      com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType
          sourceType,
      UUID sourceId,
      UUID sourceLineId) {
    if (sourceType
        != com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType
            .PURCHASE_ORDER) {
      return null;
    }
    if (sourceLineId == null) {
      throw unprocessable(
          "Purchase-order receipts require sourceLineId", "GR_SOURCE_LINE_REQUIRED");
    }

    UUID tenantId = TenantContext.requireTenantId();
    PurchaseOrderQueryService.PurchaseOrderLineInfo lineInfo;
    try {
      lineInfo = poQueryService.getPurchaseOrderLineInfo(tenantId, sourceId, sourceLineId);
    } catch (NotFoundException ignored) {
      throw unprocessable(
          "Purchase-order line cannot be resolved for receipt materialization",
          "GR_PO_LINE_NOT_MATERIALIZABLE");
    }
    if (lineInfo.productId() == null) {
      throw unprocessable("Purchase-order line has no product", "GR_PO_LINE_NOT_MATERIALIZABLE");
    }

    ProductType productType =
        productFacade
            .findById(tenantId, lineInfo.productId())
            .map(product -> product.getProductType())
            .orElseThrow(
                () ->
                    unprocessable(
                        "Purchase-order line product cannot be resolved",
                        "GR_PO_LINE_NOT_MATERIALIZABLE"));
    if (productType == null
        || productType == ProductType.CHEMICAL
        || productType == ProductType.CONSUMABLE) {
      throw unprocessable(
          "Product type is not supported for stock-unit materialization: " + productType,
          "GR_PO_LINE_NOT_MATERIALIZABLE");
    }
    return productType;
  }

  private void validatePurchaseRequestItemLengths(
      ProductType productType, List<CreateGoodsReceiptRequest.GoodsReceiptItemRequest> items) {
    if (productType == null) {
      return;
    }
    for (CreateGoodsReceiptRequest.GoodsReceiptItemRequest item : items) {
      validatePurchaseItemLength(productType, item.getLength(), item.getLengthUnit());
    }
  }

  private void validatePurchaseEntityItemLengths(
      ProductType productType, List<GoodsReceiptItem> items) {
    if (productType == null) {
      return;
    }
    for (GoodsReceiptItem item : items) {
      validatePurchaseItemLength(productType, item.getLength(), item.getLengthUnit());
    }
  }

  private void validatePurchaseItemLength(
      ProductType productType, BigDecimal length, String lengthUnit) {
    boolean lengthRequired =
        primaryMeasureService.primaryMeasure(productType) == PrimaryMeasure.LENGTH;
    boolean hasLength = length != null;
    boolean hasUnit = lengthUnit != null && !lengthUnit.isBlank();

    if (lengthRequired && (!hasLength || !hasUnit)) {
      throw unprocessable(
          "Length and lengthUnit are required for fabric receipt items", "GR_ITEM_LENGTH_REQUIRED");
    }
    if (!hasLength && !hasUnit) {
      return;
    }
    if (!hasLength
        || !hasUnit
        || length.signum() <= 0
        || primaryMeasureService.toCanonical(length, lengthUnit, PrimaryMeasure.LENGTH).isEmpty()) {
      throw unprocessable(
          "Receipt item length must be positive and use M, CM, or MM", "GR_ITEM_UNIT_INVALID");
    }
  }

  private GoodsReceiptDomainException unprocessable(String message, String errorCode) {
    return new GoodsReceiptDomainException(message, errorCode, 422);
  }

  /**
   * Generates an item barcode. Purchase receipts include receipt identity to remain collision-safe.
   */
  private String generateBarcode(
      com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType
          sourceType,
      String sourceIdentifier,
      String receiptNumber,
      int sequenceNo) {
    if (sourceType
        == com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType
            .PURCHASE_ORDER) {
      return String.format("%s-%s-%03d", sourceIdentifier, receiptNumber, sequenceNo);
    }
    return String.format("%s-%03d", sourceIdentifier, sequenceNo);
  }

  private GoodsReceiptResponse mapToResponse(GoodsReceipt receipt, List<GoodsReceiptItem> items) {
    List<GoodsReceiptResponse.GoodsReceiptItemResponse> itemResponses =
        items.stream()
            .map(
                item ->
                    GoodsReceiptResponse.GoodsReceiptItemResponse.builder()
                        .id(item.getId())
                        .sequenceNo(item.getSequenceNo())
                        .barcode(item.getBarcode())
                        .serialNumber(item.getSerialNumber())
                        .netWeight(item.getNetWeight())
                        .grossWeight(item.getGrossWeight())
                        .length(item.getLength())
                        .lengthUnit(item.getLengthUnit())
                        .notes(item.getNotes())
                        .build())
            .toList();

    return GoodsReceiptResponse.builder()
        .id(receipt.getId())
        .uid(receipt.getUid())
        .receiptNumber(receipt.getReceiptNumber())
        .sourceType(receipt.getSourceType())
        .sourceId(receipt.getSourceId())
        .sourceLineId(receipt.getSourceLineId())
        .supplierBatchCode(receipt.getSupplierBatchCode())
        .receivedById(receipt.getReceivedById())
        .receivedAt(receipt.getReceivedAt())
        .packageCount(receipt.getPackageCount())
        .grossWeight(receipt.getGrossWeight())
        .netWeight(receipt.getNetWeight())
        .vehicleInfo(receipt.getVehicleInfo())
        .damageNotes(receipt.getDamageNotes())
        .status(receipt.getStatus())
        .items(itemResponses)
        .build();
  }
}
