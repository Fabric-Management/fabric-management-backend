package com.fabricmanagement.procurement.purchaseorder.api.query;

import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public contract for other modules (e.g. GoodsReceipt) to query PurchaseOrder information without
 * deeply coupling to its internal domain or repositories.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseOrderQueryService {

  private final PurchaseOrderRepository poRepository;

  /** Retrieves the logical PO number for a given ID. Used for barcode generation. */
  public String getPurchaseOrderNumber(UUID id) {
    return poRepository
        .findById(id)
        .orElseThrow(() -> new ProcurementDomainException("PurchaseOrder not found with id: " + id))
        .getPoNumber();
  }
}
