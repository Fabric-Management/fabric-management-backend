package com.fabricmanagement.procurement.purchaseorder.app;

import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrder;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderLine;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderStatus;
import com.fabricmanagement.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.fabricmanagement.procurement.purchaseorder.dto.PurchaseOrderResponse;
import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderLineRepository;
import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PurchaseOrderService {

  private final PurchaseOrderRepository poRepository;
  private final PurchaseOrderLineRepository lineRepository;

  public PurchaseOrderResponse getPurchaseOrder(UUID id) {
    PurchaseOrder po = findEntityById(id);
    List<PurchaseOrderLine> lines =
        lineRepository.findByPurchaseOrderIdAndIsActiveTrueOrderByCreatedAtAsc(id);
    return mapToResponse(po, lines);
  }

  /** Creates a PurchaseOrder in DRAFT state with all lines. Total is computed automatically. */
  @Transactional
  public PurchaseOrderResponse createPurchaseOrder(CreatePurchaseOrderRequest request) {
    BigDecimal total =
        request.getLines().stream()
            .map(l -> l.getUnitPrice().multiply(l.getQty()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    PurchaseOrder po =
        PurchaseOrder.builder()
            .poNumber(generatePoNumber())
            .workOrderId(request.getWorkOrderId())
            .tradingPartnerId(request.getTradingPartnerId())
            .supplierQuoteId(request.getSupplierQuoteId())
            .status(PurchaseOrderStatus.DRAFT)
            .currency(request.getCurrency())
            .paymentTerms(request.getPaymentTerms())
            .expectedDelivery(request.getExpectedDelivery())
            .totalAmount(total)
            .notes(request.getNotes())
            .build();

    PurchaseOrder saved = poRepository.save(po);

    List<PurchaseOrderLine> lines = new ArrayList<>();
    for (CreatePurchaseOrderRequest.PurchaseOrderLineRequest lineReq : request.getLines()) {
      BigDecimal lineTotal = lineReq.getUnitPrice().multiply(lineReq.getQty());
      lines.add(
          PurchaseOrderLine.builder()
              .purchaseOrderId(saved.getId())
              .rfqLineId(lineReq.getRfqLineId())
              .materialId(lineReq.getMaterialId())
              .productDesc(lineReq.getProductDesc())
              .qty(lineReq.getQty())
              .unit(lineReq.getUnit())
              .unitPrice(lineReq.getUnitPrice())
              .currency(lineReq.getCurrency())
              .totalPrice(lineTotal)
              .build());
    }
    List<PurchaseOrderLine> savedLines = lineRepository.saveAll(lines);

    log.info(
        "PurchaseOrder created: {} [workOrder={}, supplier={}]",
        saved.getPoNumber(),
        saved.getWorkOrderId(),
        saved.getTradingPartnerId());

    return mapToResponse(saved, savedLines);
  }

  /**
   * Transitions the PurchaseOrder status. Validates against the state machine in
   * PurchaseOrderStatus.
   *
   * <p>Special rule: CONFIRMED → PARTIALLY_RECEIVED / RECEIVED transitions are driven by
   * GoodsReceipt confirmation (via event in Phase 3.2+). Manual status change here is for
   * DRAFT→SENT, SENT→CONFIRMED, etc.
   */
  @Transactional
  public PurchaseOrderResponse changeStatus(UUID id, PurchaseOrderStatus newStatus) {
    PurchaseOrder po = findEntityById(id);

    if (!po.getStatus().canTransitionTo(newStatus)) {
      throw new ProcurementDomainException(
          String.format(
              "Invalid PurchaseOrder status transition: %s → %s (PO: %s)",
              po.getStatus(), newStatus, po.getPoNumber()));
    }

    po.setStatus(newStatus);
    PurchaseOrder saved = poRepository.save(po);

    List<PurchaseOrderLine> lines =
        lineRepository.findByPurchaseOrderIdAndIsActiveTrueOrderByCreatedAtAsc(id);

    log.info("PurchaseOrder {} status changed to {}", saved.getPoNumber(), newStatus);
    return mapToResponse(saved, lines);
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private PurchaseOrder findEntityById(UUID id) {
    return poRepository
        .findById(id)
        .orElseThrow(
            () -> new ProcurementDomainException("PurchaseOrder not found with id: " + id));
  }

  /** Format: PO-{YEAR}-{8-char random suffix}. DB UNIQUE index provides hard safety. */
  private String generatePoNumber() {
    String year = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy"));
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    return String.format("PO-%s-%s", year, suffix);
  }

  private PurchaseOrderResponse mapToResponse(PurchaseOrder po, List<PurchaseOrderLine> lines) {
    List<PurchaseOrderResponse.PurchaseOrderLineResponse> lineResps =
        lines.stream()
            .map(
                l ->
                    PurchaseOrderResponse.PurchaseOrderLineResponse.builder()
                        .id(l.getId())
                        .materialId(l.getMaterialId())
                        .productDesc(l.getProductDesc())
                        .qty(l.getQty())
                        .unit(l.getUnit())
                        .unitPrice(l.getUnitPrice())
                        .currency(l.getCurrency())
                        .totalPrice(l.getTotalPrice())
                        .build())
            .toList();

    return PurchaseOrderResponse.builder()
        .id(po.getId())
        .uid(po.getUid())
        .poNumber(po.getPoNumber())
        .workOrderId(po.getWorkOrderId())
        .tradingPartnerId(po.getTradingPartnerId())
        .supplierQuoteId(po.getSupplierQuoteId())
        .status(po.getStatus())
        .currency(po.getCurrency())
        .paymentTerms(po.getPaymentTerms())
        .expectedDelivery(po.getExpectedDelivery())
        .totalAmount(po.getTotalAmount())
        .revisionNumber(po.getRevisionNumber())
        .notes(po.getNotes())
        .lines(lineResps)
        .build();
  }
}
