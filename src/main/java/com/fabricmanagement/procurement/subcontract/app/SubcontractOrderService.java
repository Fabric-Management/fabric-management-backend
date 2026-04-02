package com.fabricmanagement.procurement.subcontract.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import com.fabricmanagement.procurement.subcontract.domain.SubcontractOrder;
import com.fabricmanagement.procurement.subcontract.domain.SubcontractOrderStatus;
import com.fabricmanagement.procurement.subcontract.dto.CreateSubcontractOrderRequest;
import com.fabricmanagement.procurement.subcontract.dto.SubcontractOrderResponse;
import com.fabricmanagement.procurement.subcontract.dto.UpdateSubcontractOrderRequest;
import com.fabricmanagement.procurement.subcontract.infra.repository.SubcontractOrderRepository;
import com.fabricmanagement.production.masterdata.material.api.facade.MaterialFacade;
import com.fabricmanagement.production.masterdata.material.dto.MaterialDto;
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
  private final MaterialFacade materialFacade;

  public SubcontractOrderResponse getSubcontractOrder(UUID id) {
    return mapToResponse(findEntityById(id));
  }

  @Transactional
  public SubcontractOrderResponse createSubcontractOrder(CreateSubcontractOrderRequest request) {
    UUID tenantId = TenantContext.requireTenantId();

    MaterialDto inputMaterial = null;
    if (request.getInputMaterialId() != null) {
      inputMaterial =
          materialFacade
              .findById(tenantId, request.getInputMaterialId())
              .orElseThrow(() -> new ProcurementDomainException("Input material not found"));
    }

    MaterialDto outputMaterial = null;
    if (request.getOutputMaterialId() != null) {
      outputMaterial =
          materialFacade
              .findById(tenantId, request.getOutputMaterialId())
              .orElseThrow(() -> new ProcurementDomainException("Output material not found"));
    }

    SubcontractOrder sc =
        SubcontractOrder.create(
            tenantId,
            generateScNumber(),
            request.getWorkOrderId(),
            null, // batchId currently nullable/null
            request.getTradingPartnerId(),
            request.getInputMaterialId(),
            inputMaterial != null ? inputMaterial.getMaterialType() : null,
            request.getOutputMaterialId(),
            outputMaterial != null ? outputMaterial.getMaterialType() : null,
            request.getExpectedOutputQty(),
            outputMaterial != null ? outputMaterial.getUnit() : null,
            request.getMaterialSentQty(),
            inputMaterial != null ? inputMaterial.getUnit() : null,
            request.getAgreedUnitPrice(),
            request.getCurrency(),
            request.getExpectedReturnDate(),
            request.getNotes());

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
    UUID tenantId = TenantContext.requireTenantId();
    return scRepository
        .findByIdAndTenantIdAndIsActiveTrue(id, tenantId)
        .orElseThrow(
            () -> new ProcurementDomainException("SubcontractOrder not found with id: " + id));
  }

  @Transactional
  public SubcontractOrderResponse updateDraft(UUID id, UpdateSubcontractOrderRequest request) {
    SubcontractOrder sc = findEntityById(id);
    if (sc.getStatus() != SubcontractOrderStatus.DRAFT) {
      throw new ProcurementDomainException("Only DRAFT orders can be updated");
    }

    if (request.getOutputMaterialId() != null) {
      MaterialDto outputMaterial =
          materialFacade
              .findById(sc.getTenantId(), request.getOutputMaterialId())
              .orElseThrow(() -> new ProcurementDomainException("Output material not found"));
      sc.setOutputMaterialId(outputMaterial.getId());
      sc.setOutputMaterialType(outputMaterial.getMaterialType());
      sc.setOutputUnit(outputMaterial.getUnit());
    }

    if (request.getExpectedOutputQty() != null) {
      sc.setExpectedOutputQty(request.getExpectedOutputQty());
    }
    if (request.getAgreedUnitPrice() != null) {
      sc.setAgreedUnitPrice(request.getAgreedUnitPrice());
    }
    if (request.getCurrency() != null) {
      sc.setCurrency(request.getCurrency());
    }
    if (request.getExpectedReturnDate() != null) {
      sc.setExpectedReturnDate(request.getExpectedReturnDate());
    }
    if (request.getNotes() != null) {
      sc.setNotes(request.getNotes());
    }

    SubcontractOrder saved = scRepository.save(sc);
    return mapToResponse(saved);
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
        .inputMaterialId(sc.getInputMaterialId())
        .inputMaterialType(sc.getInputMaterialType())
        .outputMaterialId(sc.getOutputMaterialId())
        .outputMaterialType(sc.getOutputMaterialType())
        .materialSentQty(sc.getMaterialSentQty())
        .unit(sc.getUnit())
        .expectedOutputQty(sc.getExpectedOutputQty())
        .outputUnit(sc.getOutputUnit())
        .actualReturnedQty(sc.getActualReturnedQty())
        .wasteQty(sc.getWasteQty())
        .agreedUnitPrice(sc.getAgreedUnitPrice())
        .currency(sc.getCurrency())
        .expectedReturnDate(sc.getExpectedReturnDate())
        .notes(sc.getNotes())
        .build();
  }
}
