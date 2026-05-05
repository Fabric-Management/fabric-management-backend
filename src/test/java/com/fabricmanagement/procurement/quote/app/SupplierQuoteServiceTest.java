package com.fabricmanagement.procurement.quote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.procurement.quote.domain.QuoteEntryMethod;
import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteStatus;
import com.fabricmanagement.procurement.quote.dto.AddQuoteLineRequest;
import com.fabricmanagement.procurement.quote.dto.CreateSupplierQuoteRequest;
import com.fabricmanagement.procurement.quote.dto.SupplierQuoteResponse;
import com.fabricmanagement.procurement.quote.infra.repository.SupplierQuoteRepository;
import com.fabricmanagement.procurement.quote.mapper.SupplierQuoteMapper;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQ;
import com.fabricmanagement.procurement.rfq.infra.repository.SupplierRFQRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
  @Mock private SupplierRFQRepository rfqRepository;
  @Mock private TradingPartnerResolver partnerResolver;
  @Mock private SupplierQuoteMapper quoteMapper;
  @Mock private DomainEventPublisher eventPublisher;

  @InjectMocks private SupplierQuoteService quoteService;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID rfqId = UUID.randomUUID();
  private final UUID quoteId = UUID.randomUUID();
  private final UUID partnerId = UUID.randomUUID();
  private SupplierQuote mockQuote;
  private SupplierQuoteResponse mockResponse;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);

    mockQuote = new SupplierQuote();
    mockQuote.setId(quoteId);
    mockQuote.setTenantId(tenantId);
    mockQuote.setRfqId(rfqId);
    mockQuote.setTradingPartnerId(partnerId);
    mockQuote.setQuoteNumber("SQ-2026-TEST");
    mockQuote.setStatus(SupplierQuoteStatus.RECEIVED);

    mockResponse =
        SupplierQuoteResponse.builder().id(quoteId).status(SupplierQuoteStatus.RECEIVED).build();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("Should create Supplier Quote successfully")
  void shouldCreateQuote() {
    CreateSupplierQuoteRequest req =
        new CreateSupplierQuoteRequest(
            rfqId,
            partnerId,
            LocalDate.now().plusDays(10),
            "USD",
            null,
            null,
            QuoteEntryMethod.MANUAL_ENTRY,
            com.fabricmanagement.procurement.quote.domain.SupplierQuoteModuleType.GENERIC,
            null);

    when(quoteRepository.save(any(SupplierQuote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(quoteMapper.toResponse(any(SupplierQuote.class))).thenReturn(mockResponse);
    when(partnerResolver.resolvePartner(tenantId, partnerId)).thenReturn(Optional.empty());
    SupplierRFQ mockRfq = new SupplierRFQ();
    mockRfq.setModuleType(
        com.fabricmanagement.procurement.rfq.domain.SupplierRFQModuleType.GENERIC);
    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.of(mockRfq));

    SupplierQuoteResponse created = quoteService.createQuote(req);

    assertNotNull(created);
    verify(quoteRepository).save(any(SupplierQuote.class));
    verify(eventPublisher).publish(any());
  }

  @Test
  @DisplayName("Should successfully add line when Quote is in RECEIVED status")
  void shouldAddLineToReceivedQuote() {
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(mockQuote));
    when(quoteRepository.save(any(SupplierQuote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(quoteMapper.toResponse(any(SupplierQuote.class))).thenReturn(mockResponse);

    AddQuoteLineRequest req =
        new AddQuoteLineRequest(
            UUID.randomUUID(),
            new BigDecimal("12.50"),
            "USD",
            new BigDecimal("500"),
            "KG",
            null,
            null,
            null);

    SupplierQuoteResponse updated = quoteService.addLine(quoteId, req);

    assertNotNull(updated);
    assertEquals(1, mockQuote.getLines().size());
    assertEquals(new BigDecimal("12.50"), mockQuote.getLines().get(0).getUnitPrice());
    verify(quoteRepository).save(mockQuote);
  }

  @Test
  @DisplayName("Should throw exception when adding line to ACCEPTED quote")
  void shouldThrowWhenAddingLineToAcceptedQuote() {
    mockQuote.setStatus(SupplierQuoteStatus.ACCEPTED);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(mockQuote));

    AddQuoteLineRequest req =
        new AddQuoteLineRequest(
            UUID.randomUUID(),
            new BigDecimal("5.00"),
            "USD",
            new BigDecimal("100"),
            "KG",
            null,
            null,
            null);

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> quoteService.addLine(quoteId, req));

    assertEquals("Cannot add line to quote in status: ACCEPTED", ex.getMessage());
  }

  @Test
  @DisplayName("Should mark quote as ACCEPTED and auto-reject siblings")
  void shouldMarkQuoteAsAcceptedAndRejectSiblings() {
    UUID siblingId = UUID.randomUUID();
    SupplierQuote sibling = new SupplierQuote();
    sibling.setId(siblingId);
    sibling.setTenantId(tenantId);
    sibling.setRfqId(rfqId);
    sibling.setQuoteNumber("SQ-2026-SIBLING");
    sibling.setStatus(SupplierQuoteStatus.RECEIVED);

    // Allow modification
    List<SupplierQuote> receivedSiblings = new ArrayList<>();
    receivedSiblings.add(mockQuote);
    receivedSiblings.add(sibling);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(mockQuote));

    when(quoteRepository.findByRfqIdAndTenantIdAndStatusAndIsActiveTrue(
            rfqId, tenantId, SupplierQuoteStatus.RECEIVED))
        .thenReturn(receivedSiblings);

    when(quoteRepository.findByRfqIdAndTenantIdAndStatusAndIsActiveTrue(
            rfqId, tenantId, SupplierQuoteStatus.UNDER_REVIEW))
        .thenReturn(new ArrayList<>());

    when(quoteRepository.save(any(SupplierQuote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(quoteMapper.toResponse(any(SupplierQuote.class))).thenReturn(mockResponse);

    SupplierQuoteResponse updated = quoteService.markAsAccepted(quoteId);

    assertNotNull(updated);
    assertEquals(SupplierQuoteStatus.ACCEPTED, mockQuote.getStatus());
    assertEquals(SupplierQuoteStatus.REJECTED, sibling.getStatus());
  }

  @Test
  @DisplayName("Should throw when accepting already REJECTED quote")
  void shouldThrowWhenAcceptingRejectedQuote() {
    mockQuote.setStatus(SupplierQuoteStatus.REJECTED);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(mockQuote));

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> quoteService.markAsAccepted(quoteId));

    assertEquals("Cannot transition quote from REJECTED to ACCEPTED", ex.getMessage());
  }

  @Test
  @DisplayName("Should mark quote as REJECTED")
  void shouldMarkQuoteAsRejected() {
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(mockQuote));
    when(quoteRepository.save(any(SupplierQuote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(quoteMapper.toResponse(any(SupplierQuote.class))).thenReturn(mockResponse);

    SupplierQuoteResponse updated = quoteService.markAsRejected(quoteId);

    assertNotNull(updated);
    assertEquals(SupplierQuoteStatus.REJECTED, mockQuote.getStatus());
  }

  @Test
  @DisplayName("Should throw when rejecting already ACCEPTED quote")
  void shouldThrowWhenRejectingAcceptedQuote() {
    mockQuote.setStatus(SupplierQuoteStatus.ACCEPTED);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(mockQuote));

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> quoteService.markAsRejected(quoteId));

    assertEquals("Cannot transition quote from ACCEPTED to REJECTED", ex.getMessage());
  }
}
