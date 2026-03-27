package com.fabricmanagement.procurement.rfq.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import com.fabricmanagement.procurement.rfq.domain.RfqRecipientStatus;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQ;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQLine;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQModuleType;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQRecipient;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQStatus;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQType;
import com.fabricmanagement.procurement.rfq.dto.AddRecipientRequest;
import com.fabricmanagement.procurement.rfq.dto.AddRfqLineRequest;
import com.fabricmanagement.procurement.rfq.dto.CreateSupplierRFQRequest;
import com.fabricmanagement.procurement.rfq.infra.repository.SupplierRFQRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SupplierRFQServiceTest {

  @Mock private SupplierRFQRepository rfqRepository;

  @Mock
  private com.fabricmanagement.common.infrastructure.events.DomainEventPublisher eventPublisher;

  @InjectMocks private SupplierRFQService rfqService;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID rfqId = UUID.randomUUID();
  private SupplierRFQ mockRfq;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);

    mockRfq = new SupplierRFQ();
    mockRfq.setId(rfqId);
    mockRfq.setTenantId(tenantId);
    mockRfq.setRfqNumber("RFQ-2026-TEST");
    mockRfq.setStatus(SupplierRFQStatus.DRAFT);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("Should create RFQ successfully")
  void shouldCreateRfq() {
    CreateSupplierRFQRequest req = new CreateSupplierRFQRequest();
    req.setWorkOrderId(UUID.randomUUID());
    req.setModuleType(SupplierRFQModuleType.FIBER); // Fix #13 — artık enum
    req.setRfqType(SupplierRFQType.PURCHASE);
    req.setDeadline(Instant.now().plus(7, ChronoUnit.DAYS));

    when(rfqRepository.save(any(SupplierRFQ.class))).thenAnswer(inv -> inv.getArgument(0));

    SupplierRFQ created = rfqService.createRfq(req);

    assertNotNull(created);
    assertEquals(tenantId, created.getTenantId());
    assertNotNull(created.getRfqNumber());
    assertEquals(SupplierRFQModuleType.FIBER, created.getModuleType());
    assertEquals(SupplierRFQStatus.DRAFT, created.getStatus());
  }

  @Test
  @DisplayName("Should successfully add line when RFQ is in DRAFT status")
  void shouldAddLineToDraftRfq() {
    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.of(mockRfq));
    when(rfqRepository.save(any(SupplierRFQ.class))).thenAnswer(inv -> inv.getArgument(0));

    // Fix #2 — DTO ile gönderilir artık
    AddRfqLineRequest req = new AddRfqLineRequest();
    req.setRequestedQty(new BigDecimal("1000"));
    req.setUnit("KG");

    SupplierRFQ updated = rfqService.addLine(rfqId, req);

    assertEquals(1, updated.getLines().size());
    assertEquals(new BigDecimal("1000"), updated.getLines().get(0).getRequestedQty());
    verify(rfqRepository).save(mockRfq);
  }

  @Test
  @DisplayName("Should throw exception when adding line to non-DRAFT RFQ")
  void shouldThrowWhenAddingLineToSentRfq() {
    mockRfq.setStatus(SupplierRFQStatus.SENT);

    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.of(mockRfq));

    // Fix #6 — DRAFT guard'ı "not found" benzeri bir mesaj döner değil, statü mesajı döner
    AddRfqLineRequest req = new AddRfqLineRequest();
    req.setRequestedQty(new BigDecimal("100"));
    req.setUnit("KG");

    ProcurementDomainException ex =
        assertThrows(ProcurementDomainException.class, () -> rfqService.addLine(rfqId, req));

    assertEquals("Operation not allowed on RFQ in status: SENT", ex.getMessage());
  }

  @Test
  @DisplayName("Should successfully add recipient when RFQ is in DRAFT status")
  void shouldAddRecipientToDraftRfq() {
    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.of(mockRfq));
    when(rfqRepository.save(any(SupplierRFQ.class))).thenAnswer(inv -> inv.getArgument(0));

    UUID partnerId = UUID.randomUUID();
    // Fix #10 — AddRecipientRequest DTO
    AddRecipientRequest req = new AddRecipientRequest();
    req.setTradingPartnerId(partnerId);

    SupplierRFQ updated = rfqService.addRecipient(rfqId, req);

    assertEquals(1, updated.getRecipients().size());
    assertEquals(partnerId, updated.getRecipients().get(0).getTradingPartnerId());
    // Fix #9 — Default PENDING
    assertEquals(RfqRecipientStatus.PENDING, updated.getRecipients().get(0).getStatus());
  }

  @Test
  @DisplayName("Should throw exception when sending empty RFQ (no lines)")
  void shouldThrowWhenSendingEmptyRfq() {
    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.of(mockRfq));

    ProcurementDomainException ex =
        assertThrows(ProcurementDomainException.class, () -> rfqService.sendRfq(rfqId));

    assertEquals("Cannot send an RFQ without lines.", ex.getMessage());
  }

  @Test
  @DisplayName("Should send RFQ successfully: recipients get sentAt + status SENT")
  void shouldSendRfqSuccessfully() {
    SupplierRFQLine line = new SupplierRFQLine();
    SupplierRFQRecipient recipient = new SupplierRFQRecipient();
    recipient.setTradingPartnerId(
        UUID.randomUUID()); // RfqSentEvent List.copyOf null-element NPE'yi önler
    mockRfq.addLine(line);
    mockRfq.addRecipient(recipient);

    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.of(mockRfq));
    when(rfqRepository.save(any(SupplierRFQ.class))).thenAnswer(inv -> inv.getArgument(0));

    SupplierRFQ updated = rfqService.sendRfq(rfqId);

    assertEquals(SupplierRFQStatus.SENT, updated.getStatus());
    // Fix #8 — sentAt set edilmeli
    assertNotNull(updated.getRecipients().get(0).getSentAt());
    // Fix #9 — SENT yapılmış olmalı
    assertEquals(RfqRecipientStatus.SENT, updated.getRecipients().get(0).getStatus());
  }

  @Test
  @DisplayName("Should throw when trying to send an already SENT RFQ (Fix #6)")
  void shouldThrowWhenSendingAlreadySentRfq() {
    mockRfq.setStatus(SupplierRFQStatus.SENT);

    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.of(mockRfq));

    ProcurementDomainException ex =
        assertThrows(ProcurementDomainException.class, () -> rfqService.sendRfq(rfqId));

    assertEquals("Operation not allowed on RFQ in status: SENT", ex.getMessage());
  }
}
