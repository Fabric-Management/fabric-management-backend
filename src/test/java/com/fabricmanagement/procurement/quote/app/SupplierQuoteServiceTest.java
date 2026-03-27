package com.fabricmanagement.procurement.quote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import com.fabricmanagement.procurement.quote.domain.QuoteEntryMethod;
import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteStatus;
import com.fabricmanagement.procurement.quote.dto.AddQuoteLineRequest;
import com.fabricmanagement.procurement.quote.dto.CreateSupplierQuoteRequest;
import com.fabricmanagement.procurement.quote.infra.repository.SupplierQuoteRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
class SupplierQuoteServiceTest {

  @Mock private SupplierQuoteRepository quoteRepository;

  @Mock
  private com.fabricmanagement.common.infrastructure.events.DomainEventPublisher eventPublisher;

  @InjectMocks private SupplierQuoteService quoteService;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID rfqId = UUID.randomUUID();
  private final UUID quoteId = UUID.randomUUID();
  private SupplierQuote mockQuote;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);

    mockQuote = new SupplierQuote();
    mockQuote.setId(quoteId);
    mockQuote.setTenantId(tenantId);
    mockQuote.setRfqId(rfqId);
    mockQuote.setQuoteNumber("SQ-2026-TEST");
    mockQuote.setStatus(SupplierQuoteStatus.RECEIVED);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("Should create Supplier Quote successfully")
  void shouldCreateQuote() {
    CreateSupplierQuoteRequest req = new CreateSupplierQuoteRequest();
    req.setRfqId(rfqId);
    req.setTradingPartnerId(UUID.randomUUID());
    req.setValidUntil(LocalDate.now().plusDays(10));
    req.setCurrency("USD");
    req.setEntryMethod(QuoteEntryMethod.MANUAL_ENTRY);

    when(quoteRepository.save(any(SupplierQuote.class))).thenAnswer(inv -> inv.getArgument(0));

    SupplierQuote created = quoteService.createQuote(req);

    assertNotNull(created);
    assertEquals(tenantId, created.getTenantId());
    assertNotNull(created.getQuoteNumber());
    assertEquals("USD", created.getCurrency());
    assertEquals(QuoteEntryMethod.MANUAL_ENTRY, created.getEntryMethod());
    assertEquals(SupplierQuoteStatus.RECEIVED, created.getStatus());
  }

  @Test
  @DisplayName("Should successfully add line when Quote is in RECEIVED status")
  void shouldAddLineToReceivedQuote() {
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(mockQuote));
    when(quoteRepository.save(any(SupplierQuote.class))).thenAnswer(inv -> inv.getArgument(0));

    // Fix #2 — DTO kullanılıyor
    AddQuoteLineRequest req = new AddQuoteLineRequest();
    req.setRfqLineId(UUID.randomUUID());
    req.setUnitPrice(new BigDecimal("12.50"));
    req.setCurrency("USD");
    req.setQty(new BigDecimal("500"));
    req.setUnit("KG");

    SupplierQuote updated = quoteService.addLine(quoteId, req);

    assertEquals(1, updated.getLines().size());
    assertEquals(new BigDecimal("12.50"), updated.getLines().get(0).getUnitPrice());
    verify(quoteRepository).save(mockQuote);
  }

  @Test
  @DisplayName("Should throw exception when adding line to ACCEPTED quote")
  void shouldThrowWhenAddingLineToAcceptedQuote() {
    mockQuote.setStatus(SupplierQuoteStatus.ACCEPTED);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(mockQuote));

    AddQuoteLineRequest req = new AddQuoteLineRequest();
    req.setRfqLineId(UUID.randomUUID());
    req.setUnitPrice(new BigDecimal("5.00"));
    req.setCurrency("USD");
    req.setQty(new BigDecimal("100"));
    req.setUnit("KG");

    ProcurementDomainException ex =
        assertThrows(ProcurementDomainException.class, () -> quoteService.addLine(quoteId, req));

    assertEquals("Cannot add line to quote in status: ACCEPTED", ex.getMessage());
  }

  @Test
  @DisplayName("Should mark quote as ACCEPTED and auto-reject siblings (Fix #4,#5)")
  void shouldMarkQuoteAsAcceptedAndRejectSiblings() {
    UUID siblingId = UUID.randomUUID();
    SupplierQuote sibling = new SupplierQuote();
    sibling.setId(siblingId);
    sibling.setTenantId(tenantId);
    sibling.setRfqId(rfqId);
    sibling.setQuoteNumber("SQ-2026-SIBLING");
    sibling.setStatus(SupplierQuoteStatus.RECEIVED);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(mockQuote));
    // Fix #5 — sibling sorgusu
    when(quoteRepository.findByRfqIdAndTenantIdAndStatusAndIsActiveTrue(
            rfqId, tenantId, SupplierQuoteStatus.RECEIVED))
        .thenReturn(List.of(mockQuote, sibling));
    when(quoteRepository.save(any(SupplierQuote.class))).thenAnswer(inv -> inv.getArgument(0));

    SupplierQuote updated = quoteService.markAsAccepted(quoteId);

    assertEquals(SupplierQuoteStatus.ACCEPTED, updated.getStatus());
    assertEquals(SupplierQuoteStatus.REJECTED, sibling.getStatus()); // Fix #5
  }

  @Test
  @DisplayName("Should throw when accepting already REJECTED quote (Fix #4)")
  void shouldThrowWhenAcceptingRejectedQuote() {
    mockQuote.setStatus(SupplierQuoteStatus.REJECTED);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(mockQuote));

    ProcurementDomainException ex =
        assertThrows(ProcurementDomainException.class, () -> quoteService.markAsAccepted(quoteId));

    assertEquals("Cannot accept quote in status: REJECTED", ex.getMessage());
  }

  @Test
  @DisplayName("Should mark quote as REJECTED")
  void shouldMarkQuoteAsRejected() {
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(mockQuote));
    when(quoteRepository.save(any(SupplierQuote.class))).thenAnswer(inv -> inv.getArgument(0));

    SupplierQuote updated = quoteService.markAsRejected(quoteId);

    assertEquals(SupplierQuoteStatus.REJECTED, updated.getStatus());
  }

  @Test
  @DisplayName("Should throw when rejecting already ACCEPTED quote (Fix #4)")
  void shouldThrowWhenRejectingAcceptedQuote() {
    mockQuote.setStatus(SupplierQuoteStatus.ACCEPTED);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(mockQuote));

    ProcurementDomainException ex =
        assertThrows(ProcurementDomainException.class, () -> quoteService.markAsRejected(quoteId));

    assertEquals("Cannot reject quote in status: ACCEPTED", ex.getMessage());
  }
}
