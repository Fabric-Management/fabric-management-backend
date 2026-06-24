package com.fabricmanagement.procurement.rfq.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
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
import com.fabricmanagement.procurement.rfq.dto.SupplierRFQResponse;
import com.fabricmanagement.procurement.rfq.infra.repository.SupplierRFQRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

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
    mockRfq.setModuleType(SupplierRFQModuleType.FIBER);
    mockRfq.setRfqType(SupplierRFQType.PURCHASE);
    mockRfq.setDeadline(Instant.now().plus(7, ChronoUnit.DAYS));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("Should get RFQ by id")
  void shouldGetRfqById() {
    when(rfqRepository.findById(rfqId)).thenReturn(Optional.of(mockRfq));

    SupplierRFQResponse response = rfqService.getRfq(rfqId);

    assertEquals(rfqId, response.getId());
    assertEquals("RFQ-2026-TEST", response.getRfqNumber());
    verify(rfqRepository).findById(rfqId);
  }

  @Test
  @DisplayName("Should throw when RFQ is not found")
  void shouldThrowWhenRfqNotFound() {
    when(rfqRepository.findById(rfqId)).thenReturn(Optional.empty());

    NotFoundException ex = assertThrows(NotFoundException.class, () -> rfqService.getRfq(rfqId));

    assertEquals("SupplierRFQ not found with id: " + rfqId, ex.getMessage());
  }

  @Test
  @DisplayName("Should list RFQs as a paged response")
  void shouldListRfqs() {
    Pageable pageable = PageRequest.of(0, 20);
    when(rfqRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(mockRfq), pageable, 1));

    PagedResponse<SupplierRFQResponse> response =
        rfqService.listRfqs(null, SupplierRFQModuleType.FIBER, pageable);

    assertEquals(1, response.getContent().size());
    assertEquals(SupplierRFQModuleType.FIBER, response.getContent().get(0).getModuleType());
    assertEquals(0, response.getPage());
    assertEquals(20, response.getSize());
  }

  @Test
  @DisplayName("Should build status filter when listing RFQs")
  @SuppressWarnings({"unchecked", "rawtypes"})
  void shouldBuildStatusFilterWhenListingRfqs() {
    Pageable pageable = PageRequest.of(0, 20);
    when(rfqRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(mockRfq), pageable, 1));

    rfqService.listRfqs(SupplierRFQStatus.SENT, null, pageable);

    ArgumentCaptor<Specification<SupplierRFQ>> specCaptor =
        ArgumentCaptor.forClass(Specification.class);
    verify(rfqRepository).findAll(specCaptor.capture(), any(Pageable.class));

    Root root = org.mockito.Mockito.mock(Root.class);
    CriteriaQuery query = org.mockito.Mockito.mock(CriteriaQuery.class);
    CriteriaBuilder cb = org.mockito.Mockito.mock(CriteriaBuilder.class);
    Path tenantPath = org.mockito.Mockito.mock(Path.class);
    Path activePath = org.mockito.Mockito.mock(Path.class);
    Path statusPath = org.mockito.Mockito.mock(Path.class);
    Predicate tenantPredicate = org.mockito.Mockito.mock(Predicate.class);
    Predicate activePredicate = org.mockito.Mockito.mock(Predicate.class);
    Predicate statusPredicate = org.mockito.Mockito.mock(Predicate.class);
    Predicate combinedPredicate = org.mockito.Mockito.mock(Predicate.class);

    when(root.get("tenantId")).thenReturn(tenantPath);
    when(root.get("isActive")).thenReturn(activePath);
    when(root.get("status")).thenReturn(statusPath);
    when(cb.equal(tenantPath, tenantId)).thenReturn(tenantPredicate);
    when(cb.isTrue(activePath)).thenReturn(activePredicate);
    when(cb.equal(statusPath, SupplierRFQStatus.SENT)).thenReturn(statusPredicate);
    when(cb.and(any(Predicate[].class))).thenReturn(combinedPredicate);

    Predicate predicate = specCaptor.getValue().toPredicate(root, query, cb);

    assertEquals(combinedPredicate, predicate);
    verify(cb).equal(statusPath, SupplierRFQStatus.SENT);
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
    AddRfqLineRequest req = new AddRfqLineRequest(null, null, new BigDecimal("1000"), "KG", null);

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
    AddRfqLineRequest req = new AddRfqLineRequest(null, null, new BigDecimal("100"), "KG", null);

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
