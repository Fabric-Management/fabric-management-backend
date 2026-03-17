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
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteLine;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteStatus;
import com.fabricmanagement.procurement.quote.dto.CreateSupplierQuoteRequest;
import com.fabricmanagement.procurement.quote.infra.repository.SupplierQuoteRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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

  @InjectMocks private SupplierQuoteService quoteService;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID quoteId = UUID.randomUUID();
  private SupplierQuote mockQuote;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);

    mockQuote = new SupplierQuote();
    mockQuote.setId(quoteId);
    mockQuote.setTenantId(tenantId);
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
    req.setRfqId(UUID.randomUUID());
    req.setTradingPartnerId(UUID.randomUUID());
    req.setValidUntil(LocalDate.now().plusDays(10));
    req.setCurrency("USD");
    req.setEntryMethod(QuoteEntryMethod.MANUAL_ENTRY);

    when(quoteRepository.save(any(SupplierQuote.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

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
    when(quoteRepository.save(any(SupplierQuote.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SupplierQuoteLine line = new SupplierQuoteLine();
    line.setUnitPrice(new BigDecimal("12.50"));
    line.setQty(new BigDecimal("500"));

    SupplierQuote updated = quoteService.addLine(quoteId, line);

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

    SupplierQuoteLine line = new SupplierQuoteLine();

    ProcurementDomainException ex =
        assertThrows(ProcurementDomainException.class, () -> quoteService.addLine(quoteId, line));

    assertEquals("Cannot add line to quote in status: ACCEPTED", ex.getMessage());
  }

  @Test
  @DisplayName("Should mark quote as ACCEPTED")
  void shouldMarkQuoteAsAccepted() {
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(mockQuote));
    when(quoteRepository.save(any(SupplierQuote.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SupplierQuote updated = quoteService.markAsAccepted(quoteId);

    assertEquals(SupplierQuoteStatus.ACCEPTED, updated.getStatus());
  }

  @Test
  @DisplayName("Should mark quote as REJECTED")
  void shouldMarkQuoteAsRejected() {
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(mockQuote));
    when(quoteRepository.save(any(SupplierQuote.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SupplierQuote updated = quoteService.markAsRejected(quoteId);

    assertEquals(SupplierQuoteStatus.REJECTED, updated.getStatus());
  }
}
