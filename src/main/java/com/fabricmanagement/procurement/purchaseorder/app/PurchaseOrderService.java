package com.fabricmanagement.procurement.purchaseorder.app;

import com.fabricmanagement.common.infrastructure.approval.ApprovalPort;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import com.fabricmanagement.procurement.purchaseorder.app.validation.PurchaseOrderValidationEngine;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrder;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderLine;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderModuleType;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderStatus;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.GenericPurchaseSpecs;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.PurchaseOrderSpecs;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PurchaseOrderService {

  private static final String ENTITY_TYPE = "PURCHASE_ORDER";

  private final PurchaseOrderRepository poRepository;
  private final PurchaseOrderLineRepository lineRepository;
  private final PurchaseOrderValidationEngine validationEngine;
  private final ApprovalPort approvalPort;

  public PurchaseOrderResponse getPurchaseOrder(UUID id) {
    PurchaseOrder po = findEntityById(id);
    List<PurchaseOrderLine> lines =
        lineRepository.findByPurchaseOrderIdAndIsActiveTrueOrderByCreatedAtAsc(id);
    return mapToResponse(po, lines);
  }

  public PagedResponse<PurchaseOrderResponse> listPurchaseOrders(
      PurchaseOrderModuleType moduleType, PurchaseOrderStatus status, Pageable pageable) {

    UUID tenantId = TenantContext.getCurrentTenantId();

    Specification<PurchaseOrder> spec =
        (root, query, cb) -> {
          var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
          predicates.add(cb.equal(root.get("tenantId"), tenantId));
          predicates.add(cb.isTrue(root.get("isActive")));

          if (moduleType != null) {
            predicates.add(cb.equal(root.get("moduleType"), moduleType));
          }
          if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
          }

          return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

    Page<PurchaseOrder> poPage = poRepository.findAll(spec, pageable);

    return PagedResponse.from(poPage, this::mapToSummaryResponse);
  }

  /** Creates a PurchaseOrder in DRAFT state with all lines. Total is computed automatically. */
  @Transactional
  public PurchaseOrderResponse createPurchaseOrder(CreatePurchaseOrderRequest request) {
    PurchaseOrderModuleType type =
        request.getModuleType() != null ? request.getModuleType() : PurchaseOrderModuleType.GENERIC;
    PurchaseOrderSpecs specs =
        request.getModuleSpecs() != null
            ? request.getModuleSpecs()
            : new GenericPurchaseSpecs(null);

    validationEngine.validateOnCreate(type, specs);

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
            .moduleType(type)
            .moduleSpecs(specs)
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
              .moduleSpecs(
                  lineReq.getModuleSpecs() != null
                      ? lineReq.getModuleSpecs()
                      : new GenericPurchaseSpecs(null))
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

    PurchaseOrderStatus effectiveStatus = newStatus;

    // Completeness validation before sending to supplier —
    // runs for both DRAFT→SENT and PENDING_APPROVAL→SENT (safety net if specs
    // were modified during approval period)
    if (effectiveStatus == PurchaseOrderStatus.SENT) {
      validationEngine.validateOnConfirm(po.getModuleType(), po.getModuleSpecs());

      // Approval check only when transitioning from DRAFT (not from PENDING_APPROVAL callback)
      if (po.getStatus() == PurchaseOrderStatus.DRAFT) {
        boolean needsApproval =
            approvalPort.requiresApproval(
                TenantContext.getCurrentTenantId(),
                TenantContext.getCurrentUserId(),
                ENTITY_TYPE,
                po.getId(),
                48); // 48 hours expiry

        if (needsApproval) {
          log.info(
              "PurchaseOrder {} requires approval, moving to PENDING_APPROVAL", po.getPoNumber());
          effectiveStatus = PurchaseOrderStatus.PENDING_APPROVAL;
        }
      }
    }

    po.setStatus(effectiveStatus);
    PurchaseOrder saved = poRepository.save(po);

    List<PurchaseOrderLine> lines =
        lineRepository.findByPurchaseOrderIdAndIsActiveTrueOrderByCreatedAtAsc(id);

    log.info(
        "PurchaseOrder {} status: {} → {} (requested: {})",
        saved.getPoNumber(),
        po.getStatus(),
        effectiveStatus,
        newStatus);
    return mapToResponse(saved, lines);
  }

  /**
   * Transitions the PurchaseOrder status as SystemUser — internal use only.
   *
   * <p>Called by {@link
   * com.fabricmanagement.procurement.purchaseorder.app.listener.PurchaseOrderApprovalEventListener}
   * when the approval system publishes approved/rejected events. SystemUser.ID bypasses approval
   * policy in ApprovalGuardService (trusted user level), preventing re-entry loops.
   *
   * <p><b>Do NOT expose via Controller.</b>
   */
  @Transactional
  public PurchaseOrderResponse changeStatusAsSystem(UUID id, PurchaseOrderStatus newStatus) {
    return TenantContext.executeInTenantContext(
        TenantContext.getCurrentTenantId(),
        () -> {
          TenantContext.setCurrentUserId(SystemUser.ID);
          return changeStatus(id, newStatus);
        });
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
                        .moduleSpecs(l.getModuleSpecs())
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
        .moduleType(po.getModuleType())
        .moduleSpecs(po.getModuleSpecs())
        .lines(lineResps)
        .build();
  }

  private PurchaseOrderResponse mapToSummaryResponse(PurchaseOrder po) {
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
        .moduleType(po.getModuleType())
        .moduleSpecs(po.getModuleSpecs())
        .lines(null) // Summary DTO mantığıyla null bırakılır
        .build();
  }
}
