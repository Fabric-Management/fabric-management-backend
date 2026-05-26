package com.fabricmanagement.procurement.purchaseorder.app;

import com.fabricmanagement.common.infrastructure.approval.ApprovalPort;
import com.fabricmanagement.common.infrastructure.persistence.DocumentNumberGenerator;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.common.util.Money;
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
import java.util.List;
import java.util.Objects;
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
  private final DocumentNumberGenerator documentNumberGenerator;

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

    // 1. Save PO header first (with zero total) to obtain the generated ID
    PurchaseOrder po =
        PurchaseOrder.builder()
            .poNumber(generatePoNumber())
            .workOrderId(request.getWorkOrderId())
            .tradingPartnerId(request.getTradingPartnerId())
            .supplierQuoteId(request.getSupplierQuoteId())
            .moduleType(type)
            .moduleSpecs(specs)
            .status(PurchaseOrderStatus.DRAFT)
            .paymentTerms(request.getPaymentTerms())
            .expectedDelivery(request.getExpectedDelivery())
            .totalAmount(Money.zero(request.getCurrency()))
            .notes(request.getNotes())
            .build();

    PurchaseOrder saved = poRepository.save(po);

    // 2. Create and save lines — factory method calls recalculateTotal()
    List<PurchaseOrderLine> lines =
        request.getLines().stream()
            .map(
                lineReq ->
                    PurchaseOrderLine.create(
                        saved.getId(),
                        lineReq.getRfqLineId(),
                        lineReq.getProductId(),
                        lineReq.getProductDesc(),
                        lineReq.getQty(),
                        lineReq.getUnit(),
                        Money.of(lineReq.getUnitPrice(), lineReq.getCurrency()),
                        lineReq.getModuleSpecs()))
            .toList();
    List<PurchaseOrderLine> savedLines = lineRepository.saveAll(lines);

    // 3. Compute header total from saved lines' Money-rounded totalPrice values
    BigDecimal total =
        savedLines.stream()
            .map(PurchaseOrderLine::getTotalPrice)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    saved.updateTotalAmount(Money.of(total, saved.getCurrency()));
    PurchaseOrder finalSaved = poRepository.save(saved);

    log.info(
        "PurchaseOrder created: {} [workOrder={}, supplier={}]",
        finalSaved.getPoNumber(),
        finalSaved.getWorkOrderId(),
        finalSaved.getTradingPartnerId());

    return mapToResponse(finalSaved, savedLines);
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
                48, // 48 hours expiry
                extractAmount(po.getTotalAmount()),
                po.getCurrency());

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

  private String generatePoNumber() {
    UUID tenantId = TenantContext.requireTenantId();
    return documentNumberGenerator.generate(tenantId, ENTITY_TYPE, "PO", LocalDate.now(), 5);
  }

  private PurchaseOrderResponse mapToResponse(PurchaseOrder po, List<PurchaseOrderLine> lines) {
    List<PurchaseOrderResponse.PurchaseOrderLineResponse> lineResps =
        lines.stream()
            .map(
                l ->
                    PurchaseOrderResponse.PurchaseOrderLineResponse.builder()
                        .id(l.getId())
                        .productId(l.getProductId())
                        .productDesc(l.getProductDesc())
                        .qty(l.getQty())
                        .unit(l.getUnit())
                        .unitPrice(extractAmount(l.getUnitPrice()))
                        .currency(extractCurrencyCode(l.getUnitPrice()))
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
        .totalAmount(extractAmount(po.getTotalAmount()))
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
        .totalAmount(extractAmount(po.getTotalAmount()))
        .revisionNumber(po.getRevisionNumber())
        .notes(po.getNotes())
        .moduleType(po.getModuleType())
        .moduleSpecs(po.getModuleSpecs())
        .lines(null) // Summary — lines loaded lazily via separate query
        .build();
  }

  private static BigDecimal extractAmount(Money money) {
    return money != null ? money.getAmount() : null;
  }

  private static String extractCurrencyCode(Money money) {
    return money != null ? money.getCurrency().getCurrencyCode() : null;
  }
}
