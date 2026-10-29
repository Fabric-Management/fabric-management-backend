package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.exception.GlobalExceptionHandler;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.collection.api.controller.CollectionController;
import com.fabricmanagement.finance.collection.app.port.CollectionSourcePort;
import com.fabricmanagement.finance.collection.app.port.CollectionSourcePort.*;
import com.fabricmanagement.finance.collection.domain.*;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.*;
import com.fabricmanagement.finance.collection.dto.CollectionRequests;
import com.fabricmanagement.finance.collection.infra.repository.*;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import com.fabricmanagement.finance.payment.app.PaymentService;
import com.fabricmanagement.finance.payment.domain.exception.PaymentHeldForRefundException;
import com.fabricmanagement.finance.payment.domain.exception.PaymentReversalException;
import com.fabricmanagement.finance.payment.dto.CreateAllocationRequest;
import com.fabricmanagement.finance.payment.dto.CreatePaymentRequest;
import com.fabricmanagement.finance.payment.dto.ReversePaymentRequest;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import com.fabricmanagement.finance.period.app.port.FinancialPeriodGuard;
import com.fabricmanagement.finance.period.domain.exception.ClosedFinancialPeriodException;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;

/** Real PostgreSQL: locking, receipt replay, rollback, RLS and payment/invoice coexistence. */
@Import(CollectionLedgerIT.FixedTime.class)
class CollectionLedgerIT extends AbstractIntegrationTest {
  @TestConfiguration
  static class FixedTime {
    @Bean
    @Primary
    Clock collectionClock() {
      return Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired ObjectMapper objectMapper;
  @Autowired CollectionCommandService collectionCommands;
  @Autowired CollectionProjectionService projection;
  @Autowired CollectionPostingService posting;
  @Autowired CollectionChequeService cheques;
  @Autowired CollectionOverSettlementService overSettlements;
  @Autowired CollectionRefundService refundService;
  @Autowired CollectionRefundLedger refundLedger;
  @Autowired CollectionQueryService queries;
  @Autowired CollectionObligationRepository obligations;
  @Autowired CollectionChequeRepository instruments;
  @Autowired CollectionPaymentMatchRepository matches;
  @Autowired PaymentService payments;
  @Autowired PaymentRepository paymentRepository;
  @Autowired InvoiceRepository invoices;
  @Autowired SystemTransactionExecutor system;
  @Autowired JdbcTemplate jdbc;
  JdbcTemplate tenantJdbc;

  /**
   * The only JdbcTemplate bean is the system one, which never joins this test's transactions; the
   * RLS probe needs the primary DataSource so SET LOCAL ROLE applies to the query that follows.
   */
  @Autowired
  void tenantDataSource(@Qualifier("dataSource") DataSource dataSource) {
    tenantJdbc = new JdbcTemplate(dataSource);
  }

  @Autowired TransactionTemplate transactions;
  @MockitoSpyBean CollectionCommandStore commands;
  @MockitoBean CollectionSourcePort source;
  @MockitoBean CollectionAccessService access;
  @MockitoBean DomainEventPublisher events;
  @MockitoBean FinancialPeriodGuard periods;
  UUID tenant,
      otherTenant,
      actor,
      order,
      partner,
      plan,
      revision,
      logical,
      obligation,
      approval,
      otherPartner;
  final LocalDate date = LocalDate.of(2026, 10, 20);
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
    otherPartner = UUID.randomUUID();
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
          sql.update(
              "INSERT INTO"
                  + " common_company.common_trading_partner(id,tenant_id,uid,registry_id,partner_type)"
                  + " VALUES(?,?,?,?,'CUSTOMER')",
              otherPartner,
              otherTenant,
              otherPartner.toString(),
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
  void receiptReplayCreatesExactlyOnePaymentAndReopensTheSameLedgerWithoutBrowserState() {
    var body = receipt(UUID.randomUUID(), "1000.00", 0L);
    var first = in(tenant, () -> posting.receive(obligation, body, actor));
    var replay = in(tenant, () -> posting.receive(obligation, body, actor));
    assertThat(replay.getId()).isEqualTo(first.getId());
    assertThat(replay.getPaymentId()).isEqualTo(first.getPaymentId());
    assertThat(
            in(
                tenant,
                () ->
                    paymentRepository
                        .findByTenantId(tenant, PageRequest.of(0, 20))
                        .getTotalElements()))
        .isEqualTo(1);
    var detail = in(tenant, () -> queries.detail(obligation, actor, PageRequest.of(0, 20)));
    assertThat(new BigDecimal(detail.obligation().recordedOutstandingAmount()))
        .isEqualByComparingTo("3284.00");
    assertThat(detail.matches().getContent().getFirst().paymentId())
        .isEqualTo(first.getPaymentId());
  }

  @Test
  void concurrentNewReceiptsCannotBothSpendTheSameObligationBalance() throws Exception {
    var results =
        race(
            () -> posting.receive(obligation, receipt(UUID.randomUUID(), "3000.00", 0L), actor),
            () -> posting.receive(obligation, receipt(UUID.randomUUID(), "3000.00", 0L), actor));
    assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
    assertThat(
            in(
                tenant,
                () ->
                    paymentRepository
                        .findByTenantId(tenant, PageRequest.of(0, 20))
                        .getTotalElements()))
        .isEqualTo(1);
    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("3000.00");
  }

  @Test
  void twoObligationsCannotOvermatchOneExistingPayment() throws Exception {
    UUID secondOrder = UUID.randomUUID(), secondLogical = UUID.randomUUID();
    var accepted =
        new Agreement(
            secondOrder,
            partner,
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            now,
            now,
            false,
            false,
            List.of(
                new Expectation(
                    secondLogical,
                    "GBP",
                    new BigDecimal("4284.00"),
                    Kind.INSTALMENT,
                    Method.BANK_TRANSFER,
                    date,
                    false,
                    null)),
            null,
            1L);
    when(source.find(any(), eq(secondOrder))).thenReturn(Optional.of(accepted));
    in(
        tenant,
        () -> {
          projection.synchronize(secondOrder);
          return null;
        });
    UUID second =
        in(
            tenant,
            () -> obligations.findByTenantIdAndOrderId(tenant, secondOrder).getFirst().getId());
    UUID payment = in(tenant, () -> createPayment("1000.00"));
    var results =
        race(
            () -> posting.match(obligation, match(payment, "600.00", 0L), actor),
            () -> posting.match(second, match(payment, "600.00", 0L), actor));
    assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
    assertThat(in(tenant, () -> matches.matchedPayment(tenant, payment)))
        .isEqualByComparingTo("600.00");
  }

  @Test
  void failureAfterPostingRollsBackPaymentMatchAndReceiptTogether() {
    UUID operation = UUID.randomUUID();
    doThrow(new IllegalStateException("Injected receipt persistence failure"))
        .when(commands)
        .record(eq(operation), any(), any(), any(), any(), anyLong(), any(), anyList());
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () -> posting.receive(obligation, receipt(operation, "1000.00", 0L), actor)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            in(
                tenant,
                () ->
                    paymentRepository
                        .findByTenantId(tenant, PageRequest.of(0, 20))
                        .getTotalElements()))
        .isZero();
    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("0");
  }

  @Test
  void chequeReceiptIsNotCashAndClearingReplayUsesOnePayment() {
    chequeSource();
    UUID cheque = receiveCheque();
    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("0");
    assertThat(
            in(
                tenant,
                () ->
                    paymentRepository
                        .findByTenantId(tenant, PageRequest.of(0, 20))
                        .getTotalElements()))
        .isZero();
    long version = version();
    var body = transition(UUID.randomUUID(), ChequeAction.CLEAR, 0L, version);
    var cleared = in(tenant, () -> cheques.transition(cheque, body, actor));
    var replay = in(tenant, () -> cheques.transition(cheque, body, actor));
    assertThat(replay.getPaymentId()).isEqualTo(cleared.getPaymentId());
    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("4284.00");
    assertThat(
            in(
                tenant,
                () ->
                    paymentRepository
                        .findByTenantId(tenant, PageRequest.of(0, 20))
                        .getTotalElements()))
        .isEqualTo(1);
  }

  @Test
  void failedClearingReceiptRollsBackInstrumentStateAndItsNewPayment() {
    chequeSource();
    UUID cheque = receiveCheque();
    UUID operation = UUID.randomUUID();
    long version = version();
    doThrow(new IllegalStateException("Injected clearing receipt failure"))
        .when(commands)
        .record(eq(operation), any(), any(), any(), any(), anyLong(), any(), anyList());
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        cheques.transition(
                            cheque, transition(operation, ChequeAction.CLEAR, 0L, version), actor)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            in(
                tenant,
                () -> instruments.findByTenantIdAndId(tenant, cheque).orElseThrow().getState()))
        .isEqualTo(ChequeState.RECEIVED);
    assertThat(
            in(
                tenant,
                () ->
                    paymentRepository
                        .findByTenantId(tenant, PageRequest.of(0, 20))
                        .getTotalElements()))
        .isZero();
  }

  @Test
  void dishonourReversesOnlyItsMatchesAndReopensCashOutstanding() {
    chequeSource();
    UUID cheque = receiveCheque();
    long version = version();
    var clear =
        in(
            tenant,
            () ->
                cheques.transition(
                    cheque, transition(UUID.randomUUID(), ChequeAction.CLEAR, 0L, version), actor));
    long chequeVersion =
        in(
            tenant,
            () -> instruments.findByTenantIdAndId(tenant, cheque).orElseThrow().getVersion());
    long balanceVersion = version();
    in(
        tenant,
        () ->
            cheques.transition(
                cheque,
                transition(
                    UUID.randomUUID(), ChequeAction.DISHONOUR, chequeVersion, balanceVersion),
                actor));
    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("0");
    // The clearing payment is cancelled on a dated reversal and keeps its history: still RECEIVED,
    // linked to an opposite payment that nets it to zero.
    assertThat(in(tenant, () -> payments.getPayment(clear.getPaymentId()).status()))
        .isEqualTo("RECEIVED");
    assertThat(
            in(
                tenant,
                () ->
                    paymentRepository
                        .findByTenantIdAndId(tenant, clear.getPaymentId())
                        .orElseThrow()
                        .isReversed()))
        .isTrue();
    assertThat(
            in(
                tenant,
                () -> instruments.findByTenantIdAndId(tenant, cheque).orElseThrow().getState()))
        .isEqualTo(ChequeState.DISHONOURED);
  }

  @Test
  void voidingAMatchedPaymentIsRefusedAndItsReversalReleasesTheBalanceWithNoEventDelivery() {
    var receipt =
        in(
            tenant,
            () -> posting.receive(obligation, receipt(UUID.randomUUID(), "1000.00", 0L), actor));
    // Review-3 Y3/O1: a matched payment is never voided in place; it is reversed on a dated
    // posting.
    assertThatThrownBy(() -> in(tenant, () -> payments.voidPayment(receipt.getPaymentId())))
        .isInstanceOfSatisfying(
            PaymentReversalException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("PAYMENT_VOID_HAS_MATCHES"));
    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("1000.00");
    in(
        tenant,
        () ->
            payments.reverse(
                receipt.getPaymentId(),
                new ReversePaymentRequest(date, "Receipt entered twice", null),
                actor));
    // DomainEventPublisher is mocked, so no listener can make this read pass accidentally.
    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("0");
  }

  @Test
  void closedPeriodRefusesFinancialPostingButNotPhysicalChequeReceipt() {
    doThrow(new ClosedFinancialPeriodException(date, YearMonth.from(date)))
        .when(periods)
        .assertPostingAllowed(tenant, date);
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        posting.receive(
                            obligation, receipt(UUID.randomUUID(), "1000.00", 0L), actor)))
        .isInstanceOf(ClosedFinancialPeriodException.class);
    chequeSource();
    assertThat(receiveCheque()).isNotNull();
  }

  @Test
  void invoiceAllocationAndPlanMatchPointAtOnePaymentAndDeallocationDoesNotEraseItsCash() {
    UUID invoice = invoice("1000.00");
    var received =
        in(
            tenant,
            () -> posting.receive(obligation, receipt(UUID.randomUUID(), "1000.00", 0L), actor));
    var allocation =
        in(
            tenant,
            () ->
                payments.allocatePayment(
                    received.getPaymentId(),
                    new CreateAllocationRequest(invoice, new BigDecimal("1000.00"))));
    in(
        tenant,
        () -> {
          payments.deallocatePayment(received.getPaymentId(), allocation.id());
          return null;
        });
    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("1000.00");
    assertThat(
            in(
                tenant,
                () ->
                    paymentRepository
                        .findByTenantId(tenant, PageRequest.of(0, 20))
                        .getTotalElements()))
        .isEqualTo(1);
  }

  private UUID invoice(String amount) {
    return in(
        tenant,
        () ->
            invoices
                .saveAndFlush(
                    Invoice.builder()
                        .tradingPartnerId(partner)
                        .invoiceNumber("INV-GB-" + order)
                        .issueDate(date)
                        .dueDate(date.plusMonths(1))
                        .subtotal(Money.of(new BigDecimal(amount), "GBP"))
                        .totalAmount(Money.of(new BigDecimal(amount), "GBP"))
                        .amountDue(Money.of(new BigDecimal(amount), "GBP"))
                        .amountPaid(Money.zero("GBP"))
                        .amountCredited(Money.zero("GBP"))
                        .status(InvoiceStatus.SENT)
                        .build())
                .getId());
  }

  @Test
  void refundAllocationConflictExposesPaymentAndInvoicesAtTheTopLevel() throws Exception {
    UUID payment = matchedInFull();
    UUID invoice = invoice("4100.00");
    in(
        tenant,
        () ->
            payments.allocatePayment(
                payment, new CreateAllocationRequest(invoice, new BigDecimal("4100.00"))));
    lowerTo("4000.00");
    var request = resolution(OverSettlementDecision.REFUND, "284.00", null);
    var mvc =
        MockMvcBuilders.standaloneSetup(new CollectionController(queries, collectionCommands))
            .setControllerAdvice(new GlobalExceptionHandler(null))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    var user = new AuthenticatedUserContext(actor, "FINANCE_MANAGER", List.of(), null, tenant);
    var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
    TenantContext.setCurrentTenantId(tenant);
    TenantContext.setCurrentUserId(actor);
    try {
      mvc.perform(
              post("/api/v1/finance/collections/{id}/over-settlement-resolutions", obligation)
                  .principal(authentication)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsBytes(request)))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("REFUND_EXCEEDS_UNALLOCATED"))
          .andExpect(jsonPath("$.paymentId").value(payment.toString()))
          .andExpect(jsonPath("$.allocations[0].invoiceId").value(invoice.toString()))
          .andExpect(jsonPath("$.allocations[0].amount").value("4100.00"))
          .andExpect(jsonPath("$.details").doesNotExist());
    } finally {
      TenantContext.clear();
    }
    assertThat(in(tenant, () -> refundLedger.held(tenant, payment))).isEqualByComparingTo("0");
    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("4284.00");
  }

  @Test
  void anotherTenantCannotReadOrPostToTheObligation() {
    assertThatThrownBy(
            () -> in(otherTenant, () -> queries.detail(obligation, actor, PageRequest.of(0, 20))))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                in(
                    otherTenant,
                    () ->
                        posting.receive(obligation, receipt(UUID.randomUUID(), "1.00", 0L), actor)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void rlsHidesRowsWithoutApplicationFiltersAndPreventsForeignTenantInsertion() {
    system.executeInTransaction(
        sql -> {
          sql.execute(
              "DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE"
                  + " rolname='collection_rls_probe') THEN CREATE ROLE collection_rls_probe NOLOGIN"
                  + " NOBYPASSRLS; END IF; END $$");
          sql.execute("GRANT USAGE ON SCHEMA finance TO collection_rls_probe");
          sql.execute(
              "GRANT SELECT, INSERT ON finance.collection_obligation TO collection_rls_probe");
          return null;
        });
    assertThat(
            in(
                otherTenant,
                () -> {
                  tenantJdbc.execute("SET LOCAL ROLE collection_rls_probe");
                  tenantJdbc.execute("SET LOCAL row_security=on");
                  return tenantJdbc.queryForObject(
                      "SELECT count(*) FROM finance.collection_obligation", Integer.class);
                }))
        .isZero();
    assertThatThrownBy(
            () ->
                in(
                    otherTenant,
                    () -> {
                      tenantJdbc.execute("SET LOCAL ROLE collection_rls_probe");
                      tenantJdbc.update(
                          "INSERT INTO"
                              + " finance.collection_obligation(id,tenant_id,created_at,updated_at,order_id,plan_id,logical_id,partner_id,currency,revision_id,amount,kind,method)"
                              + " VALUES(?,?,now(),now(),?,?,?,?,'GBP',?,1,'INSTALMENT','CASH')",
                          UUID.randomUUID(),
                          tenant,
                          order,
                          plan,
                          UUID.randomUUID(),
                          partner,
                          revision);
                      return null;
                    }))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void sameTenantForeignPaymentKeyIsRejectedByDatabaseEvenWithoutServiceValidation() {
    UUID foreignPayment = in(otherTenant, () -> createPayment("10.00"));
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () -> {
                      jdbc.update(
                          "INSERT INTO"
                              + " finance.collection_payment_match(id,tenant_id,created_at,updated_at,obligation_id,payment_id,amount,effective_date,occurred_at,actor_id,reason)"
                              + " VALUES(?,?,now(),now(),?,?,1,?,now(),?,'Foreign payment probe')",
                          UUID.randomUUID(),
                          tenant,
                          obligation,
                          foreignPayment,
                          date,
                          actor);
                      return null;
                    }))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void anIdempotencyReceiptCannotBeRewrittenAfterCommit() {
    var recorded =
        in(
            tenant,
            () -> posting.receive(obligation, receipt(UUID.randomUUID(), "1000.00", 0L), actor));
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () -> {
                      jdbc.update(
                          "UPDATE finance.collection_command_receipt SET request_hash='tampered'"
                              + " WHERE id=?",
                          recorded.getId());
                      return null;
                    }))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void aRecordedMatchCanOnlyBeReversedOnceAndNeverRewritten() {
    var recorded =
        in(
            tenant,
            () -> posting.receive(obligation, receipt(UUID.randomUUID(), "1000.00", 0L), actor));
    UUID match = recorded.getMatchIds().getFirst();
    // The amount, the payment and the obligation are what the evidence says; none can move.
    for (String rewrite :
        List.of(
            "amount=1",
            "reason='Rewritten'",
            "effective_date=effective_date+1",
            "obligation_id=gen_random_uuid()",
            "is_active=false")) {
      assertThatThrownBy(
              () ->
                  in(
                      tenant,
                      () -> {
                        jdbc.update(
                            "UPDATE finance.collection_payment_match SET "
                                + rewrite
                                + " WHERE id=?",
                            match);
                        return null;
                      }))
          .as(rewrite)
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    // Reversal may be added once; once there it can neither change nor be taken back.
    in(
        tenant,
        () ->
            jdbc.update(
                "UPDATE finance.collection_payment_match SET reversed_on=?,reversal_occurred_at=?,"
                    + "reversed_at=?,reversed_by=?,reversal_reason='Wrong receipt' WHERE id=?",
                java.sql.Date.valueOf(date),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now),
                actor,
                match));
    for (String undo :
        List.of(
            "reversal_reason='Other reason'", "reversed_at=NULL", "reversed_on=reversed_on+1")) {
      assertThatThrownBy(
              () ->
                  in(
                      tenant,
                      () -> {
                        jdbc.update(
                            "UPDATE finance.collection_payment_match SET " + undo + " WHERE id=?",
                            match);
                        return null;
                      }))
          .as(undo)
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
  }

  @Test
  void aClearedChequeCoversOnlyWhatIsStillHeldAfterPartOfItWasRefunded() {
    chequeSource();
    UUID cheque = receiveCheque();
    long balance = version();
    in(
        tenant,
        () ->
            cheques.transition(
                cheque, transition(UUID.randomUUID(), ChequeAction.CLEAR, 0L, balance), actor));
    lowerToWithSecondInstalment("4000.00", "500.00");
    in(
        tenant,
        () ->
            overSettlements.resolve(
                obligation, resolution(OverSettlementDecision.REFUND, "284.00", null), actor));

    // The application never changes, but 284.00 of what it covered is going back to the buyer.
    var shown = in(tenant, () -> queries.detail(obligation, actor, PageRequest.of(0, 20)));
    assertThat(new BigDecimal(shown.obligation().receivedChequeAmount()))
        .isEqualByComparingTo("4000.00");

    // The plan rises again: that 284.00 is missing, so a cheque for it is accepted.
    revision = UUID.randomUUID();
    when(source.find(any(), eq(order)))
        .thenAnswer(call -> Optional.of(agreement(Method.CHEQUE, "4284.00")));
    in(
        tenant,
        () -> {
          projection.synchronize(order);
          return null;
        });
    long version = version();
    var second =
        new CollectionRequests.ReceiveCheque(
            UUID.randomUUID(),
            partner,
            "GBP",
            new BigDecimal("284.00"),
            date,
            date.plusMonths(3),
            "NORTHBANK-REPLACEMENT-" + order,
            "Signed instrument scan",
            now,
            "Buyer replaced the refunded part with a cheque",
            List.of(
                new CollectionRequests.Application(obligation, version, new BigDecimal("284.00"))));
    assertThat(in(tenant, () -> cheques.receive(second, actor).getResourceId())).isNotNull();
  }

  private void chequeSource() {
    UUID next = UUID.randomUUID();
    revision = next;
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

  private Agreement agreement(Method method) {
    return agreement(method, "4284.00");
  }

  private Agreement agreement(Method method, String amount) {
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
                new BigDecimal(amount),
                Kind.INSTALMENT,
                method,
                date,
                false,
                method == Method.CHEQUE ? date.plusMonths(3) : null)),
        null,
        1L);
  }

  /** The plan is lowered after cash was matched: everything above the new amount is a surplus. */
  private void lowerTo(String amount) {
    revision = UUID.randomUUID();
    when(source.find(any(), eq(order)))
        .thenAnswer(call -> Optional.of(agreement(Method.BANK_TRANSFER, amount)));
    in(
        tenant,
        () -> {
          projection.synchronize(order);
          return null;
        });
  }

  /** As {@link #lowerTo}, and the difference becomes a second instalment of the same order. */
  private UUID lowerToWithSecondInstalment(String amount, String second) {
    UUID secondLogical = UUID.randomUUID();
    revision = UUID.randomUUID();
    when(source.find(any(), eq(order)))
        .thenAnswer(
            call -> {
              var first = agreement(Method.BANK_TRANSFER, amount);
              var rows = new ArrayList<>(first.rows());
              rows.add(
                  new Expectation(
                      secondLogical,
                      "GBP",
                      new BigDecimal(second),
                      Kind.INSTALMENT,
                      Method.BANK_TRANSFER,
                      date,
                      false,
                      null));
              return Optional.of(
                  new Agreement(
                      order, partner, plan, revision, 1, approval, now, now, false, false, rows,
                      null, 1L));
            });
    in(
        tenant,
        () -> {
          projection.synchronize(order);
          return null;
        });
    return in(
        tenant,
        () ->
            obligations.findByTenantIdAndOrderId(tenant, order).stream()
                .filter(value -> value.getLogicalId().equals(secondLogical))
                .findFirst()
                .orElseThrow()
                .getId());
  }

  private CollectionRequests.ResolveOverSettlement resolution(
      OverSettlementDecision decision, String amount, UUID target) {
    return new CollectionRequests.ResolveOverSettlement(
        UUID.randomUUID(),
        version(),
        decision,
        new BigDecimal(amount),
        date,
        now,
        "Buyer paid more than the lowered plan",
        target,
        target == null
            ? null
            : in(
                tenant,
                () -> obligations.findByTenantIdAndId(tenant, target).orElseThrow().getVersion()),
        null);
  }

  private UUID matchedInFull() {
    UUID payment = in(tenant, () -> createPayment("4284.00"));
    long version = version();
    in(tenant, () -> posting.match(obligation, match(payment, "4284.00", version), actor));
    return payment;
  }

  private long version() {
    return in(
        tenant,
        () -> obligations.findByTenantIdAndId(tenant, obligation).orElseThrow().getVersion());
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

  private CollectionRequests.Match match(UUID payment, String amount, Long version) {
    return new CollectionRequests.Match(
        UUID.randomUUID(),
        version,
        payment,
        new BigDecimal(amount),
        date,
        now,
        "Buyer credit matched explicitly");
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

  private UUID createPayment(String amount) {
    return payments
        .createPayment(
            new CreatePaymentRequest(
                TenantContext.requireTenantId().equals(tenant) ? partner : otherPartner,
                "INBOUND",
                "BANK_TRANSFER",
                new BigDecimal(amount),
                "GBP",
                date,
                "NORTHBANK-42",
                "Bank credit",
                null))
        .id();
  }

  private <T> T in(UUID asTenant, Supplier<T> work) {
    return TenantContext.executeInTenantContext(
        asTenant,
        () -> {
          TenantContext.setCurrentUserId(actor);
          return transactions.execute(status -> work.get());
        });
  }

  private List<Boolean> race(Supplier<?> first, Supplier<?> second) throws Exception {
    var start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var futures =
          List.of(
              executor.submit(() -> attempt(start, first)),
              executor.submit(() -> attempt(start, second)));
      start.countDown();
      return List.of(
          futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS));
    }
  }

  private boolean attempt(CountDownLatch start, Supplier<?> action) throws InterruptedException {
    start.await();
    try {
      in(tenant, action::get);
      return true;
    } catch (
        com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException conflict) {
      return false;
    }
  }

  @Test
  void aRefundedSurplusStaysHeldOnItsPaymentUntilTheOutboundPaymentThatPaysItIsUndone() {
    UUID payment = matchedInFull();
    UUID next = lowerToWithSecondInstalment("4000.00", "500.00");
    var refund = resolution(OverSettlementDecision.REFUND, "284.00", null);

    var receipt = in(tenant, () -> overSettlements.resolve(obligation, refund, actor));

    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("4000.00");
    assertThat(in(tenant, () -> refundLedger.held(tenant, payment))).isEqualByComparingTo("284.00");
    assertThat(in(tenant, () -> matches.matchedPayment(tenant, payment)))
        .isEqualByComparingTo("4000.00");
    var replay = in(tenant, () -> overSettlements.resolve(obligation, refund, actor));
    assertThat(replay.getId()).isEqualTo(receipt.getId());
    assertThat(in(tenant, () -> refundLedger.held(tenant, payment))).isEqualByComparingTo("284.00");
    var shown = in(tenant, () -> queries.detail(obligation, actor, PageRequest.of(0, 20)));
    assertThat(shown.obligation().overmatchedAmount()).isEqualTo("0.00");
    assertThat(new BigDecimal(shown.obligation().refundPendingAmount()))
        .isEqualByComparingTo("284.00");
    UUID refundId = in(tenant, () -> queries.refunds(obligation, actor)).getFirst().id();

    // While it is owed, the inbound payment can neither be undone nor spent again.
    var mistake = new ReversePaymentRequest(date, "Mistake", null);
    // A matched payment is never voided (Review-3 Y3/O1); its reversal meets the held refund.
    assertThatThrownBy(() -> in(tenant, () -> payments.voidPayment(payment)))
        .isInstanceOfSatisfying(
            PaymentReversalException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("PAYMENT_VOID_HAS_MATCHES"));
    assertThatThrownBy(() -> in(tenant, () -> payments.reverse(payment, mistake, actor)))
        .isInstanceOf(PaymentHeldForRefundException.class);
    long expected =
        in(tenant, () -> obligations.findByTenantIdAndId(tenant, next).orElseThrow().getVersion());
    var spendingHeldMoney =
        new CollectionRequests.Match(
            UUID.randomUUID(),
            expected,
            payment,
            new BigDecimal("0.01"),
            date,
            now,
            "Spending the held money");
    assertThatThrownBy(() -> in(tenant, () -> posting.match(next, spendingHeldMoney, actor)))
        .isInstanceOfSatisfying(
            com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("COLLECTION_PAYMENT_OVERMATCH"));

    UUID outbound =
        in(
            tenant,
            () ->
                payments
                    .createPayment(
                        new CreatePaymentRequest(
                            partner,
                            "OUTBOUND",
                            "BANK_TRANSFER",
                            new BigDecimal("284.00"),
                            "GBP",
                            date,
                            "REFUND-42",
                            "Refund to buyer",
                            null))
                    .id());
    in(
        tenant,
        () ->
            refundService.settle(
                refundId,
                new CollectionRequests.SettleRefund(
                    UUID.randomUUID(), 0L, outbound, now, "Refund transferred"),
                actor));
    var paid = in(tenant, () -> queries.refunds(obligation, actor)).getFirst();
    assertThat(paid.state()).isEqualTo(RefundState.SETTLED);
    assertThat(paid.settlementPaymentId()).isEqualTo(outbound);
    assertThat(in(tenant, () -> refundLedger.held(tenant, payment))).isEqualByComparingTo("284.00");

    // Taking the refund payment back means the buyer is owed the money again.
    in(
        tenant,
        () ->
            payments.reverse(
                outbound, new ReversePaymentRequest(date, "Sent in error", null), actor));
    var reopened = in(tenant, () -> queries.refunds(obligation, actor)).getFirst();
    assertThat(reopened.state()).isEqualTo(RefundState.OPEN);
    assertThat(reopened.step()).isEqualTo(2);

    in(
        tenant,
        () ->
            refundService.cancel(
                refundId,
                new CollectionRequests.CancelRefund(
                    UUID.randomUUID(), 2L, date, now, "Buyer keeps it on account"),
                actor));
    assertThat(in(tenant, () -> refundLedger.held(tenant, payment))).isEqualByComparingTo("0");
    assertThat(in(tenant, () -> queries.refunds(obligation, actor)).getFirst().state())
        .isEqualTo(RefundState.CANCELLED);
  }

  @Test
  void aReceiptWhoseRefundWasPaidIsReversedOnceTheAmountIsConfirmedAndTheRefundIsNeverOwedAgain() {
    UUID payment = matchedInFull();
    lowerToWithSecondInstalment("4000.00", "500.00");
    in(
        tenant,
        () ->
            overSettlements.resolve(
                obligation, resolution(OverSettlementDecision.REFUND, "284.00", null), actor));
    UUID refundId = in(tenant, () -> queries.refunds(obligation, actor)).getFirst().id();
    UUID outbound =
        in(
            tenant,
            () ->
                payments
                    .createPayment(
                        new CreatePaymentRequest(
                            partner,
                            "OUTBOUND",
                            "BANK_TRANSFER",
                            new BigDecimal("284.00"),
                            "GBP",
                            date,
                            "REFUND-43",
                            "Refund to buyer",
                            null))
                    .id());
    in(
        tenant,
        () ->
            refundService.settle(
                refundId,
                new CollectionRequests.SettleRefund(
                    UUID.randomUUID(), 0L, outbound, now, "Refund transferred"),
                actor));

    // The receipt was entered by mistake, but the refund really left the bank: the amount that
    // stays recorded is shown first, and nothing is posted until it is confirmed.
    var unconfirmed = new ReversePaymentRequest(date, "Receipt entered twice", null);
    assertThatThrownBy(() -> in(tenant, () -> payments.reverse(payment, unconfirmed, actor)))
        .isInstanceOfSatisfying(
            PaymentHeldForRefundException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("PAYMENT_KEEPS_PAID_REFUND");
              assertThat(e.getArgs()).containsExactly("284.00");
            });
    var confirmed =
        new ReversePaymentRequest(date, "Receipt entered twice", new BigDecimal("284.00"));
    in(tenant, () -> payments.reverse(payment, confirmed, actor));

    // The paid refund stays as recorded: that money left.
    var paid = in(tenant, () -> queries.refunds(obligation, actor)).getFirst();
    assertThat(paid.state()).isEqualTo(RefundState.SETTLED);
    assertThat(paid.step()).isEqualTo(1);

    // Taking the refund payment back does not make the buyer owed money from a receipt that no
    // longer exists: the refund reopens and is cancelled on the same day.
    in(
        tenant,
        () ->
            payments.reverse(
                outbound, new ReversePaymentRequest(date, "Bank returned it", null), actor));
    var closed = in(tenant, () -> queries.refunds(obligation, actor)).getFirst();
    assertThat(closed.state()).isEqualTo(RefundState.CANCELLED);
    assertThat(closed.step()).isEqualTo(3);
    assertThat(in(tenant, () -> refundLedger.held(tenant, payment))).isEqualByComparingTo("0");
  }

  @Test
  void advanceFreesTheSurplusOnItsPaymentWithoutOwingAnythingBack() {
    UUID payment = matchedInFull();
    lowerTo("4000.00");
    var advance = resolution(OverSettlementDecision.ADVANCE, "284.00", null);

    in(tenant, () -> overSettlements.resolve(obligation, advance, actor));

    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("4000.00");
    assertThat(in(tenant, () -> matches.matchedPayment(tenant, payment)))
        .isEqualByComparingTo("4000.00");
    assertThat(in(tenant, () -> refundLedger.held(tenant, payment))).isEqualByComparingTo("0");
    assertThat(in(tenant, () -> queries.refunds(obligation, actor))).isEmpty();
    // The freed 284.00 is the buyer's unmatched money and can be matched again by hand.
    UUID elsewhere = lowerToWithSecondInstalment("4000.00", "284.00");
    long expected =
        in(tenant, () -> obligations.findByTenantIdAndId(tenant, elsewhere).orElseThrow())
            .getVersion();
    in(
        tenant,
        () ->
            posting.match(
                elsewhere,
                new CollectionRequests.Match(
                    UUID.randomUUID(),
                    expected,
                    payment,
                    new BigDecimal("284.00"),
                    date,
                    now,
                    "Advance applied"),
                actor));
    assertThat(in(tenant, () -> matches.matchedPayment(tenant, payment)))
        .isEqualByComparingTo("4284.00");
  }

  @Test
  void reapplyMovesTheSurplusToTheNextInstalmentAndNeverOverfillsIt() {
    UUID payment = matchedInFull();
    UUID next = lowerToWithSecondInstalment("4000.00", "200.00");
    var tooMuch = resolution(OverSettlementDecision.REAPPLY, "284.00", next);
    var fitting = resolution(OverSettlementDecision.REAPPLY, "200.00", next);

    assertThatThrownBy(() -> in(tenant, () -> overSettlements.resolve(obligation, tooMuch, actor)))
        .isInstanceOf(
            com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException.class);
    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("4284.00");

    in(tenant, () -> overSettlements.resolve(obligation, fitting, actor));

    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("4084.00");
    assertThat(in(tenant, () -> posting.settled(next))).isEqualByComparingTo("200.00");
    assertThat(in(tenant, () -> matches.matchedPayment(tenant, payment)))
        .isEqualByComparingTo("4284.00");
    assertThat(in(tenant, () -> refundLedger.held(tenant, payment))).isEqualByComparingTo("0");
  }

  @Test
  void bankTransferExpectationNeverReportsMissingChequeDelivery() {
    var detail = in(tenant, () -> queries.detail(obligation, actor, PageRequest.of(0, 20)));
    assertThat(new BigDecimal(detail.obligation().missingInstrumentAmount()))
        .isEqualByComparingTo("0");
    assertThat(new BigDecimal(detail.obligation().recordedOutstandingAmount()))
        .isEqualByComparingTo("4284");
  }
}
