package com.fabricmanagement.procurement.subcontract.api.query;

import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import com.fabricmanagement.procurement.subcontract.infra.repository.SubcontractOrderRepository;
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
  public String getSubcontractOrderNumber(UUID id) {
    return scRepository
        .findById(id)
        .orElseThrow(
            () -> new ProcurementDomainException("SubcontractOrder not found with id: " + id))
        .getScNumber();
  }
}
