package com.fabricmanagement.finance.payment.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.payment.app.port.InvoiceAllocationView;
import com.fabricmanagement.finance.payment.app.port.InvoicePaymentPort;
import com.fabricmanagement.finance.payment.domain.Payment;
import com.fabricmanagement.finance.payment.domain.PaymentAllocation;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import com.fabricmanagement.finance.payment.domain.event.PaymentAllocatedEvent;
import com.fabricmanagement.finance.payment.dto.CreateAllocationRequest;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import com.fabricmanagement.finance.payment.mapper.PaymentMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private InvoicePaymentPort invoicePaymentPort;
  @Mock private ApplicationEventPublisher eventPublisher;
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
    when(invoicePaymentPort.getInvoiceForAllocation(tenantId, invoiceId))
        .thenReturn(
            new InvoiceAllocationView(
                invoiceId, Money.of(new java.math.BigDecimal("50.00"), "USD"), "USD", true));

    CreateAllocationRequest req = new CreateAllocationRequest(invoiceId, new BigDecimal("40.00"));

    paymentService.allocatePayment(paymentId, req);

    verify(invoicePaymentPort)
        .applyAllocation(
            eq(tenantId),
            eq(invoiceId),
            eq(Money.of(new java.math.BigDecimal("40.00"), "USD")),
            any());
    verify(paymentRepository).save(payment);
    verify(eventPublisher).publishEvent(any(PaymentAllocatedEvent.class));

    assertThat(payment.getAllocatedAmount())
        .isEqualTo(Money.of(new java.math.BigDecimal("40.00"), "USD"));
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

    verify(invoicePaymentPort)
        .reverseAllocation(
            eq(tenantId), eq(invoiceId), eq(Money.of(new java.math.BigDecimal("40.00"), "USD")));
    verify(paymentRepository).save(payment);

    assertThat(allocation.getIsActive()).isFalse();
    assertThat(payment.getAllocatedAmount()).isEqualTo(Money.zero("USD"));
  }
}
