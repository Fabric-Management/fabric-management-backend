package com.fabricmanagement.finance.payment.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.common.app.FinanceDocumentNumberGenerator;
import com.fabricmanagement.finance.common.app.SettlementFxResult;
import com.fabricmanagement.finance.payment.app.port.InvoiceAllocationView;
import com.fabricmanagement.finance.payment.app.port.InvoicePaymentPort;
import com.fabricmanagement.finance.payment.domain.Payment;
import com.fabricmanagement.finance.payment.domain.PaymentAllocation;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import com.fabricmanagement.finance.payment.domain.event.PaymentAllocatedEvent;
import com.fabricmanagement.finance.payment.dto.CreateAllocationRequest;
import com.fabricmanagement.finance.payment.dto.CreatePaymentRequest;
import com.fabricmanagement.finance.payment.infra.repository.PaymentAllocationRepository;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import com.fabricmanagement.finance.payment.mapper.PaymentMapper;
import com.fabricmanagement.finance.period.app.port.FinancialPeriodGuard;
import com.fabricmanagement.finance.period.domain.exception.ClosedFinancialPeriodException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private PaymentAllocationRepository paymentAllocationRepository;
  @Mock private InvoicePaymentPort invoicePaymentPort;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private FinanceDocumentNumberGenerator documentNumberGenerator;
  @Mock private FinancialPeriodGuard financialPeriodGuard;
  @Spy private PaymentMapper paymentMapper = Mappers.getMapper(PaymentMapper.class);

  @InjectMocks private PaymentService paymentService;

  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void testAllocatePayment_Success() {
    UUID paymentId = UUID.randomUUID();
    UUID invoiceId = UUID.randomUUID();

    Payment payment =
        Payment.builder()
            .tradingPartnerId(UUID.randomUUID())
            .paymentNumber("PAY-1")
            .direction(PaymentDirection.INBOUND)
            .amount(Money.of(new java.math.BigDecimal("100.00"), "USD"))
            .paymentDate(LocalDate.now())
            .allocations(new ArrayList<>())
            .build();
    payment.setId(paymentId);

    when(paymentRepository.findByTenantIdAndId(tenantId, paymentId))
        .thenReturn(Optional.of(payment));
    when(paymentRepository.saveAndFlush(payment))
        .thenAnswer(
            invocation -> {
              payment
                  .getAllocations()
                  .forEach(
                      allocation -> {
                        if (allocation.getId() == null) {
                          allocation.setId(UUID.randomUUID());
                        }
                      });
              return payment;
            });
    when(paymentAllocationRepository
            .findFirstByTenantIdAndPaymentIdAndInvoiceIdAndIsActiveTrueOrderByCreatedAtDesc(
                eq(tenantId), eq(paymentId), eq(invoiceId)))
        .thenAnswer(
            invocation ->
                payment.getAllocations().stream()
                    .filter(allocation -> allocation.getId() != null)
                    .findFirst());
    when(invoicePaymentPort.getInvoiceForAllocation(tenantId, invoiceId))
        .thenReturn(
            new InvoiceAllocationView(
                invoiceId, Money.of(new java.math.BigDecimal("50.00"), "USD"), "USD", true));
    when(invoicePaymentPort.recordAllocationFx(
            eq(tenantId),
            eq(invoiceId),
            any(UUID.class),
            eq(Money.of(new java.math.BigDecimal("40.00"), "USD")),
            any()))
        .thenReturn(SettlementFxResult.zero("USD", BigDecimal.ONE, LocalDate.now()));

    CreateAllocationRequest req = new CreateAllocationRequest(invoiceId, new BigDecimal("40.00"));

    paymentService.allocatePayment(paymentId, req);

    verify(financialPeriodGuard).assertPostingAllowed(tenantId, payment.getPaymentDate());
    verify(invoicePaymentPort)
        .applyAllocation(
            eq(tenantId),
            eq(invoiceId),
            eq(Money.of(new java.math.BigDecimal("40.00"), "USD")),
            any());
    verify(paymentRepository).saveAndFlush(payment);
    verify(eventPublisher).publish(any(PaymentAllocatedEvent.class));

    assertThat(payment.getAllocatedAmount())
        .isEqualTo(Money.of(new java.math.BigDecimal("40.00"), "USD"));
  }

  @Test
  void allocatePayment_rejectsClosedPaymentDateBeforeBusinessMutation() {
    UUID paymentId = UUID.randomUUID();
    UUID invoiceId = UUID.randomUUID();
    LocalDate paymentDate = LocalDate.of(2026, 5, 20);

    Payment payment =
        Payment.builder()
            .tradingPartnerId(UUID.randomUUID())
            .paymentNumber("PAY-1")
            .direction(PaymentDirection.INBOUND)
            .amount(Money.of(new BigDecimal("100.00"), "USD"))
            .paymentDate(paymentDate)
            .allocations(new ArrayList<>())
            .build();
    payment.setId(paymentId);

    when(paymentRepository.findByTenantIdAndId(tenantId, paymentId))
        .thenReturn(Optional.of(payment));
    doThrow(new ClosedFinancialPeriodException(paymentDate, YearMonth.of(2026, 5)))
        .when(financialPeriodGuard)
        .assertPostingAllowed(tenantId, paymentDate);

    CreateAllocationRequest req = new CreateAllocationRequest(invoiceId, new BigDecimal("40.00"));

    assertThatThrownBy(() -> paymentService.allocatePayment(paymentId, req))
        .isInstanceOf(ClosedFinancialPeriodException.class);

    assertThat(payment.getAllocations()).isEmpty();
    verify(invoicePaymentPort, never()).getInvoiceForAllocation(any(), any());
    verify(paymentRepository, never()).saveAndFlush(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void createPaymentWithAllocationsRejectsClosedPaymentDateBeforeNumberOrSave() {
    UUID invoiceId = UUID.randomUUID();
    LocalDate paymentDate = LocalDate.of(2026, 5, 20);
    CreatePaymentRequest request =
        new CreatePaymentRequest(
            UUID.randomUUID(),
            PaymentDirection.INBOUND.name(),
            "BANK_TRANSFER",
            new BigDecimal("100.00"),
            "USD",
            paymentDate,
            "BANK-1",
            null,
            List.of(new CreateAllocationRequest(invoiceId, new BigDecimal("40.00"))));

    doThrow(new ClosedFinancialPeriodException(paymentDate, YearMonth.of(2026, 5)))
        .when(financialPeriodGuard)
        .assertPostingAllowed(tenantId, paymentDate);

    assertThatThrownBy(() -> paymentService.createPayment(request))
        .isInstanceOf(ClosedFinancialPeriodException.class);

    verify(documentNumberGenerator, never())
        .nextNumber(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    verify(paymentRepository, never()).save(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void testDeallocatePayment_Success() {
    UUID paymentId = UUID.randomUUID();
    UUID invoiceId = UUID.randomUUID();

    Payment payment =
        Payment.builder()
            .tradingPartnerId(UUID.randomUUID())
            .paymentNumber("PAY-1")
            .direction(PaymentDirection.INBOUND)
            .amount(Money.of(new java.math.BigDecimal("100.00"), "USD"))
            .paymentDate(LocalDate.now())
            .allocations(new ArrayList<>())
            .build();
    payment.setId(paymentId);

    PaymentAllocation allocation =
        payment.allocate(
            invoiceId,
            Money.of(new java.math.BigDecimal("40.00"), "USD"),
            Money.of(new java.math.BigDecimal("50.00"), "USD"),
            "USD");
    UUID allocationId = UUID.randomUUID();
    allocation.setId(allocationId);

    when(paymentRepository.findByTenantIdAndId(tenantId, paymentId))
        .thenReturn(Optional.of(payment));

    paymentService.deallocatePayment(paymentId, allocationId);

    verifyNoInteractions(financialPeriodGuard);
    verify(invoicePaymentPort)
        .reverseAllocation(
            eq(tenantId), eq(invoiceId), eq(Money.of(new java.math.BigDecimal("40.00"), "USD")));
    verify(invoicePaymentPort).reverseAllocationFx(tenantId, allocationId);
    verify(paymentRepository).save(payment);

    assertThat(allocation.getIsActive()).isFalse();
    assertThat(payment.getAllocatedAmount()).isEqualTo(Money.zero("USD"));
  }
}
