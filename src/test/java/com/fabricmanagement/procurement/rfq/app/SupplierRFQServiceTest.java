package com.fabricmanagement.procurement.rfq.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQ;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQLine;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQStatus;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQType;
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
    req.setModuleType("FIBER");
    req.setRfqType(SupplierRFQType.PURCHASE);
    req.setDeadline(Instant.now().plus(7, ChronoUnit.DAYS));

    when(rfqRepository.save(any(SupplierRFQ.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SupplierRFQ created = rfqService.createRfq(req);

    assertNotNull(created);
    assertEquals(tenantId, created.getTenantId());
    assertNotNull(created.getRfqNumber());
    assertEquals("FIBER", created.getModuleType());
    assertEquals(SupplierRFQStatus.DRAFT, created.getStatus());
  }

  @Test
  @DisplayName("Should successfully add line when RFQ is in DRAFT status")
  void shouldAddLineToDraftRfq() {
    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.of(mockRfq));
    when(rfqRepository.save(any(SupplierRFQ.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SupplierRFQLine line = new SupplierRFQLine();
    line.setRequestedQty(new BigDecimal("1000"));
    line.setUnit("KG");

    SupplierRFQ updated = rfqService.addLine(rfqId, line);

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

    SupplierRFQLine line = new SupplierRFQLine();

    ProcurementDomainException ex =
        assertThrows(ProcurementDomainException.class, () -> rfqService.addLine(rfqId, line));

    assertEquals("Cannot add line to RFQ in status: SENT", ex.getMessage());
  }

  @Test
  @DisplayName("Should successfully add recipient when RFQ is in DRAFT status")
  void shouldAddRecipientToDraftRfq() {
    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.of(mockRfq));
    when(rfqRepository.save(any(SupplierRFQ.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    UUID partnerId = UUID.randomUUID();
    SupplierRFQ updated = rfqService.addRecipient(rfqId, partnerId);

    assertEquals(1, updated.getRecipients().size());
    assertEquals(partnerId, updated.getRecipients().get(0).getTradingPartnerId());
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
  @DisplayName("Should send RFQ successfully when validation passes")
  void shouldSendRfqSuccessfully() {
    // Add one line and one recipient
    mockRfq.getLines().add(new SupplierRFQLine());
    mockRfq.addRecipient(new com.fabricmanagement.procurement.rfq.domain.SupplierRFQRecipient());

    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.of(mockRfq));
    when(rfqRepository.save(any(SupplierRFQ.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SupplierRFQ updated = rfqService.sendRfq(rfqId);

    assertEquals(SupplierRFQStatus.SENT, updated.getStatus());
  }
}
