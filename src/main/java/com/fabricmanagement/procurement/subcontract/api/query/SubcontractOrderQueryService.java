package com.fabricmanagement.procurement.subcontract.api.query;

import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import com.fabricmanagement.procurement.subcontract.infra.repository.SubcontractOrderRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public contract for other modules (e.g. GoodsReceipt) to query SubcontractOrder information
 * without deeply coupling to its internal domain or repositories.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubcontractOrderQueryService {

  private final SubcontractOrderRepository scRepository;

  /** Retrieves the logical SC number for a given ID. Used for barcode generation. */
  public String getSubcontractOrderNumber(UUID tenantId, UUID id) {
    return scRepository
        .findByIdAndTenantIdAndIsActiveTrue(id, tenantId)
        .orElseThrow(
            () -> new ProcurementDomainException("SubcontractOrder not found with id: " + id))
        .getScNumber();
  }

  public record SubcontractOutputInfo(
      String scNumber,
      UUID outputProductId,
      ProductType outputProductType,
      String outputUnit,
      UUID batchId) {}

  /** Retrieves full output information needed to process goods receipts from a subcontractor. */
  public SubcontractOutputInfo getSubcontractOutputInfo(UUID tenantId, UUID subcontractOrderId) {
    com.fabricmanagement.procurement.subcontract.domain.SubcontractOrder sc =
        scRepository
            .findByIdAndTenantIdAndIsActiveTrue(subcontractOrderId, tenantId)
            .orElseThrow(
                () ->
                    new ProcurementDomainException(
                        "SubcontractOrder not found: " + subcontractOrderId));

    return new SubcontractOutputInfo(
        sc.getScNumber(),
        sc.getOutputProductId(),
        sc.getOutputProductType(),
        sc.getOutputUnit(),
        sc.getBatchId());
  }
}
