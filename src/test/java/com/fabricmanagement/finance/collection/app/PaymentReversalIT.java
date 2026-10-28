package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.collection.app.port.CollectionSourcePort;
import com.fabricmanagement.finance.collection.app.port.CollectionSourcePort.*;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.*;
import com.fabricmanagement.finance.collection.dto.CollectionRequests;
import com.fabricmanagement.finance.collection.infra.repository.CollectionObligationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import com.fabricmanagement.finance.common.exception.FuturePostingDateException;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import com.fabricmanagement.finance.payment.app.PaymentService;
import com.fabricmanagement.finance.payment.domain.event.PaymentReversedEvent;
import com.fabricmanagement.finance.payment.domain.exception.PaymentReversalException;
import com.fabricmanagement.finance.payment.dto.CreateAllocationRequest;
import com.fabricmanagement.finance.payment.dto.CreatePaymentRequest;
import com.fabricmanagement.finance.payment.dto.ReversePaymentRequest;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import com.fabricmanagement.finance.period.app.port.FinancialPeriodGuard;
import com.fabricmanagement.finance.period.domain.exception.ClosedFinancialPeriodException;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real PostgreSQL: reversing a payment of any method posts an opposite, dated payment, releases
 * what the original had claimed (invoice allocations, collection matches) and happens only once.
 */
@Import(PaymentReversalIT.FixedTime.class)
class PaymentReversalIT extends AbstractIntegrationTest {
  @TestConfiguration
  static class FixedTime {
    @Bean
    @Primary
    Clock paymentReversalClock() {
      return Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired CollectionProjectionService projection;
  @Autowired CollectionPostingService posting;
  @Autowired CollectionChequeService cheques;
  @Autowired CollectionObligationRepository obligations;
  @Autowired CollectionPaymentMatchRepository matches;
  @Autowired PaymentService payments;
  @Autowired PaymentRepository paymentRepository;
  @Autowired InvoiceRepository invoices;
  @Autowired SystemTransactionExecutor system;
  @Autowired TransactionTemplate transactions;
  @MockitoBean CollectionSourcePort source;
  @MockitoBean CollectionAccessService access;
  @MockitoBean DomainEventPublisher events;
  @MockitoBean FinancialPeriodGuard periods;
  UUID tenant, otherTenant, actor, order, partner, plan, revision, logical, obligation, approval;
  final LocalDate date = LocalDate.of(2026, 10, 20);
  final LocalDate nextDay = date.plusDays(1);
  final Instant now = Instant.parse("2026-10-20T12:00:00Z");

  @BeforeEach
  void seed() {
    tenant = UUID.randomUUID();
    otherTenant = UUID.randomUUID();
    actor = UUID.randomUUID();
    order = UUID.randomUUID();
    partner = UUID.randomUUID();
    plan = UUID.randomUUID();
    revision = UUID.randomUUID();
    logical = UUID.randomUUID();
    approval = UUID.randomUUID();
    system.executeInTransaction(
        sql -> {
          for (UUID id : List.of(tenant, otherTenant))
            sql.update(
                "INSERT INTO common_tenant.common_tenant(id,uid,slug,name,status)"
                    + " VALUES(?,?,?,'Northbridge Cloth','ACTIVE')",
                id,
                id.toString(),
                id.toString());
          UUID registry = UUID.randomUUID();
          sql.update(
              "INSERT INTO common_company.trading_partner_registry(id,uid,official_name,country)"
                  + " VALUES(?,?,'Pennine Textiles','GBR')",
              registry,
              registry.toString());
          sql.update(
              "INSERT INTO"
                  + " common_company.common_trading_partner(id,tenant_id,uid,registry_id,partner_type)"
                  + " VALUES(?,?,?,?,'CUSTOMER')",
              partner,
              tenant,
              partner.toString(),
              registry);
          return null;
        });
    when(source.find(any(), eq(order)))
        .thenAnswer(
            call ->
                tenant.equals(call.getArgument(0))
                    ? Optional.of(agreement(Method.BANK_TRANSFER))
                    : Optional.empty());
    when(access.reach(eq(actor), anyString()))
        .thenAnswer(
            call ->
                new CollectionAccessService.Reach(
                    TenantContext.requireTenantId(), DataScope.ORGANIZATION, Set.of(actor)));
    in(
        tenant,
        () -> {
          projection.synchronize(order);
          return null;
        });
    obligation =
        in(tenant, () -> obligations.findByTenantIdAndOrderId(tenant, order).getFirst().getId());
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
    system.executeInTransaction(
        sql -> {
          for (UUID id : List.of(tenant, otherTenant)) {
            sql.queryForObject(
                "SELECT set_config('app.collection_purge_tenant',?,true)",
                String.class,
                id.toString());
            for (String table :
                List.of(
                    "collection_source_observation",
                    "collection_command_receipt",
                    "collection_cheque_event",
                    "collection_refund_event",
                    "collection_refund",
                    "collection_payment_match",
                    "collection_cheque_application",
                    "collection_cheque",
                    "collection_expectation",
                    "collection_obligation",
                    "collection_projection",
                    "fx_realization",
                    "finance_payment_allocation",
                    "finance_payment",
                    "finance_invoice_tax_line",
                    "finance_invoice_line",
                    "finance_invoice",
                    "document_number_counter"))
              sql.update("DELETE FROM finance." + table + " WHERE tenant_id=?", id);
          }
          return null;
        });
  }

  @Test
  void aReversalIsAnOppositeDatedPaymentAndTheOriginalStaysAsRecorded() {
    UUID original = in(tenant, () -> createPayment("1000.00", date));

    var reversal =
        in(
            tenant,
            () ->
                payments.reverse(
                    original, new ReversePaymentRequest(nextDay, "Booked twice", null), actor));

    assertThat(reversal.direction()).isEqualTo("OUTBOUND");
    assertThat(reversal.method()).isEqualTo("BANK_TRANSFER");
    assertThat(reversal.amount()).isEqualByComparingTo("1000.00");
    assertThat(reversal.paymentDate()).isEqualTo(nextDay);
    assertThat(reversal.reversesPaymentId()).isEqualTo(original);
    assertThat(reversal.reversalReason()).isEqualTo("Booked twice");
    var kept = in(tenant, () -> payments.getPayment(original));
    assertThat(kept.status()).isEqualTo("RECEIVED");
    assertThat(kept.paymentDate()).isEqualTo(date);
    assertThat(kept.reversedByPaymentId()).isEqualTo(reversal.id());
    assertThat(totalPayments()).isEqualTo(2);
    verify(events, atLeastOnce()).publish(any(PaymentReversedEvent.class));
  }

  @Test
  void aReversalReleasesTheInvoiceAllocationAndRestoresTheInvoiceBalance() {
    UUID invoice = in(tenant, () -> invoice("1000.00"));
    UUID original = in(tenant, () -> createPayment("1000.00", date));
    in(
        tenant,
        () ->
            payments.allocatePayment(
                original, new CreateAllocationRequest(invoice, new BigDecimal("1000.00"))));
    assertThat(amountPaid(invoice)).isEqualByComparingTo("1000.00");

    in(
        tenant,
        () ->
            payments.reverse(
                original,
                new ReversePaymentRequest(nextDay, "Paid the wrong invoice", null),
                actor));

    assertThat(amountPaid(invoice)).isEqualByComparingTo("0");
  }

  @Test
  void aPaymentWithAllocationsCannotBeVoidedOnlyReversed() {
    UUID invoice = in(tenant, () -> invoice("1000.00"));
    UUID original = in(tenant, () -> createPayment("1000.00", date));
    in(
        tenant,
        () ->
            payments.allocatePayment(
                original, new CreateAllocationRequest(invoice, new BigDecimal("1000.00"))));

    assertThatThrownBy(() -> in(tenant, () -> payments.voidPayment(original)))
        .isInstanceOfSatisfying(
            PaymentReversalException.class,
            refusal ->
                assertThat(refusal.getErrorCode()).isEqualTo("PAYMENT_VOID_HAS_ALLOCATIONS"));
    assertThat(amountPaid(invoice)).isEqualByComparingTo("1000.00");
  }

  @Test
  void aReversalReleasesTheCollectionMatchesSoTheExpectationIsOutstandingAgain() {
    var received =
        in(
            tenant,
            () -> posting.receive(obligation, receipt(UUID.randomUUID(), "1000.00", 0L), actor));
    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("1000.00");

    in(
        tenant,
        () ->
            payments.reverse(
                received.getPaymentId(),
                new ReversePaymentRequest(date, "Keyed against the wrong buyer", null),
                actor));

    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("0");
    var released =
        in(
            tenant,
            () ->
                matches
                    .findByTenantIdAndObligationId(tenant, obligation, PageRequest.of(0, 20))
                    .getContent());
    assertThat(released)
        .isNotEmpty()
        .allSatisfy(match -> assertThat(match.getReversedOn()).isNotNull());
  }

  @Test
  void aPaymentCanBeReversedOnlyOnce() {
    UUID original = in(tenant, () -> createPayment("1000.00", date));
    var request = new ReversePaymentRequest(nextDay, "Booked twice", null);
    in(tenant, () -> payments.reverse(original, request, actor));

    assertThatThrownBy(() -> in(tenant, () -> payments.reverse(original, request, actor)))
        .isInstanceOfSatisfying(
            PaymentReversalException.class,
            refusal -> assertThat(refusal.getErrorCode()).isEqualTo("PAYMENT_ALREADY_REVERSED"));
    assertThat(totalPayments()).isEqualTo(2);
  }

  @Test
  void aReversalCanNeitherBeReversedNorVoided() {
    UUID original = in(tenant, () -> createPayment("1000.00", date));
    UUID reversal =
        in(
            tenant,
            () ->
                payments
                    .reverse(
                        original, new ReversePaymentRequest(nextDay, "Booked twice", null), actor)
                    .id());

    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        payments.reverse(
                            reversal, new ReversePaymentRequest(nextDay, "Undo", null), actor)))
        .isInstanceOfSatisfying(
            PaymentReversalException.class,
            refusal ->
                assertThat(refusal.getErrorCode()).isEqualTo("PAYMENT_REVERSAL_NOT_REVERSIBLE"));
    assertThatThrownBy(() -> in(tenant, () -> payments.voidPayment(reversal)))
        .isInstanceOfSatisfying(
            PaymentReversalException.class,
            refusal -> assertThat(refusal.getErrorCode()).isEqualTo("PAYMENT_REVERSAL_IMMUTABLE"));
  }

  @Test
  void aFutureDatedReversalIsRefusedAndChangesNothing() {
    UUID original = in(tenant, () -> createPayment("1000.00", date));

    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        payments.reverse(
                            original,
                            new ReversePaymentRequest(
                                LocalDate.of(2026, 10, 23), "Booked ahead", null),
                            actor)))
        .isInstanceOfSatisfying(
            FuturePostingDateException.class,
            refusal ->
                assertThat(refusal.getErrorCode()).isEqualTo("FINANCE_POSTING_DATE_IN_FUTURE"));

    assertThat(in(tenant, () -> payments.getPayment(original).reversedByPaymentId())).isNull();
    assertThat(totalPayments()).isEqualTo(1);
  }

  @Test
  void aFutureDatedPaymentIsRefusedAndNothingIsPosted() {
    assertThatThrownBy(() -> in(tenant, () -> createPayment("1000.00", LocalDate.of(2026, 10, 23))))
        .isInstanceOfSatisfying(
            FuturePostingDateException.class,
            refusal ->
                assertThat(refusal.getErrorCode()).isEqualTo("FINANCE_POSTING_DATE_IN_FUTURE"));
    assertThat(totalPayments()).isZero();
  }

  @Test
  void aClosedPeriodRefusesTheReversalDateAndLeavesThePaymentUntouched() {
    UUID original = in(tenant, () -> createPayment("1000.00", date));
    doThrow(new ClosedFinancialPeriodException(nextDay, YearMonth.from(nextDay)))
        .when(periods)
        .assertPostingAllowed(tenant, nextDay);

    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        payments.reverse(
                            original,
                            new ReversePaymentRequest(nextDay, "Booked twice", null),
                            actor)))
        .isInstanceOf(ClosedFinancialPeriodException.class);

    assertThat(in(tenant, () -> payments.getPayment(original).reversedByPaymentId())).isNull();
    assertThat(totalPayments()).isEqualTo(1);
  }

  @Test
  void twoConcurrentReversalsOfOnePaymentPostExactlyOneReversal() throws Exception {
    UUID original = in(tenant, () -> createPayment("1000.00", date));
    var request = new ReversePaymentRequest(nextDay, "Booked twice", null);

    var results = race(() -> payments.reverse(original, request, actor));

    assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
    assertThat(totalPayments()).isEqualTo(2);
  }

  @Test
  void anotherTenantCannotReverseThePayment() {
    UUID original = in(tenant, () -> createPayment("1000.00", date));

    assertThatThrownBy(
            () ->
                in(
                    otherTenant,
                    () ->
                        payments.reverse(
                            original, new ReversePaymentRequest(nextDay, "Not mine", null), actor)))
        .isInstanceOf(FinanceDomainException.class)
        .hasMessageContaining("Payment not found");
    assertThat(in(tenant, () -> payments.getPayment(original).reversedByPaymentId())).isNull();
  }

  @Test
  void aChequeClearingPaymentIsReversedFromTheChequeAndNeverDirectly() {
    chequeSource();
    UUID cheque = receiveCheque();
    long version = version();
    var clear =
        in(
            tenant,
            () ->
                cheques.transition(
                    cheque, transition(UUID.randomUUID(), ChequeAction.CLEAR, 0L, version), actor));

    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        payments.reverse(
                            clear.getPaymentId(),
                            new ReversePaymentRequest(nextDay, "Directly", null),
                            actor)))
        .isInstanceOfSatisfying(
            PaymentReversalException.class,
            refusal -> assertThat(refusal.getErrorCode()).isEqualTo("PAYMENT_BOUND_TO_INSTRUMENT"));
    assertThat(in(tenant, () -> payments.getPayment(clear.getPaymentId()).reversedByPaymentId()))
        .isNull();
  }

  private void chequeSource() {
    revision = UUID.randomUUID();
    when(source.find(any(), eq(order))).thenAnswer(call -> Optional.of(agreement(Method.CHEQUE)));
    in(
        tenant,
        () -> {
          projection.synchronize(order);
          return null;
        });
  }

  private UUID receiveCheque() {
    long version = version();
    return in(
        tenant,
        () ->
            cheques
                .receive(
                    new CollectionRequests.ReceiveCheque(
                        UUID.randomUUID(),
                        partner,
                        "GBP",
                        new BigDecimal("4284.00"),
                        date,
                        date.plusMonths(3),
                        "NORTHBANK-" + order,
                        "Signed instrument scan",
                        now,
                        "Buyer handed cheque to finance",
                        List.of(
                            new CollectionRequests.Application(
                                obligation, version, new BigDecimal("4284.00")))),
                    actor)
                .getResourceId());
  }

  private CollectionRequests.Transition transition(
      UUID operation, ChequeAction action, Long chequeVersion, Long balanceVersion) {
    return new CollectionRequests.Transition(
        operation,
        chequeVersion,
        action,
        date,
        now,
        "Bank evidence reviewed",
        "Instrument and bank statement verified",
        null,
        null,
        List.of(new CollectionRequests.Version(obligation, balanceVersion)));
  }

  private Agreement agreement(Method method) {
    return new Agreement(
        order,
        partner,
        plan,
        revision,
        1,
        approval,
        now,
        now,
        false,
        false,
        List.of(
            new Expectation(
                logical,
                "GBP",
                new BigDecimal("4284.00"),
                Kind.INSTALMENT,
                method,
                date,
                false,
                method == Method.CHEQUE ? date.plusMonths(3) : null)),
        null,
        1L);
  }

  private CollectionRequests.Receipt receipt(UUID operation, String amount, Long version) {
    return new CollectionRequests.Receipt(
        operation,
        version,
        Method.BANK_TRANSFER,
        new BigDecimal(amount),
        date,
        now,
        "NORTHBANK-TRANSFER-42",
        "Bank credit verified");
  }

  private long version() {
    return in(
        tenant,
        () -> obligations.findByTenantIdAndId(tenant, obligation).orElseThrow().getVersion());
  }

  private UUID createPayment(String amount, LocalDate paidOn) {
    return payments
        .createPayment(
            new CreatePaymentRequest(
                partner,
                "INBOUND",
                "BANK_TRANSFER",
                new BigDecimal(amount),
                "GBP",
                paidOn,
                "NORTHBANK-" + UUID.randomUUID(),
                "Bank credit",
                null))
        .id();
  }

  private UUID invoice(String amount) {
    return invoices
        .saveAndFlush(
            Invoice.builder()
                .tradingPartnerId(partner)
                .invoiceNumber("INV-GB-" + UUID.randomUUID())
                .issueDate(date)
                .dueDate(date.plusMonths(1))
                .subtotal(Money.of(new BigDecimal(amount), "GBP"))
                .totalAmount(Money.of(new BigDecimal(amount), "GBP"))
                .amountDue(Money.of(new BigDecimal(amount), "GBP"))
                .amountPaid(Money.zero("GBP"))
                .amountCredited(Money.zero("GBP"))
                .status(InvoiceStatus.SENT)
                .build())
        .getId();
  }

  private BigDecimal amountPaid(UUID invoice) {
    return in(
        tenant,
        () ->
            invoices
                .findByTenantIdAndId(tenant, invoice)
                .orElseThrow()
                .getAmountPaid()
                .getAmount());
  }

  private long totalPayments() {
    return in(
        tenant,
        () -> paymentRepository.findByTenantId(tenant, PageRequest.of(0, 50)).getTotalElements());
  }

  private <T> T in(UUID asTenant, Supplier<T> work) {
    return TenantContext.executeInTenantContext(
        asTenant,
        () -> {
          TenantContext.setCurrentUserId(actor);
          return transactions.execute(status -> work.get());
        });
  }

  /** Runs the same command twice at once; true when it succeeded, false when the books refused. */
  private List<Boolean> race(Supplier<?> command) throws Exception {
    var start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var futures =
          List.of(
              executor.submit(() -> attempt(start, command)),
              executor.submit(() -> attempt(start, command)));
      start.countDown();
      return List.of(
          futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS));
    }
  }

  private boolean attempt(CountDownLatch start, Supplier<?> command) throws InterruptedException {
    start.await();
    try {
      in(tenant, command::get);
      return true;
    } catch (DomainException refused) {
      return false;
    }
  }
}
