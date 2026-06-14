package com.fabricmanagement.finance.invoice.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceLine;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import com.fabricmanagement.finance.invoice.dto.CreateInvoiceRequest;
import com.fabricmanagement.finance.invoice.dto.UpdateInvoiceRequest;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import com.fabricmanagement.finance.invoice.mapper.InvoiceMapper;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceAmountGuardTest {

  @Mock private InvoiceRepository invoiceRepository;
  @Mock private InvoiceMapper invoiceMapper;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private TenantReportingCurrencyPort reportingCurrencyPort;

  @Mock
  private com.fabricmanagement.finance.common.app.FinanceDocumentNumberGenerator
      documentNumberGenerator;

  @InjectMocks private InvoiceService invoiceService;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID invoiceId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(userId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @org.junit.jupiter.api.Test
  void createInvoice_withNullCurrency_resolvesFromPort() {
    // Arrange
    CreateInvoiceRequest request =
        new CreateInvoiceRequest(
            UUID.randomUUID(),
            null,
            null,
            InvoiceType.SALES.name(),
            java.time.LocalDate.now(),
            java.time.LocalDate.now().plusDays(30),
            BigDecimal.valueOf(1000), // subtotal
            BigDecimal.valueOf(180), // tax
            BigDecimal.ZERO, // discount
            BigDecimal.valueOf(1180), // total
            null, // currency
            BigDecimal.valueOf(18), // taxRate
            "Address", // billingAddress
            "Notes", // notes
            null, // originalInvoiceId
            java.util.Collections.emptyList()); // lines

    when(reportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");

    // We only want to intercept the invoice entity passed to the mapper
    when(invoiceMapper.toDto(any(Invoice.class)))
        .thenAnswer(
            inv -> {
              Invoice saved = inv.getArgument(0);
              // the test focuses on the entity state before save/map
              assertThat(saved.getCurrency()).isEqualTo("GBP");
              assertThat(saved.getSubtotal().getCurrency().getCurrencyCode()).isEqualTo("GBP");
              return null;
            });

    when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

    // Act
    invoiceService.createInvoice(request);

    // Assert
    verify(reportingCurrencyPort).getReportingCurrency(tenantId);
  }

  @ParameterizedTest(name = "reject monetary field update: {0}")
  @MethodSource("monetaryFieldRequests")
  void updateInvoice_withLines_rejectMonetaryFieldUpdate(
      String fieldName, UpdateInvoiceRequest request) {
    // Arrange
    Invoice invoice =
        Invoice.builder().subtotal(Money.zero("GBP")).status(InvoiceStatus.DRAFT).build();
    ReflectionTestUtils.setField(invoice, "id", invoiceId);

    InvoiceLine line =
        InvoiceLine.builder().quantity(BigDecimal.TEN).unitPrice(BigDecimal.TEN).build();
    invoice.addLine(line);

    when(invoiceRepository.findByTenantIdAndId(tenantId, invoiceId))
        .thenReturn(Optional.of(invoice));

    // Act & Assert
    assertThatThrownBy(() -> invoiceService.updateInvoice(invoiceId, request))
        .isInstanceOf(FinanceDomainException.class)
        .hasMessageContaining("Cannot directly update monetary amounts on an invoice with lines");
  }

  /**
   * Provides an UpdateInvoiceRequest for each of the four guarded monetary fields. Each request
   * sets exactly one monetary field, leaving the rest null.
   */
  static Stream<Arguments> monetaryFieldRequests() {
    BigDecimal val = new BigDecimal("100.00");
    return Stream.of(
        Arguments.of("subtotal", monetaryRequest(val, null, null, null)),
        Arguments.of("taxAmount", monetaryRequest(null, val, null, null)),
        Arguments.of("discountAmount", monetaryRequest(null, null, val, null)),
        Arguments.of("totalAmount", monetaryRequest(null, null, null, val)));
  }

  /**
   * Creates an UpdateInvoiceRequest with only monetary fields populated. Isolates the positional
   * record constructor dependency to one place.
   */
  private static UpdateInvoiceRequest monetaryRequest(
      BigDecimal subtotal,
      BigDecimal taxAmount,
      BigDecimal discountAmount,
      BigDecimal totalAmount) {
    return new UpdateInvoiceRequest(
        null, null, null, null, subtotal, taxAmount, discountAmount, totalAmount, null, null, null);
  }
}
