package com.fabricmanagement.production.execution.goodsreceipt.app;

import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceipt;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptItem;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptStatus;
import com.fabricmanagement.production.execution.goodsreceipt.domain.exception.GoodsReceiptDomainException;
import com.fabricmanagement.production.execution.goodsreceipt.dto.CreateGoodsReceiptRequest;
import com.fabricmanagement.production.execution.goodsreceipt.dto.GoodsReceiptResponse;
import com.fabricmanagement.production.execution.goodsreceipt.infra.repository.GoodsReceiptItemRepository;
import com.fabricmanagement.production.execution.goodsreceipt.infra.repository.GoodsReceiptRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GoodsReceiptService {

  private final GoodsReceiptRepository receiptRepository;
  private final GoodsReceiptItemRepository itemRepository;
  private final com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository
      batchRepository;
  private final com.fabricmanagement.procurement.purchaseorder.infra.repository
          .PurchaseOrderRepository
      poRepository;
  private final com.fabricmanagement.procurement.subcontract.infra.repository
          .SubcontractOrderRepository
      scRepository;

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
    GoodsReceipt receipt =
        GoodsReceipt.builder()
            .receiptNumber(generateReceiptNumber())
            .sourceType(request.getSourceType())
            .sourceId(request.getSourceId())
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
      String barcode = generateBarcode(sourceIdentifier, seqNo);
      GoodsReceiptItem item =
          GoodsReceiptItem.builder()
              .goodsReceiptId(saved.getId())
              .sequenceNo(seqNo)
              .barcode(barcode)
              .serialNumber(itemReq.getSerialNumber())
              .netWeight(itemReq.getNetWeight())
              .grossWeight(itemReq.getGrossWeight())
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

    receipt.setStatus(GoodsReceiptStatus.CONFIRMED);
    GoodsReceipt saved = receiptRepository.save(receipt);

    log.info(
        "GoodsReceipt confirmed: {} [source={}/{}]",
        saved.getReceiptNumber(),
        saved.getSourceType(),
        saved.getSourceId());

    // TODO Phase 3.2+: publish GoodsReceiptConfirmed event
    // → IWM StockTransaction(RECEIPT) for each item
    // → Update PurchaseOrder / SubcontractOrder status

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
    return switch (sourceType) {
      case BATCH ->
          batchRepository
              .findById(sourceId)
              .orElseThrow(
                  () ->
                      new GoodsReceiptDomainException("Batch not found for sourceId: " + sourceId))
              .getBatchCode();
      case PURCHASE_ORDER ->
          poRepository
              .findById(sourceId)
              .orElseThrow(
                  () ->
                      new GoodsReceiptDomainException(
                          "PurchaseOrder not found for sourceId: " + sourceId))
              .getPoNumber();
      case SUBCONTRACT_ORDER ->
          scRepository
              .findById(sourceId)
              .orElseThrow(
                  () ->
                      new GoodsReceiptDomainException(
                          "SubcontractOrder not found for sourceId: " + sourceId))
              .getScNumber();
    };
  }

  /** Generates item barcode from source identifier. Format: {sourceIdentifier}-{3-digit-seq} */
  private String generateBarcode(String sourceIdentifier, int sequenceNo) {
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
                        .notes(item.getNotes())
                        .build())
            .toList();

    return GoodsReceiptResponse.builder()
        .id(receipt.getId())
        .uid(receipt.getUid())
        .receiptNumber(receipt.getReceiptNumber())
        .sourceType(receipt.getSourceType())
        .sourceId(receipt.getSourceId())
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
