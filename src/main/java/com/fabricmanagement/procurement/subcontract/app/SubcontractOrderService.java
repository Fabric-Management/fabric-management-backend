package com.fabricmanagement.procurement.subcontract.app;

import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import com.fabricmanagement.procurement.subcontract.domain.SubcontractOrder;
import com.fabricmanagement.procurement.subcontract.domain.SubcontractOrderStatus;
import com.fabricmanagement.procurement.subcontract.dto.CreateSubcontractOrderRequest;
import com.fabricmanagement.procurement.subcontract.dto.SubcontractOrderResponse;
import com.fabricmanagement.procurement.subcontract.infra.repository.SubcontractOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SubcontractOrderService {

  private final SubcontractOrderRepository scRepository;

  public SubcontractOrderResponse getSubcontractOrder(UUID id) {
    return mapToResponse(findEntityById(id));
  }

  @Transactional
  public SubcontractOrderResponse createSubcontractOrder(CreateSubcontractOrderRequest request) {
    SubcontractOrder sc =
        SubcontractOrder.builder()
            .scNumber(generateScNumber())
            .workOrderId(request.getWorkOrderId())
            .tradingPartnerId(request.getTradingPartnerId())
            .status(SubcontractOrderStatus.DRAFT)
            .materialId(request.getMaterialId())
            .materialSentQty(request.getMaterialSentQty())
            .unit(request.getUnit())
            .agreedUnitPrice(request.getAgreedUnitPrice())
            .currency(request.getCurrency())
            .expectedReturnDate(request.getExpectedReturnDate())
            .notes(request.getNotes())
            .build();

    SubcontractOrder saved = scRepository.save(sc);
    log.info(
        "SubcontractOrder created: {} [workOrder={}]", saved.getScNumber(), saved.getWorkOrderId());
    return mapToResponse(saved);
  }

  /**
   * Transitions the SubcontractOrder status (state machine enforced).
   *
   * <p>Special: when newStatus=COMPLETED, actualReturnedQty must be provided. wasteQty is computed
   * automatically: materialSentQty − actualReturnedQty.
   */
  @Transactional
  public SubcontractOrderResponse changeStatus(
      UUID id, SubcontractOrderStatus newStatus, BigDecimal actualReturnedQty) {
    SubcontractOrder sc = findEntityById(id);

    if (!sc.getStatus().canTransitionTo(newStatus)) {
      throw new ProcurementDomainException(
          String.format(
              "Invalid SubcontractOrder status transition: %s → %s (SC: %s)",
              sc.getStatus(), newStatus, sc.getScNumber()));
    }

    if (newStatus == SubcontractOrderStatus.COMPLETED) {
      if (actualReturnedQty == null || actualReturnedQty.compareTo(BigDecimal.ZERO) <= 0) {
        throw new ProcurementDomainException(
            "actualReturnedQty is required when completing a SubcontractOrder: "
                + sc.getScNumber());
      }
      sc.setActualReturnedQty(actualReturnedQty);
      BigDecimal sent = sc.getMaterialSentQty() != null ? sc.getMaterialSentQty() : BigDecimal.ZERO;
      BigDecimal waste = sent.subtract(actualReturnedQty);
      sc.setWasteQty(waste.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : waste);

      log.info(
          "SubcontractOrder {} COMPLETED: sent={}, returned={}, waste={}",
          sc.getScNumber(),
          sent,
          actualReturnedQty,
          sc.getWasteQty());
    }

    sc.setStatus(newStatus);
    SubcontractOrder saved = scRepository.save(sc);
    return mapToResponse(saved);
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private SubcontractOrder findEntityById(UUID id) {
    return scRepository
        .findById(id)
        .orElseThrow(
            () -> new ProcurementDomainException("SubcontractOrder not found with id: " + id));
  }

  private String generateScNumber() {
    String year = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy"));
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    return String.format("SC-%s-%s", year, suffix);
  }

  private SubcontractOrderResponse mapToResponse(SubcontractOrder sc) {
    return SubcontractOrderResponse.builder()
        .id(sc.getId())
        .uid(sc.getUid())
        .scNumber(sc.getScNumber())
        .workOrderId(sc.getWorkOrderId())
        .tradingPartnerId(sc.getTradingPartnerId())
        .status(sc.getStatus())
        .materialId(sc.getMaterialId())
        .materialSentQty(sc.getMaterialSentQty())
        .unit(sc.getUnit())
        .actualReturnedQty(sc.getActualReturnedQty())
        .wasteQty(sc.getWasteQty())
        .agreedUnitPrice(sc.getAgreedUnitPrice())
        .currency(sc.getCurrency())
        .expectedReturnDate(sc.getExpectedReturnDate())
        .notes(sc.getNotes())
        .build();
  }
}
