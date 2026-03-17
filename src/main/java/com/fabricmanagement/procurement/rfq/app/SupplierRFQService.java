package com.fabricmanagement.procurement.rfq.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQ;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQLine;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQRecipient;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQStatus;
import com.fabricmanagement.procurement.rfq.dto.CreateSupplierRFQRequest;
import com.fabricmanagement.procurement.rfq.infra.repository.SupplierRFQRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupplierRFQService {

  private final SupplierRFQRepository rfqRepository;

  @Transactional
  public SupplierRFQ createRfq(CreateSupplierRFQRequest req) {
    SupplierRFQ rfq = new SupplierRFQ();
    rfq.setTenantId(TenantContext.getCurrentTenantId());

    // Generate RFQ number
    String uniqueSeq = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    rfq.setRfqNumber("RFQ-2026-" + uniqueSeq);

    rfq.setWorkOrderId(req.getWorkOrderId());
    rfq.setModuleType(req.getModuleType());
    rfq.setRfqType(req.getRfqType());
    rfq.setDeadline(req.getDeadline());
    rfq.setNotes(req.getNotes());
    rfq.setStatus(SupplierRFQStatus.DRAFT);

    return rfqRepository.save(rfq);
  }

  @Transactional
  public SupplierRFQ addLine(UUID rfqId, SupplierRFQLine line) {
    SupplierRFQ rfq = getActiveRfq(rfqId);

    if (rfq.getStatus() != SupplierRFQStatus.DRAFT) {
      throw new ProcurementDomainException("Cannot add line to RFQ in status: " + rfq.getStatus());
    }

    line.setTenantId(rfq.getTenantId());
    rfq.addLine(line);
    return rfqRepository.save(rfq);
  }

  @Transactional
  public SupplierRFQ addRecipient(UUID rfqId, UUID tradingPartnerId) {
    SupplierRFQ rfq = getActiveRfq(rfqId);

    if (rfq.getStatus() != SupplierRFQStatus.DRAFT) {
      throw new ProcurementDomainException(
          "Cannot add recipient to RFQ in status: " + rfq.getStatus());
    }

    SupplierRFQRecipient recipient = new SupplierRFQRecipient();
    recipient.setTenantId(rfq.getTenantId());
    recipient.setTradingPartnerId(tradingPartnerId);

    rfq.addRecipient(recipient);
    return rfqRepository.save(rfq);
  }

  @Transactional
  public SupplierRFQ sendRfq(UUID rfqId) {
    SupplierRFQ rfq = getActiveRfq(rfqId);

    if (rfq.getLines().isEmpty()) {
      throw new ProcurementDomainException("Cannot send an RFQ without lines.");
    }
    if (rfq.getRecipients().isEmpty()) {
      throw new ProcurementDomainException("Cannot send an RFQ without recipients.");
    }

    rfq.setStatus(SupplierRFQStatus.SENT);
    // NotificationHub emit event here

    return rfqRepository.save(rfq);
  }

  private SupplierRFQ getActiveRfq(UUID rfqId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return rfqRepository
        .findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId)
        .orElseThrow(() -> new ProcurementDomainException("SupplierRFQ not found"));
  }
}
