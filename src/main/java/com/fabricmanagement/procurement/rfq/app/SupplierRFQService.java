package com.fabricmanagement.procurement.rfq.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import com.fabricmanagement.procurement.rfq.domain.RfqRecipientStatus;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQ;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQLine;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQRecipient;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQStatus;
import com.fabricmanagement.procurement.rfq.dto.AddRecipientRequest;
import com.fabricmanagement.procurement.rfq.dto.AddRfqLineRequest;
import com.fabricmanagement.procurement.rfq.dto.CreateSupplierRFQRequest;
import com.fabricmanagement.procurement.rfq.infra.repository.SupplierRFQRepository;
import java.time.Instant;
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
@Transactional(readOnly = true) // Fix #11 — sınıf seviyesinde readOnly, write metodları override
public class SupplierRFQService {

  private final SupplierRFQRepository rfqRepository;

  @Transactional
  public SupplierRFQ createRfq(CreateSupplierRFQRequest req) {
    SupplierRFQ rfq = new SupplierRFQ();
    rfq.setTenantId(TenantContext.getCurrentTenantId());

    // Fix #7 — Yıl dinamik hesaplanıyor (hard-coded 2026 değil)
    String year = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy"));
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    rfq.setRfqNumber(String.format("RFQ-%s-%s", year, suffix));

    rfq.setWorkOrderId(req.getWorkOrderId());
    rfq.setModuleType(req.getModuleType());
    rfq.setRfqType(req.getRfqType());
    rfq.setDeadline(req.getDeadline());
    rfq.setNotes(req.getNotes());
    rfq.setStatus(SupplierRFQStatus.DRAFT);

    SupplierRFQ saved = rfqRepository.save(rfq);
    log.info(
        "SupplierRFQ created: {} [type={}, wo={}]",
        saved.getRfqNumber(),
        req.getRfqType(),
        req.getWorkOrderId());
    return saved;
  }

  @Transactional
  public SupplierRFQ addLine(UUID rfqId, AddRfqLineRequest req) {
    SupplierRFQ rfq = getActiveDraftRfq(rfqId);

    // Fix #2 — DTO'dan entity oluşturuyoruz (client entity göndermez)
    SupplierRFQLine line = new SupplierRFQLine();
    line.setTenantId(rfq.getTenantId());
    line.setMaterialId(req.getMaterialId());
    line.setProductDesc(req.getProductDesc());
    line.setRequestedQty(req.getRequestedQty());
    line.setUnit(req.getUnit());
    if (req.getModuleSpecs() != null) {
      line.setModuleSpecs(req.getModuleSpecs());
    }

    rfq.addLine(line);
    return rfqRepository.save(rfq);
  }

  @Transactional
  public SupplierRFQ addRecipient(UUID rfqId, AddRecipientRequest req) {
    SupplierRFQ rfq = getActiveDraftRfq(rfqId);

    // Fix #10 — DTO kullanılıyor, ayrıca Fix #9 — PENDING ile başlatılıyor
    SupplierRFQRecipient recipient = new SupplierRFQRecipient();
    recipient.setTenantId(rfq.getTenantId());
    recipient.setTradingPartnerId(req.getTradingPartnerId());
    recipient.setResponseDeadline(req.getResponseDeadline()); // opsiyonel override
    recipient.setStatus(RfqRecipientStatus.PENDING);

    rfq.addRecipient(recipient);
    return rfqRepository.save(rfq);
  }

  @Transactional
  public SupplierRFQ sendRfq(UUID rfqId) {
    // Fix #6 — Sadece DRAFT RFQ gönderilebilir
    SupplierRFQ rfq = getActiveDraftRfq(rfqId);

    if (rfq.getLines().isEmpty()) {
      throw new ProcurementDomainException("Cannot send an RFQ without lines.");
    }
    if (rfq.getRecipients().isEmpty()) {
      throw new ProcurementDomainException("Cannot send an RFQ without recipients.");
    }

    // Fix #8 — Her alıcıya sentAt set et ve statüyü SENT yap
    Instant now = Instant.now();
    rfq.getRecipients()
        .forEach(
            r -> {
              r.setSentAt(now);
              r.setStatus(RfqRecipientStatus.SENT);
            });

    rfq.setStatus(SupplierRFQStatus.SENT);
    // TODO(phase-7): NotificationHub event emit — RfqSentEvent per recipient

    SupplierRFQ saved = rfqRepository.save(rfq);
    log.info(
        "SupplierRFQ sent: {} to {} recipients",
        saved.getRfqNumber(),
        saved.getRecipients().size());
    return saved;
  }

  // ── Private Helpers ────────────────────────────────────────────────────────

  /**
   * Fix #6 — Sadece tenant'a ait, aktif ve DRAFT statüdeki RFQ'ları döner. SENT/COMPLETED/CANCELLED
   * olan RFQ'lara mutasyon yapılamaz.
   */
  private SupplierRFQ getActiveDraftRfq(UUID rfqId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    SupplierRFQ rfq =
        rfqRepository
            .findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId)
            .orElseThrow(() -> new ProcurementDomainException("SupplierRFQ not found"));

    if (rfq.getStatus() != SupplierRFQStatus.DRAFT) {
      throw new ProcurementDomainException(
          "Operation not allowed on RFQ in status: " + rfq.getStatus());
    }

    return rfq;
  }
}
