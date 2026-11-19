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
import com.fabricmanagement.finance.collection.dto.CollectionDtos;
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
  @Autowired CollectionCustomerViewService customerView;
  @Autowired CollectionReconciliationJob reconciliation;
  @Autowired CollectionReconciliationHealthService reconciliationHealth;
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
        MockMvcBuilders.standaloneSetup(
                new CollectionController(queries, collectionCommands, customerView))
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
                new CollectionRequests.Application(obligation, version, new BigDecimal("284.00"))),
            com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind.OWN,
            null,
            null);
    assertThat(in(tenant, () -> cheques.receive(second, actor).getResourceId())).isNotNull();
  }

  // ---- PAYMENT-PLAN-9 S1: application history -------------------------------------------------

  @Test
  void aReceivedApplicationCarriesTheEvidenceOfItsReceiptEvent() {
    chequeSource("6000.00");
    UUID cheque = receiveCheque("6000.00", "2000.00");
    var rows =
        jdbc.queryForList(
            "SELECT a.amount,a.effective_date,a.occurred_at,a.actor_id,a.reason,a.reversed_at,"
                + " e.effective_date AS event_date,e.occurred_at AS event_at,e.actor_id AS"
                + " event_actor,e.reason AS event_reason FROM finance.collection_cheque_application"
                + " a JOIN finance.collection_cheque_event e ON e.tenant_id=a.tenant_id AND"
                + " e.cheque_id=a.cheque_id AND e.action='RECEIVE' WHERE a.cheque_id=?",
            cheque);
    assertThat(rows)
        .singleElement()
        .satisfies(
            row -> {
              assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("2000.00");
              assertThat(row.get("effective_date")).isEqualTo(row.get("event_date"));
              assertThat(row.get("effective_date").toString()).isEqualTo(date.toString());
              assertThat(row.get("occurred_at")).isEqualTo(row.get("event_at"));
              assertThat(row.get("actor_id")).isEqualTo(row.get("event_actor"));
              assertThat(row.get("reason")).isEqualTo(row.get("event_reason"));
              assertThat(row.get("reversed_at")).isNull();
            });
    var view = in(tenant, () -> queries.cheque(cheque, actor));
    assertThat(new BigDecimal(view.unappliedAmount())).isEqualByComparingTo("4000.00");
  }

  @Test
  void severalActiveApplicationsToOneInstalmentAreSummedListedOnceAndClearedAsOneMatch() {
    chequeSource("6000.00");
    UUID cheque = receiveCheque("6000.00", "2000.00");
    seedApplication(cheque, "1000.00", false);
    UUID reversed = seedApplication(cheque, "500.00", true);

    var detail = in(tenant, () -> queries.detail(obligation, actor, PageRequest.of(0, 20)));
    assertThat(new BigDecimal(detail.obligation().receivedChequeAmount()))
        .isEqualByComparingTo("3000.00");
    var view = in(tenant, () -> queries.cheque(cheque, actor));
    assertThat(new BigDecimal(view.unappliedAmount())).isEqualByComparingTo("3000.00");
    assertThat(view.applications()).hasSize(2);
    var listed = in(tenant, () -> queries.chequeIds(obligation, actor, PageRequest.of(0, 20)));
    assertThat(listed.getContent()).containsExactly(cheque);
    assertThat(listed.getTotalElements()).isEqualTo(1);
    // A full first page makes Spring Data run the (distinct) count query.
    assertThat(
            in(tenant, () -> queries.chequeIds(obligation, actor, PageRequest.of(0, 1)))
                .getTotalElements())
        .isEqualTo(1);
    // The reversed application stays in the ledger.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM finance.collection_cheque_application WHERE cheque_id=?",
                Long.class,
                cheque))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT reversed_at IS NOT NULL FROM finance.collection_cheque_application"
                    + " WHERE id=?",
                Boolean.class,
                reversed))
        .isTrue();

    // One version for the one obligation; two would be refused as a duplicate.
    long version = version();
    var cleared =
        in(
            tenant,
            () ->
                cheques.transition(
                    cheque, transition(UUID.randomUUID(), ChequeAction.CLEAR, 0L, version), actor));
    assertThat(
            in(
                tenant,
                () ->
                    paymentRepository
                        .findByTenantIdAndId(tenant, cleared.getPaymentId())
                        .orElseThrow()
                        .getAmount()
                        .getAmount()))
        .isEqualByComparingTo("6000.00");
    assertThat(cleared.getMatchIds()).hasSize(1);
    assertThat(
            in(
                tenant,
                () ->
                    matches.findByTenantIdAndPaymentIdAndReversedAtIsNull(
                        tenant, cleared.getPaymentId())))
        .singleElement()
        .satisfies(
            match -> {
              assertThat(match.getAmount()).isEqualByComparingTo("3000.00");
              assertThat(match.getObligationId()).isEqualTo(obligation);
              assertThat(match.getChequeId()).isEqualTo(cheque);
            });
  }

  @Test
  void cashBesideAHeldChequeIgnoresItsReversedApplications() {
    chequeSource("6000.00");
    UUID cheque = receiveCheque("6000.00", "2000.00");
    seedApplication(cheque, "1000.00", false);
    seedApplication(cheque, "500.00", true);
    // The held cheque still covers 3,000; the reversed 500 no longer does.
    long version = version();
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        posting.receive(
                            obligation, receipt(UUID.randomUUID(), "3000.01", version), actor)))
        .isInstanceOf(
            com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException.class)
        .hasMessageContaining("have not cleared");
    in(
        tenant,
        () -> posting.receive(obligation, receipt(UUID.randomUUID(), "3000.00", version), actor));
    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("3000.00");
  }

  @Test
  void clearingIsRefusedWhenTheInstalmentCannotTakeTheSummedApplications() {
    chequeSource("6000.00");
    UUID cheque = receiveCheque("6000.00", "2000.00");
    seedApplication(cheque, "1000.00", false);
    // The plan is lowered below the two applications together, though each alone would fit.
    chequeSource("2500.00");
    long version = version();
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        cheques.transition(
                            cheque,
                            transition(UUID.randomUUID(), ChequeAction.CLEAR, 0L, version),
                            actor)))
        .isInstanceOf(
            com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException.class)
        .hasMessageContaining("outstanding");
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
  void aClearedPairWithTwoActiveApplicationsCoversOnlyWhatItsMatchStillHolds() {
    chequeSource("3000.00");
    UUID cheque = receiveCheque("3000.00", "2000.00");
    seedApplication(cheque, "1000.00", false);
    long balance = version();
    in(
        tenant,
        () ->
            cheques.transition(
                cheque, transition(UUID.randomUUID(), ChequeAction.CLEAR, 0L, balance), actor));
    lowerToWithSecondInstalment("1000.00", "500.00");
    in(
        tenant,
        () ->
            overSettlements.resolve(
                obligation, resolution(OverSettlementDecision.REFUND, "2000.00", null), actor));
    // 1,000 of the 3,000 match is still held. Capping each row would count 1,000 + 1,000.
    var shown = in(tenant, () -> queries.detail(obligation, actor, PageRequest.of(0, 20)));
    assertThat(new BigDecimal(shown.obligation().receivedChequeAmount()))
        .isEqualByComparingTo("1000.00");
  }

  @Test
  void aChequeApplicationIsEvidenceThatCanOnlyBeReversedOnceAndNeverRewritten() {
    chequeSource("6000.00");
    UUID cheque = receiveCheque("6000.00", "2000.00");
    UUID other = receiveCheque("6000.00", "1000.00");
    UUID application =
        jdbc.queryForObject(
            "SELECT id FROM finance.collection_cheque_application WHERE cheque_id=?",
            UUID.class,
            cheque);
    String update = "UPDATE finance.collection_cheque_application SET ";
    for (String rewrite :
        List.of(
            "amount=1",
            "reason='Rewritten'",
            "effective_date=effective_date+1",
            "occurred_at=occurred_at+interval '1 hour'",
            "actor_id=gen_random_uuid()",
            "cheque_id='" + other + "'",
            "created_at=created_at-interval '1 day'",
            "is_active=false",
            // Partial or impossible reversals.
            "reversed_at=created_at+interval '1 hour'",
            "reversed_on=effective_date,reversed_at=created_at+interval '1 hour',"
                + "reversal_occurred_at=created_at+interval '1 hour',reversed_by=actor_id",
            "reversed_on=effective_date-1,reversed_at=created_at+interval '1 hour',"
                + "reversal_occurred_at=created_at+interval '1 hour',reversed_by=actor_id,"
                + "reversal_reason='Too early'"))
      assertThatThrownBy(() -> jdbc.update(update + rewrite + " WHERE id=?", application))
          .as(rewrite)
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    // Bookkeeping columns the ORM writes stay writable.
    assertThat(
            jdbc.update(
                update + "updated_at=now(),updated_by=actor_id,version=version+1 WHERE id=?",
                application))
        .isEqualTo(1);
    // A complete reversal may be added once.
    assertThat(
            jdbc.update(
                update
                    + "reversed_on=effective_date+1,reversed_at=created_at+interval '1 hour',"
                    + "reversal_occurred_at=created_at+interval '1 hour',reversed_by=?,"
                    + "reversal_reason='Applied to the wrong instalment' WHERE id=?",
                actor,
                application))
        .isEqualTo(1);
    for (String undo :
        List.of("reversal_reason='Other reason'", "reversed_at=NULL", "reversed_on=reversed_on+1"))
      assertThatThrownBy(() -> jdbc.update(update + undo + " WHERE id=?", application))
          .as(undo)
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "DELETE FROM finance.collection_cheque_application WHERE id=?", application))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    // An event names an application of its own cheque, and only APPLY / REVERSE_APPLICATION name
    // one.
    assertThatThrownBy(() -> chequeEvent(other, 1, "APPLY", application))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(() -> chequeEvent(cheque, 1, "APPLY", null))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(() -> chequeEvent(cheque, 1, "PRESENT", application))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(chequeEvent(cheque, 1, "REVERSE_APPLICATION", application)).isEqualTo(1);
    // The tenant purge in clear() deletes the event before the application it names.
  }

  // ---- PAYMENT-PLAN-9 S2: a cheque held for the customer, applied to nothing --------------------

  @Test
  void anUnlinkedChequeIsHeldForTheCustomerAndClearsIntoOneUnmatchedPayment() {
    chequeSource("6000.00");
    long before = version();
    UUID cheque = receiveUnlinked("6000.00");
    // D1 (cheque part): held for the customer, nothing applied, no money and no obligation touched.
    assertThat(version()).isEqualTo(before);
    var view = in(tenant, () -> queries.cheque(cheque, actor));
    assertThat(new BigDecimal(view.unappliedAmount())).isEqualByComparingTo("6000.00");
    assertThat(view.applications()).isEmpty();
    assertThat(view.capabilities())
        .filteredOn(c -> List.of("PRESENT", "CLEAR").contains(c.action()))
        .allSatisfy(c -> assertThat(c.allowed()).isTrue());
    assertThat(in(tenant, () -> queries.chequeIds(obligation, actor, PageRequest.of(0, 20))))
        .isEmpty();

    // D3: presented, then cleared into one payment of the whole amount, matched to nothing.
    in(tenant, () -> cheques.transition(cheque, unlinked(cheque, ChequeAction.PRESENT), actor));
    var cleared =
        in(tenant, () -> cheques.transition(cheque, unlinked(cheque, ChequeAction.CLEAR), actor));
    assertThat(cleared.getMatchIds()).isEmpty();
    var payment =
        in(
            tenant,
            () ->
                paymentRepository
                    .findByTenantIdAndId(tenant, cleared.getPaymentId())
                    .orElseThrow());
    assertThat(payment.getAmount().getAmount()).isEqualByComparingTo("6000.00");
    assertThat(payment.getTradingPartnerId()).isEqualTo(partner);
    assertThat(
            in(
                tenant,
                () ->
                    matches.findByTenantIdAndPaymentIdAndReversedAtIsNull(
                        tenant, cleared.getPaymentId())))
        .isEmpty();
    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("0");
    assertThat(version()).isEqualTo(before);
    // D12: the real clearing payment is received money in payment reporting.
    assertThat(in(tenant, () -> payments.getPayment(cleared.getPaymentId()).status()))
        .isEqualTo("RECEIVED");
    assertThat(
            in(
                tenant,
                () -> paymentRepository.findByTenantId(tenant, PageRequest.of(0, 20)).getContent()))
        .extracting(value -> value.getId())
        .containsExactly(cleared.getPaymentId());
    assertThat(
            in(
                tenant,
                () -> instruments.findByTenantIdAndId(tenant, cheque).orElseThrow().getState()))
        .isEqualTo(ChequeState.CLEARED);
  }

  @Test
  void anUnlinkedChequeTouchesNoObligationWhenReturnedCancelledOrDishonoured() {
    chequeSource("6000.00");
    long before = version();
    UUID returned = receiveUnlinked("1000.00");
    UUID cancelled = receiveUnlinked("2000.00");
    UUID dishonoured = receiveUnlinked("3000.00");
    in(tenant, () -> cheques.transition(returned, unlinked(returned, ChequeAction.RETURN), actor));
    in(
        tenant,
        () -> cheques.transition(cancelled, unlinked(cancelled, ChequeAction.CANCEL), actor));
    in(
        tenant,
        () -> cheques.transition(dishonoured, unlinked(dishonoured, ChequeAction.PRESENT), actor));
    in(
        tenant,
        () ->
            cheques.transition(dishonoured, unlinked(dishonoured, ChequeAction.DISHONOUR), actor));
    assertThat(
            in(
                tenant,
                () ->
                    List.of(returned, cancelled, dishonoured).stream()
                        .map(
                            id ->
                                instruments
                                    .findByTenantIdAndId(tenant, id)
                                    .orElseThrow()
                                    .getState())
                        .toList()))
        .containsExactly(ChequeState.RETURNED, ChequeState.CANCELLED, ChequeState.DISHONOURED);
    assertThat(version()).isEqualTo(before);
    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("0");
    assertThat(
            in(
                tenant,
                () ->
                    paymentRepository
                        .findByTenantId(tenant, PageRequest.of(0, 20))
                        .getTotalElements()))
        .isZero();
    // Their history stays readable.
    assertThat(
            in(tenant, () -> queries.chequeHistory(dishonoured, actor, PageRequest.of(0, 20)))
                .getTotalElements())
        .isEqualTo(3);
  }

  /**
   * PAYMENT-PLAN-9 D12 (closeout C4): a reconciliation run, which re-reads the changed order into
   * its projection and observes voided payments, completes without failure beside an unlinked held
   * cheque, an unlinked cleared cheque and a voided orderless payment. It counts neither cheque as
   * collected on the instalment, touches neither cheque, and leaves the clearing payment an
   * ordinary unmatched received payment, still offered on the customer's payment line.
   */
  @Test
  void reconciliationRunsBesideUnlinkedChequesAndCountsNoneAsCollected() {
    chequeSource("6000.00");
    UUID held = receiveUnlinked("2500.00");
    UUID cleared = receiveUnlinked("6000.00");
    UUID clearingPayment =
        in(tenant, () -> cheques.transition(cleared, unlinked(cleared, ChequeAction.CLEAR), actor))
            .getPaymentId();
    UUID voided = customerReceipt(UUID.randomUUID(), "300.00");
    in(tenant, () -> payments.voidPayment(voided));
    // The order reads as changed, so the run re-reads it into its projection.
    UUID first = new UUID(0, 0);
    when(source.stamps(eq(tenant), any(), anyInt()))
        .thenAnswer(
            call ->
                first.equals(call.getArgument(1))
                    ? List.of(new SourceStamp(order, 99L, 99L, revision, false))
                    : List.of());
    // No writer holds the order, so the run may read it now (a held order is left for later).
    when(source.tryHold(any(), eq(order))).thenReturn(true);
    clearInvocations(source);
    TenantContext.executeInTenantContext(
        tenant,
        () -> {
          TenantContext.setCurrentUserId(actor);
          reconciliation.reconcileTenant();
          return null;
        });
    verify(source).find(any(), eq(order));
    assertThat(TenantContext.executeInTenantContext(tenant, () -> reconciliationHealth.stale()))
        .as("the run finished without failure")
        .isFalse();
    // Neither cheque is collected money on the instalment.
    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("0");
    assertThat(received()).isEqualByComparingTo("0");
    assertThat(
            in(
                tenant,
                () ->
                    List.of(held, cleared).stream()
                        .map(id -> instruments.findByTenantIdAndId(tenant, id).orElseThrow())
                        .map(CollectionCheque::getState)
                        .toList()))
        .containsExactly(ChequeState.RECEIVED, ChequeState.CLEARED);
    // The clearing payment stays an ordinary received payment, matched to nothing.
    assertThat(in(tenant, () -> payments.getPayment(clearingPayment).status()))
        .isEqualTo("RECEIVED");
    assertThat(
            in(
                tenant,
                () ->
                    matches.findByTenantIdAndPaymentIdAndReversedAtIsNull(tenant, clearingPayment)))
        .isEmpty();
    // The customer's two lines: the clearing payment as money, the held cheque as a cheque.
    lines("6000.00", "2500.00");
  }

  // ---- PAYMENT-PLAN-9 S3: allocate later and reverse a wrong application ------------------------

  @Test
  void aChequeAllocatedLaterClearsIntoAMatchForTheAllocatedPartOnly() {
    chequeSource("4000.00");
    UUID cheque = receiveUnlinked("6000.00");
    in(
        tenant,
        () -> cheques.allocate(cheque, allocation(cheque, UUID.randomUUID(), "4000.00"), actor));
    assertThat(new BigDecimal(in(tenant, () -> queries.cheque(cheque, actor)).unappliedAmount()))
        .isEqualByComparingTo("2000.00");
    var cleared =
        in(
            tenant,
            () ->
                cheques.transition(
                    cheque,
                    transition(
                        UUID.randomUUID(), ChequeAction.CLEAR, chequeVersion(cheque), version()),
                    actor));
    // D5: one payment for the face amount, matched for the allocated part; 2,000 stays on it.
    assertThat(
            in(
                tenant,
                () ->
                    paymentRepository
                        .findByTenantIdAndId(tenant, cleared.getPaymentId())
                        .orElseThrow()
                        .getAmount()
                        .getAmount()))
        .isEqualByComparingTo("6000.00");
    assertThat(in(tenant, () -> matches.matchedPayment(tenant, cleared.getPaymentId())))
        .isEqualByComparingTo("4000.00");
    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("4000.00");
  }

  @Test
  void allocationsAreRefusedAboveTheUnappliedAmountAboveTheShortfallAndAfterClearing() {
    chequeSource("6000.00");
    UUID small = receiveUnlinked("1000.00");
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        cheques.allocate(
                            small, allocation(small, UUID.randomUUID(), "1000.01"), actor)))
        .isInstanceOf(
            com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException.class)
        .hasMessageContaining("unapplied amount");
    receiveCheque("5000.00", "5000.00");
    UUID large = receiveUnlinked("6000.00");
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        cheques.allocate(
                            large, allocation(large, UUID.randomUUID(), "1000.01"), actor)))
        .isInstanceOf(
            com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException.class)
        .hasMessageContaining("missing instrument amount");
    in(tenant, () -> cheques.transition(large, unlinked(large, ChequeAction.CLEAR), actor));
    // D7: after clearing the cheque is never allocated; its payment is.
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        cheques.allocate(
                            large, allocation(large, UUID.randomUUID(), "500.00"), actor)))
        .isInstanceOf(
            com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException.class)
        .hasFieldOrPropertyWithValue("errorCode", "CHEQUE_APPLICATION_NOT_ALLOWED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM finance.collection_cheque_application WHERE cheque_id IN"
                    + " (?,?)",
                Long.class,
                small,
                large))
        .isZero();
  }

  @Test
  void anAllocationReplaysOnceAndAReusedKeyWithOtherContentIsRefused() {
    chequeSource("6000.00");
    UUID cheque = receiveUnlinked("6000.00");
    UUID operation = UUID.randomUUID();
    var body = allocation(cheque, operation, "2500.00");
    var first = in(tenant, () -> cheques.allocate(cheque, body, actor));
    var replay = in(tenant, () -> cheques.allocate(cheque, body, actor));
    assertThat(replay.getId()).isEqualTo(first.getId());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM finance.collection_cheque_application WHERE cheque_id=?",
                Long.class,
                cheque))
        .isEqualTo(1);
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () -> cheques.allocate(cheque, allocation(cheque, operation, "100.00"), actor)))
        .isInstanceOf(
            com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException.class)
        .hasFieldOrPropertyWithValue("errorCode", "COLLECTION_OPERATION_REUSED");
  }

  @Test
  void aWrongApplicationIsReversedItsCoverageReleasedAndTheChequeStaysHeld() {
    chequeSource("6000.00");
    UUID cheque = receiveCheque("6000.00", "2000.00");
    // A second application to the same instalment, in a separate request.
    in(
        tenant,
        () -> cheques.allocate(cheque, allocation(cheque, UUID.randomUUID(), "1000.00"), actor));
    var view = in(tenant, () -> queries.cheque(cheque, actor));
    assertThat(view.applications()).hasSize(2).allSatisfy(a -> assertThat(a.reversible()).isTrue());
    UUID second = applicationOf(cheque, "1000.00");
    UUID first = applicationOf(cheque, "2000.00");

    in(
        tenant,
        () ->
            cheques.reverseApplication(cheque, second, reversal(cheque, UUID.randomUUID()), actor));
    assertThat(received()).isEqualByComparingTo("2000.00");
    in(
        tenant,
        () ->
            cheques.reverseApplication(cheque, first, reversal(cheque, UUID.randomUUID()), actor));
    // D17: coverage released, the remainder restored, history kept; nothing returned or received.
    assertThat(received()).isEqualByComparingTo("0");
    var after = in(tenant, () -> queries.cheque(cheque, actor));
    assertThat(new BigDecimal(after.unappliedAmount())).isEqualByComparingTo("6000.00");
    assertThat(after.applications()).isEmpty();
    assertThat(after.state()).isEqualTo(ChequeState.RECEIVED);
    assertThat(new BigDecimal(after.amount())).isEqualByComparingTo("6000.00");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM finance.collection_cheque_application WHERE cheque_id=? AND"
                    + " reversed_at IS NOT NULL",
                Long.class,
                cheque))
        .isEqualTo(2);
    var history =
        in(tenant, () -> queries.chequeHistory(cheque, actor, PageRequest.of(0, 20))).getContent();
    assertThat(history)
        .extracting(CollectionDtos.ChequeEvent::action)
        .containsExactly("REVERSE_APPLICATION", "REVERSE_APPLICATION", "APPLY", "RECEIVE");
    assertThat(history.getFirst().application())
        .satisfies(
            target -> {
              assertThat(target.id()).isEqualTo(first);
              assertThat(target.obligationId()).isEqualTo(obligation);
              assertThat(target.targetVisible()).isTrue();
            });
    assertThat(history)
        .allSatisfy(event -> assertThat(event.state()).isEqualTo(ChequeState.RECEIVED));
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
  void aReversalReplaysAndAStaleOrCompetingReversalCannotReleaseTheSameAmountTwice()
      throws Exception {
    chequeSource("6000.00");
    UUID cheque = receiveCheque("6000.00", "2000.00");
    UUID application = applicationOf(cheque, "2000.00");
    var stale = reversal(cheque, UUID.randomUUID());
    var body = reversal(cheque, UUID.randomUUID());
    var first = in(tenant, () -> cheques.reverseApplication(cheque, application, body, actor));
    var replay = in(tenant, () -> cheques.reverseApplication(cheque, application, body, actor));
    assertThat(replay.getId()).isEqualTo(first.getId());
    assertThatThrownBy(
            () -> in(tenant, () -> cheques.reverseApplication(cheque, application, stale, actor)))
        .isInstanceOf(
            com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException.class)
        .hasFieldOrPropertyWithValue("errorCode", "COLLECTION_VERSION_CONFLICT");

    // Two reversals of one application racing with the same versions: one wins.
    UUID racing = receiveCheque("4000.00", "4000.00");
    UUID target = applicationOf(racing, "4000.00");
    var one = reversal(racing, UUID.randomUUID());
    var two = reversal(racing, UUID.randomUUID());
    var results =
        race(
            () -> cheques.reverseApplication(racing, target, one, actor),
            () -> cheques.reverseApplication(racing, target, two, actor));
    assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
    assertThat(received()).isEqualByComparingTo("0");
  }

  @Test
  void aStaleObligationVersionRefusesTheReversalAndTwoAllocationsCannotUseOneRemainder()
      throws Exception {
    chequeSource("6000.00");
    UUID cheque = receiveCheque("4000.00", "2000.00");
    UUID application = applicationOf(cheque, "2000.00");
    var stale = reversal(cheque, UUID.randomUUID());
    // The obligation moves (another cheque received for it) after the reversal was prepared.
    receiveCheque("1000.00", "1000.00");
    var refreshed =
        new CollectionRequests.ReverseChequeApplication(
            UUID.randomUUID(),
            stale.expectedVersion(),
            stale.expectedObligationVersion(),
            date,
            now,
            "Applied to the wrong instalment",
            "Buyer's remittance advice names another order");
    assertThatThrownBy(
            () ->
                in(tenant, () -> cheques.reverseApplication(cheque, application, refreshed, actor)))
        .isInstanceOf(
            com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException.class)
        .hasFieldOrPropertyWithValue("errorCode", "COLLECTION_VERSION_CONFLICT");

    // 2,000 left on the cheque; two allocations of 1,500 race for it: one wins.
    var one = allocation(cheque, UUID.randomUUID(), "1500.00");
    var two = allocation(cheque, UUID.randomUUID(), "1500.00");
    var results =
        race(
            () -> cheques.allocate(cheque, one, actor), () -> cheques.allocate(cheque, two, actor));
    assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
    assertThat(new BigDecimal(in(tenant, () -> queries.cheque(cheque, actor)).unappliedAmount()))
        .isEqualByComparingTo("500.00");
  }

  @Test
  void anApplicationIsNotReversedAfterClearing() {
    chequeSource("6000.00");
    UUID cheque = receiveCheque("6000.00", "2000.00");
    UUID application = applicationOf(cheque, "2000.00");
    var cleared =
        in(
            tenant,
            () ->
                cheques.transition(
                    cheque,
                    transition(UUID.randomUUID(), ChequeAction.CLEAR, 0L, version()),
                    actor));
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        cheques.reverseApplication(
                            cheque, application, reversal(cheque, UUID.randomUUID()), actor)))
        .isInstanceOf(
            com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException.class)
        .hasFieldOrPropertyWithValue("errorCode", "CHEQUE_APPLICATION_NOT_ALLOWED");
    // D19: payment and clearing unchanged.
    assertThat(in(tenant, () -> matches.matchedPayment(tenant, cleared.getPaymentId())))
        .isEqualByComparingTo("2000.00");
    assertThat(
            in(
                tenant,
                () -> instruments.findByTenantIdAndId(tenant, cheque).orElseThrow().getState()))
        .isEqualTo(ChequeState.CLEARED);
  }

  @Test
  void onlyTheApplicationsStillActiveProduceMatchesWhenTheChequeClears() {
    chequeSource("6000.00");
    UUID cheque = receiveCheque("6000.00", "2000.00");
    in(
        tenant,
        () -> cheques.allocate(cheque, allocation(cheque, UUID.randomUUID(), "1500.00"), actor));
    in(
        tenant,
        () ->
            cheques.reverseApplication(
                cheque,
                applicationOf(cheque, "2000.00"),
                reversal(cheque, UUID.randomUUID()),
                actor));
    var cleared =
        in(
            tenant,
            () ->
                cheques.transition(
                    cheque,
                    transition(
                        UUID.randomUUID(), ChequeAction.CLEAR, chequeVersion(cheque), version()),
                    actor));
    // D20: one match for the remaining 1,500; 4,500 of the payment stays unmatched.
    assertThat(
            in(
                tenant,
                () ->
                    matches.findByTenantIdAndPaymentIdAndReversedAtIsNull(
                        tenant, cleared.getPaymentId())))
        .singleElement()
        .satisfies(match -> assertThat(match.getAmount()).isEqualByComparingTo("1500.00"));
    assertThat(in(tenant, () -> matches.matchedPayment(tenant, cleared.getPaymentId())))
        .isEqualByComparingTo("1500.00");
  }

  // ---- PAYMENT-PLAN-9 S4: orderless payments, allocation after clearing, customer view
  // -----------

  @Test
  void orderlessPaymentAndChequeAreTwoSeparateLinesAndNothingIsAllocatedByItself() {
    UUID payment = customerReceipt(UUID.randomUUID(), "10000.00");
    chequeSource("7000.00");
    UUID cheque = receiveUnlinked("6000.00");
    // D1: two lines, never one combined figure.
    lines("10000.00", "6000.00");
    assertThat(gbp().payments())
        .singleElement()
        .satisfies(p -> assertThat(p.id()).isEqualTo(payment));
    assertThat(gbp().cheques())
        .singleElement()
        .satisfies(c -> assertThat(c.id()).isEqualTo(cheque));
    // D8: a new obligation pulls in nothing.
    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("0");
    assertThat(received()).isEqualByComparingTo("0");
    // D2: finance allocates 7,000 of the payment; 3,000 stays, the cheque line is untouched.
    long version = version();
    in(tenant, () -> posting.match(obligation, match(payment, "7000.00", version), actor));
    lines("3000.00", "6000.00");
    assertThat(gbp().payments().getFirst().matchedAmount()).isEqualTo("7000.00");
  }

  @Test
  void theViewAndTheMatchGuardOfferTheSameAmountAndInvoiceAllocationIsOnlyLabelled() {
    UUID payment = customerReceipt(UUID.randomUUID(), "10000.00");
    chequeSource("7000.00");
    long version = version();
    in(tenant, () -> posting.match(obligation, match(payment, "7000.00", version), actor));
    // D13: 3,000 available, with no invoice allocation.
    lines("3000.00", "0.00");
    // D14: the same payment also pays 7,000 of an invoice; instalment availability stays 3,000.
    UUID invoice = invoice("7000.00");
    in(
        tenant,
        () ->
            payments.allocatePayment(
                payment, new CreateAllocationRequest(invoice, new BigDecimal("7000.00"))));
    lines("3000.00", "0.00");
    assertThat(gbp().payments().getFirst().invoiceAllocatedAmount()).isEqualTo("7000.00");
    // The guard accepts exactly what the view shows.
    var second = lowerToWithSecondInstalment("7000.00", "5000.00");
    long secondVersion =
        in(
            tenant,
            () -> obligations.findByTenantIdAndId(tenant, second).orElseThrow().getVersion());
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () -> posting.match(second, match(payment, "3000.01", secondVersion), actor)))
        .isInstanceOf(
            com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException.class)
        .hasFieldOrPropertyWithValue("errorCode", "COLLECTION_PAYMENT_OVERMATCH");
    in(tenant, () -> posting.match(second, match(payment, "3000.00", secondVersion), actor));
    assertThat(gbp()).isNull();
  }

  @Test
  void voidedReversedAndReversalPaymentsAreNotAvailableAndRefundHoldsReduceWhatIs() {
    UUID voided = customerReceipt(UUID.randomUUID(), "100.00");
    in(tenant, () -> payments.voidPayment(voided));
    UUID reversed = customerReceipt(UUID.randomUUID(), "200.00");
    in(
        tenant,
        () ->
            payments.reverse(
                reversed, new ReversePaymentRequest(date, "Receipt entered twice", null), actor));
    // D15: neither the voided, the reversed nor its reversal entry is offered.
    assertThat(gbp()).isNull();
    UUID payment = customerReceipt(UUID.randomUUID(), "5000.00");
    long version = version();
    in(tenant, () -> posting.match(obligation, match(payment, "4284.00", version), actor));
    lines("716.00", "0.00");
    lowerTo("4000.00");
    in(
        tenant,
        () ->
            overSettlements.resolve(
                obligation, resolution(OverSettlementDecision.REFUND, "284.00", null), actor));
    // 4,000 matched and 284 held to refund: still 716, not 1,000.
    lines("716.00", "0.00");
    assertThat(gbp().payments().getFirst().refundHeldAmount()).isEqualTo("284.00");
  }

  @Test
  void aLostOrderlessReceiptResponseRetriedReturnsTheOriginalPaymentOnly() {
    UUID operation = UUID.randomUUID();
    var body = customerReceiptRequest(operation, "10000.00");
    var first = in(tenant, () -> posting.receiveForCustomer(body, actor));
    var retry = in(tenant, () -> posting.receiveForCustomer(body, actor));
    // D16: one payment, the original result.
    assertThat(retry.getId()).isEqualTo(first.getId());
    assertThat(retry.getPaymentId()).isEqualTo(first.getPaymentId());
    assertThat(
            in(
                tenant,
                () ->
                    paymentRepository
                        .findByTenantId(tenant, PageRequest.of(0, 20))
                        .getTotalElements()))
        .isEqualTo(1);
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        posting.receiveForCustomer(
                            customerReceiptRequest(operation, "9000.00"), actor)))
        .isInstanceOf(
            com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException.class)
        .hasFieldOrPropertyWithValue("errorCode", "COLLECTION_OPERATION_REUSED");
  }

  @Test
  void aClearedChequeMovesFromTheChequeLineToThePaymentLineAndAFailedOneLeavesBoth() {
    chequeSource("6000.00");
    UUID cheque = receiveUnlinked("6000.00");
    UUID returned = receiveUnlinked("1500.00");
    lines("0.00", "7500.00");
    in(tenant, () -> cheques.transition(returned, unlinked(returned, ChequeAction.RETURN), actor));
    // D4: a returned cheque leaves the available totals but stays in history.
    lines("0.00", "6000.00");
    var view = in(tenant, () -> customerView.availability(partner, actor));
    assertThat(view.chequeHistory())
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.id()).isEqualTo(returned);
              assertThat(c.state()).isEqualTo(ChequeState.RETURNED);
              assertThat(c.availableAmount()).isEqualTo("0.00");
            });
    var cleared =
        in(tenant, () -> cheques.transition(cheque, unlinked(cheque, ChequeAction.CLEAR), actor));
    // D3: the cheque line drops by 6,000 and the payment line rises by 6,000.
    lines("6000.00", "0.00");
    assertThat(gbp().payments())
        .singleElement()
        .satisfies(
            p -> {
              assertThat(p.id()).isEqualTo(cleared.getPaymentId());
              assertThat(p.clearedChequeId()).isEqualTo(cheque);
            });
  }

  @Test
  void aClearedChequesRemainderIsMatchedOnceAndBlocksTheClearingReversalUntilReversed() {
    chequeSource("4000.00");
    UUID cheque = receiveUnlinked("6000.00");
    in(
        tenant,
        () -> cheques.allocate(cheque, allocation(cheque, UUID.randomUUID(), "4000.00"), actor));
    var cleared =
        in(
            tenant,
            () ->
                cheques.transition(
                    cheque,
                    transition(
                        UUID.randomUUID(), ChequeAction.CLEAR, chequeVersion(cheque), version()),
                    actor));
    UUID payment = cleared.getPaymentId();
    lines("2000.00", "0.00");
    // R4: the remainder is matched through the clearing payment, without the cheque's mark.
    var second = lowerToWithSecondInstalment("4000.00", "3000.00");
    long secondVersion =
        in(
            tenant,
            () -> obligations.findByTenantIdAndId(tenant, second).orElseThrow().getVersion());
    var later =
        in(tenant, () -> posting.match(second, match(payment, "2000.00", secondVersion), actor));
    UUID laterMatch = later.getMatchIds().getFirst();
    assertThat(
            in(
                tenant,
                () -> matches.findByTenantIdAndId(tenant, laterMatch).orElseThrow().getChequeId()))
        .isNull();
    // D7: the remainder cannot be used twice.
    long again =
        in(
            tenant,
            () -> obligations.findByTenantIdAndId(tenant, second).orElseThrow().getVersion());
    assertThatThrownBy(
            () -> in(tenant, () -> posting.match(second, match(payment, "0.01", again), actor)))
        .isInstanceOf(
            com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException.class)
        .hasFieldOrPropertyWithValue("errorCode", "COLLECTION_PAYMENT_OVERMATCH");
    // D21: the clearing cannot be reversed while the later match stands.
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        cheques.transition(
                            cheque,
                            transition(
                                UUID.randomUUID(),
                                ChequeAction.DISHONOUR,
                                chequeVersion(cheque),
                                version()),
                            actor)))
        .isInstanceOf(
            com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException.class)
        .hasFieldOrPropertyWithValue("errorCode", "CHEQUE_CLEARING_HAS_OTHER_BINDINGS");
    in(
        tenant,
        () ->
            posting.reverse(
                laterMatch,
                new CollectionRequests.Reverse(
                    UUID.randomUUID(), again, date, now, "Customer asked to keep it on account"),
                actor));
    in(
        tenant,
        () ->
            cheques.transition(
                cheque,
                transition(
                    UUID.randomUUID(), ChequeAction.DISHONOUR, chequeVersion(cheque), version()),
                actor));
    // No spendable remainder survives the reversed clearing payment.
    assertThat(gbp()).isNull();
    assertThat(
            in(
                tenant,
                () ->
                    paymentRepository
                        .findByTenantIdAndId(tenant, payment)
                        .orElseThrow()
                        .isReversed()))
        .isTrue();
  }

  private UUID customerReceipt(UUID operation, String amount) {
    return in(
            tenant,
            () -> posting.receiveForCustomer(customerReceiptRequest(operation, amount), actor))
        .getPaymentId();
  }

  private CollectionRequests.CustomerReceipt customerReceiptRequest(UUID operation, String amount) {
    return new CollectionRequests.CustomerReceipt(
        operation,
        partner,
        "GBP",
        new BigDecimal(amount),
        Method.BANK_TRANSFER,
        date,
        now,
        "NORTHBANK-TRANSFER-77",
        "Northbridge Cloth paid ahead of its first order");
  }

  /** The customer's GBP lines, or null when it has nothing available in GBP. */
  private CollectionDtos.CurrencyAvailability gbp() {
    return in(tenant, () -> customerView.availability(partner, actor)).currencies().stream()
        .filter(line -> line.currency().equals("GBP"))
        .findFirst()
        .orElse(null);
  }

  private void lines(String paymentsAvailable, String chequesAvailable) {
    var line = gbp();
    assertThat(line).isNotNull();
    assertThat(new BigDecimal(line.paymentsAvailable())).isEqualByComparingTo(paymentsAvailable);
    assertThat(new BigDecimal(line.chequesAvailable())).isEqualByComparingTo(chequesAvailable);
  }

  private CollectionRequests.AllocateCheque allocation(UUID cheque, UUID operation, String amount) {
    long chequeVersion = chequeVersion(cheque);
    long version = version();
    return new CollectionRequests.AllocateCheque(
        operation,
        chequeVersion,
        date,
        now,
        "Buyer asked to use the cheque for this instalment",
        "E-mail from the buyer's accounts team",
        List.of(new CollectionRequests.Application(obligation, version, new BigDecimal(amount))));
  }

  private CollectionRequests.ReverseChequeApplication reversal(UUID cheque, UUID operation) {
    return new CollectionRequests.ReverseChequeApplication(
        operation,
        chequeVersion(cheque),
        version(),
        date,
        now,
        "Applied to the wrong instalment",
        "Buyer's remittance advice names another order");
  }

  private long chequeVersion(UUID cheque) {
    return in(
        tenant, () -> instruments.findByTenantIdAndId(tenant, cheque).orElseThrow().getVersion());
  }

  private UUID applicationOf(UUID cheque, String amount) {
    return jdbc.queryForObject(
        "SELECT id FROM finance.collection_cheque_application WHERE cheque_id=? AND"
            + " amount=CAST(? AS numeric) AND reversed_at IS NULL",
        UUID.class,
        cheque,
        amount);
  }

  private BigDecimal received() {
    return new BigDecimal(
        in(tenant, () -> queries.detail(obligation, actor, PageRequest.of(0, 20)))
            .obligation()
            .receivedChequeAmount());
  }

  @Test
  void anEndorsedChequeHeldForAnUnknownCustomerIsRefused() {
    var request =
        new CollectionRequests.ReceiveCheque(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "GBP",
            new BigDecimal("6000.00"),
            date,
            date.plusMonths(3),
            "NORTHBANK-ENDORSED",
            "Signed instrument scan",
            now,
            "Kept for the buyer's future purchases",
            List.of(),
            com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind.ENDORSED,
            null,
            "Harrowgate Mills Ltd");
    assertThatThrownBy(() -> in(tenant, () -> cheques.receive(request, actor)))
        .isInstanceOf(
            com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException.class)
        .hasFieldOrPropertyWithValue("errorCode", "COLLECTION_PARTNER_NOT_FOUND");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM finance.collection_cheque WHERE tenant_id=?",
                Long.class,
                tenant))
        .isZero();
  }

  /**
   * PAYMENT-PLAN-9 C1 (TD-44), through the real partner port: a supplier-only partner and a removed
   * customer take no orderless payment and no held cheque; the customer view hides the supplier and
   * still reads the removed customer's history. A customer on hold is not refused.
   */
  @Test
  void receiptsAreTakenOnlyFromACustomerThatWasNotRemoved() {
    UUID supplier = UUID.randomUUID(), removed = UUID.randomUUID(), onHold = UUID.randomUUID();
    system.executeInTransaction(
        sql -> {
          // One registry entry each: a tenant holds a registry entry once (uk_tp_tenant_registry).
          for (var partnerRow :
              List.of(
                  List.of(supplier, "Calder Yarns", "SUPPLIER", true, "ACTIVE"),
                  List.of(removed, "Ribble Weaving", "CUSTOMER", false, "ACTIVE"),
                  List.of(onHold, "Aire Dyers", "BOTH", true, "SUSPENDED"))) {
            UUID registry = UUID.randomUUID();
            sql.update(
                "INSERT INTO common_company.trading_partner_registry(id,uid,official_name,country)"
                    + " VALUES(?,?,?,'GBR')",
                registry,
                registry.toString(),
                partnerRow.get(1));
            sql.update(
                "INSERT INTO common_company.common_trading_partner"
                    + "(id,tenant_id,uid,registry_id,partner_type,is_active,status)"
                    + " VALUES(?,?,?,?,?,?,?)",
                partnerRow.get(0),
                tenant,
                partnerRow.get(0).toString(),
                registry,
                partnerRow.get(2),
                partnerRow.get(3),
                partnerRow.get(4));
          }
          return null;
        });
    for (var refused :
        List.of(
            List.of(supplier, "COLLECTION_PARTNER_NOT_CUSTOMER"),
            List.of(removed, "COLLECTION_PARTNER_REMOVED"))) {
      UUID partnerId = (UUID) refused.get(0);
      String code = (String) refused.get(1);
      assertThatThrownBy(
              () ->
                  in(
                      tenant,
                      () ->
                          posting.receiveForCustomer(
                              customerReceiptFrom(partnerId, UUID.randomUUID()), actor)))
          .isInstanceOf(
              com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException
                  .class)
          .hasFieldOrPropertyWithValue("errorCode", code);
      assertThatThrownBy(
              () -> in(tenant, () -> cheques.receive(endorsedChequeFrom(partnerId), actor)))
          .isInstanceOf(
              com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException
                  .class)
          .hasFieldOrPropertyWithValue("errorCode", code);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM finance.collection_cheque WHERE tenant_id=? AND partner_id IN"
                    + " (?,?)",
                Long.class,
                tenant,
                supplier,
                removed))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM finance.collection_command_receipt WHERE tenant_id=?"
                    + " AND resource_type='PAYMENT'",
                Long.class,
                tenant))
        .isZero();
    // The view: a supplier reads like an unknown partner; a removed customer's history stays.
    assertThatThrownBy(() -> in(tenant, () -> customerView.availability(supplier, actor)))
        .isInstanceOf(
            com.fabricmanagement.common.infrastructure.web.exception.NotFoundException.class);
    assertThat(in(tenant, () -> customerView.availability(removed, actor)).partnerId())
        .isEqualTo(removed);
    // A customer on hold may still pay an old debt.
    assertThat(
            in(
                    tenant,
                    () ->
                        posting.receiveForCustomer(
                            customerReceiptFrom(onHold, UUID.randomUUID()), actor))
                .getPaymentId())
        .isNotNull();
  }

  private CollectionRequests.CustomerReceipt customerReceiptFrom(UUID partnerId, UUID operation) {
    return new CollectionRequests.CustomerReceipt(
        operation,
        partnerId,
        "GBP",
        new BigDecimal("2500.00"),
        Method.BANK_TRANSFER,
        date,
        now,
        "NORTHBANK-TRANSFER-88",
        "Paid without an order");
  }

  private CollectionRequests.ReceiveCheque endorsedChequeFrom(UUID partnerId) {
    return new CollectionRequests.ReceiveCheque(
        UUID.randomUUID(),
        partnerId,
        "GBP",
        new BigDecimal("1500.00"),
        date,
        date.plusMonths(2),
        "NORTHBANK-" + UUID.randomUUID().toString().substring(0, 8),
        "Signed instrument scan",
        now,
        "Kept for future purchases",
        List.of(),
        com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind.ENDORSED,
        null,
        "Harrowgate Mills Ltd");
  }

  private UUID receiveUnlinked(String amount) {
    return in(
        tenant,
        () ->
            cheques
                .receive(
                    new CollectionRequests.ReceiveCheque(
                        UUID.randomUUID(),
                        partner,
                        "GBP",
                        new BigDecimal(amount),
                        date,
                        date.plusMonths(3),
                        "NORTHBANK-" + UUID.randomUUID().toString().substring(0, 8),
                        "Signed instrument scan",
                        now,
                        "Kept for the buyer's future purchases",
                        List.of(),
                        com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind
                            .OWN,
                        null,
                        null),
                    actor)
                .getResourceId());
  }

  private CollectionRequests.Transition unlinked(UUID cheque, ChequeAction action) {
    long chequeVersion =
        in(
            tenant,
            () -> instruments.findByTenantIdAndId(tenant, cheque).orElseThrow().getVersion());
    return new CollectionRequests.Transition(
        UUID.randomUUID(),
        chequeVersion,
        action,
        date,
        now,
        "Bank evidence reviewed",
        "Instrument and bank statement verified",
        null,
        null,
        null);
  }

  /** History written straight to the table: S3 adds the commands that write it. */
  private UUID seedApplication(UUID cheque, String amount, boolean reversed) {
    UUID id = UUID.randomUUID();
    String columns =
        "INSERT INTO"
            + " finance.collection_cheque_application(id,uid,tenant_id,cheque_id,obligation_id,"
            + "amount,effective_date,occurred_at,actor_id,reason,created_at,updated_at";
    if (reversed)
      jdbc.update(
          columns
              + ",reversed_on,reversed_at,reversal_occurred_at,reversed_by,reversal_reason)"
              + " VALUES(?,?,?,?,?,CAST(? AS numeric),?,?,?,'Second part agreed with the buyer',"
              + "now(),now(),?,now(),now(),?,'Applied to the wrong instalment')",
          id,
          id.toString(),
          tenant,
          cheque,
          obligation,
          amount,
          java.sql.Date.valueOf(date),
          java.sql.Timestamp.from(now),
          actor,
          java.sql.Date.valueOf(date),
          actor);
    else
      jdbc.update(
          columns
              + ") VALUES(?,?,?,?,?,CAST(? AS numeric),?,?,?,'Second part agreed with the buyer',"
              + "now(),now())",
          id,
          id.toString(),
          tenant,
          cheque,
          obligation,
          amount,
          java.sql.Date.valueOf(date),
          java.sql.Timestamp.from(now),
          actor);
    return id;
  }

  private int chequeEvent(UUID cheque, int revision, String action, UUID application) {
    UUID id = UUID.randomUUID();
    String sql =
        "INSERT INTO finance.collection_cheque_event(id,uid,tenant_id,cheque_id,evidence_revision,"
            + "action,state,effective_date,occurred_at,actor_id,reason,evidence,amount,received_on,"
            + "due_on,instrument_reference,application_id,created_at,updated_at)"
            + " VALUES(?,?,?,?,?,?,'RECEIVED',?,?,?,'Application corrected','Finance review',6000,"
            + "?,?,'NORTHBANK-EVIDENCE',"
            + (application == null ? "NULL" : "'" + application + "'")
            + ",now(),now())";
    return jdbc.update(
        sql,
        id,
        id.toString(),
        tenant,
        cheque,
        revision,
        action,
        java.sql.Date.valueOf(date),
        java.sql.Timestamp.from(now),
        actor,
        java.sql.Date.valueOf(date),
        java.sql.Date.valueOf(date.plusMonths(3)));
  }

  private void chequeSource(String amount) {
    revision = UUID.randomUUID();
    when(source.find(any(), eq(order)))
        .thenAnswer(call -> Optional.of(agreement(Method.CHEQUE, amount)));
    in(
        tenant,
        () -> {
          projection.synchronize(order);
          return null;
        });
  }

  private UUID receiveCheque(String amount, String applied) {
    return receiveChequeAs(actor, amount, applied);
  }

  /** Received by {@code user}, who therefore is the cheque's creator (R6). */
  private UUID receiveChequeAs(UUID user, String amount, String applied) {
    long version = version();
    return asUser(
        user,
        () ->
            cheques
                .receive(
                    new CollectionRequests.ReceiveCheque(
                        UUID.randomUUID(),
                        partner,
                        "GBP",
                        new BigDecimal(amount),
                        date,
                        date.plusMonths(3),
                        "NORTHBANK-" + UUID.randomUUID().toString().substring(0, 8),
                        "Signed instrument scan",
                        now,
                        "Buyer handed cheque to finance",
                        List.of(
                            new CollectionRequests.Application(
                                obligation, version, new BigDecimal(applied))),
                        com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind
                            .OWN,
                        null,
                        null),
                    user)
                .getResourceId());
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
                                obligation, version, new BigDecimal("4284.00"))),
                        com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind
                            .OWN,
                        null,
                        null),
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

  /** Runs in this test's tenant as another user, so the rows it writes are that user's (R6). */
  private <T> T asUser(UUID user, Supplier<T> work) {
    return TenantContext.executeInTenantContext(
        tenant,
        () -> {
          TenantContext.setCurrentUserId(user);
          return transactions.execute(status -> work.get());
        });
  }

  /**
   * doAnswer, not when(): re-stubbing with when() would run the earlier answer outside a tenant.
   */
  private void reach(UUID user, DataScope scope, UUID... principals) {
    doAnswer(
            call ->
                new CollectionAccessService.Reach(
                    TenantContext.requireTenantId(), scope, Set.of(principals)))
        .when(access)
        .reach(eq(user), anyString());
  }

  private boolean reads(UUID user, UUID cheque) {
    try {
      asUser(user, () -> queries.cheque(cheque, user));
      return true;
    } catch (com.fabricmanagement.common.infrastructure.web.exception.NotFoundException hidden) {
      return false;
    }
  }

  /**
   * PAYMENT-PLAN-9 decision 14, closeout C8(b). Once a cheque's last application is reversed it
   * follows R6 like any unlinked cheque: it is reached through its creator under the reader's
   * finance scope. Under OWN and DEPARTMENT the person who recorded it (and, under DEPARTMENT, that
   * person's department) keeps it; whoever reached someone else's cheque only through the
   * instalment loses it after the reversal, and that person's reversal still replays.
   */
  @Test
  void afterItsLastApplicationIsReversedAChequeIsReachedOnlyThroughItsCreator() {
    chequeSource("6000.00");
    for (var scope : List.of(DataScope.OWN, DataScope.DEPARTMENT)) {
      UUID recorder = UUID.randomUUID(), recordersColleague = UUID.randomUUID();
      UUID owner = UUID.randomUUID(), ownersColleague = UUID.randomUUID();
      system.executeInTransaction(
          sql ->
              sql.update(
                  "UPDATE finance.collection_obligation SET responsible_user_id=? WHERE id=?",
                  owner,
                  obligation));
      // The recorder may write the instalment when the cheque is received; scopes narrow after.
      reach(recorder, DataScope.ORGANIZATION, recorder);
      UUID cheque = receiveChequeAs(recorder, "6000.00", "2000.00");
      UUID application = applicationOf(cheque, "2000.00");
      boolean department = scope == DataScope.DEPARTMENT;
      if (department) {
        reach(recorder, scope, recorder, recordersColleague);
        reach(recordersColleague, scope, recorder, recordersColleague);
        reach(owner, scope, owner, ownersColleague);
        reach(ownersColleague, scope, owner, ownersColleague);
      } else {
        reach(recorder, scope, recorder);
        reach(owner, scope, owner);
      }
      // Before: the instalment's owner reaches the cheque through its only application.
      assertThat(reads(owner, cheque)).as("%s owner before", scope).isTrue();
      var body = reversal(cheque, UUID.randomUUID());
      var first = asUser(owner, () -> cheques.reverseApplication(cheque, application, body, owner));
      // After: only the creator's side keeps the cheque.
      assertThat(reads(owner, cheque)).as("%s owner after", scope).isFalse();
      assertThat(reads(recorder, cheque)).as("%s recorder after", scope).isTrue();
      if (department) {
        assertThat(reads(ownersColleague, cheque)).as("owner's colleague after").isFalse();
        assertThat(reads(recordersColleague, cheque)).as("recorder's colleague after").isTrue();
      }
      // The owner can no longer see the cheque, yet the same reversal replays to its own result.
      var replay =
          asUser(owner, () -> cheques.reverseApplication(cheque, application, body, owner));
      assertThat(replay.getId()).isEqualTo(first.getId());
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM finance.collection_cheque_event WHERE cheque_id=?"
                      + " AND action='REVERSE_APPLICATION'",
                  Long.class,
                  cheque))
          .isEqualTo(1L);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM finance.collection_cheque_application WHERE cheque_id=?"
                      + " AND reversed_at IS NULL",
                  Long.class,
                  cheque))
          .isZero();
    }
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

  // PAYMENT-PLAN-7 S2b: kind and drawer recorded on every received cheque.

  @Autowired
  com.fabricmanagement.platform.tradingpartner.app.TradingPartnerChequeDrawerService drawerList;

  private List<java.util.Map<String, Object>> chequeEventsOf(UUID cheque) {
    return jdbc.queryForList(
        "SELECT action, drawer_kind, drawer_name, drawer_id FROM finance.collection_cheque_event"
            + " WHERE cheque_id = ? ORDER BY evidence_revision",
        cheque);
  }

  private CollectionRequests.Transition correct(
      UUID cheque, LocalDate due, CollectionRequests.Correction override) {
    long chequeVersion =
        in(
            tenant,
            () -> instruments.findByTenantIdAndId(tenant, cheque).orElseThrow().getVersion());
    return new CollectionRequests.Transition(
        UUID.randomUUID(),
        chequeVersion,
        ChequeAction.CORRECT,
        date,
        now,
        "Customer sent a corrected copy",
        "Corrected instrument scan",
        null,
        override != null
            ? override
            : new CollectionRequests.Correction(
                new BigDecimal("4284.00"), date, due, "NORTHBANK-" + order),
        null);
  }

  @Test
  void anOwnChequeWithoutAListedDrawerRecordsTheCustomerByName() {
    chequeSource();
    UUID cheque = receiveCheque();
    var stored = in(tenant, () -> instruments.findByTenantIdAndId(tenant, cheque).orElseThrow());
    assertThat(stored.getDrawerKind())
        .isEqualTo(com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind.OWN);
    assertThat(stored.getDrawerName()).isEqualTo("Pennine Textiles");
    assertThat(stored.getDrawerId()).isNull();
    assertThat(chequeEventsOf(cheque))
        .singleElement()
        .satisfies(event -> assertThat(event.get("drawer_name")).isEqualTo("Pennine Textiles"));
  }

  @Test
  void aListedDrawerKeepsItsReceiptNameThroughRenameAndAnUnrelatedCorrection() {
    chequeSource();
    var added =
        in(
            tenant,
            () ->
                drawerList.add(
                    partner,
                    "Oliver Hartley",
                    com.fabricmanagement.platform.tradingpartner.domain.ChequeDrawerType
                        .INDIVIDUAL));
    long version = version();
    UUID cheque =
        in(
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
                            "Partner handed over a personal cheque",
                            List.of(
                                new CollectionRequests.Application(
                                    obligation, version, new BigDecimal("4284.00"))),
                            com.fabricmanagement.finance.collection.domain.CollectionTypes
                                .DrawerKind.OWN,
                            added.drawer().id(),
                            null),
                        actor)
                    .getResourceId());
    in(
        tenant,
        () -> {
          drawerList.change(
              partner,
              added.drawer().id(),
              "Oliver J Hartley",
              com.fabricmanagement.platform.tradingpartner.domain.ChequeDrawerType.INDIVIDUAL);
          drawerList.deactivate(partner, added.drawer().id());
          return null;
        });
    in(tenant, () -> cheques.transition(cheque, correct(cheque, date.plusMonths(4), null), actor));
    var stored = in(tenant, () -> instruments.findByTenantIdAndId(tenant, cheque).orElseThrow());
    assertThat(stored.getDrawerName()).isEqualTo("Oliver Hartley");
    assertThat(stored.getDrawerId()).isEqualTo(added.drawer().id());
    assertThat(stored.getDueOn()).isEqualTo(date.plusMonths(4));
  }

  @Test
  void aWrongTagIsCorrectedWithHistoryAndTheReceiptEventStaysAsItWas() {
    chequeSource();
    UUID cheque = receiveCheque();
    in(
        tenant,
        () ->
            cheques.transition(
                cheque,
                correct(
                    cheque,
                    date.plusMonths(3),
                    new CollectionRequests.Correction(
                        new BigDecimal("4284.00"),
                        date,
                        date.plusMonths(3),
                        "NORTHBANK-" + order,
                        com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind
                            .ENDORSED,
                        null,
                        "Calder Mills")),
                actor));
    var history = chequeEventsOf(cheque);
    assertThat(history).hasSize(2);
    assertThat(history.get(0).get("drawer_kind")).isEqualTo("OWN");
    assertThat(history.get(1).get("action")).isEqualTo("CORRECT");
    assertThat(history.get(1).get("drawer_kind")).isEqualTo("ENDORSED");
    assertThat(history.get(1).get("drawer_name")).isEqualTo("Calder Mills");
  }

  @Test
  void aClearedChequesDrawerCorrectionReplaysWithoutTouchingMoneyOrInstalmentVersion() {
    chequeSource("4000.00");
    UUID cheque = receiveUnlinked("6000.00");
    in(
        tenant,
        () -> cheques.allocate(cheque, allocation(cheque, UUID.randomUUID(), "4000.00"), actor));
    var clearing =
        in(
            tenant,
            () ->
                cheques.transition(
                    cheque,
                    transition(
                        UUID.randomUUID(), ChequeAction.CLEAR, chequeVersion(cheque), version()),
                    actor));
    var second = lowerToWithSecondInstalment("4000.00", "3000.00");
    long secondVersion =
        in(
            tenant,
            () -> obligations.findByTenantIdAndId(tenant, second).orElseThrow().getVersion());
    var later =
        in(
            tenant,
            () ->
                posting.match(
                    second, match(clearing.getPaymentId(), "1000.00", secondVersion), actor));
    lines("1000.00", "0.00");
    var before = in(tenant, () -> instruments.findByTenantIdAndId(tenant, cheque).orElseThrow());
    long obligationVersion = version();
    var request =
        new CollectionRequests.Transition(
            UUID.randomUUID(),
            before.getVersion(),
            ChequeAction.CORRECT_DRAWER,
            date,
            now,
            "Verified drawer identity",
            "Cheque image reviewed",
            null,
            null,
            null,
            new CollectionRequests.DrawerCorrection(
                com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind.ENDORSED,
                null,
                "Calder Mills"));

    var saved = in(tenant, () -> cheques.transition(cheque, request, actor));
    assertThat(in(tenant, () -> cheques.transition(cheque, request, actor)).getId())
        .isEqualTo(saved.getId());
    for (var key : List.of(request.operationId(), UUID.randomUUID())) {
      var changed =
          new CollectionRequests.Transition(
              key,
              request.expectedVersion(),
              request.action(),
              request.effectiveDate(),
              request.occurredAt(),
              request.reason(),
              request.evidence(),
              null,
              null,
              null,
              new CollectionRequests.DrawerCorrection(
                  com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind
                      .ENDORSED,
                  null,
                  "Pennine Weaving"));
      assertThatThrownBy(() -> in(tenant, () -> cheques.transition(cheque, changed, actor)))
          .hasFieldOrPropertyWithValue(
              "errorCode",
              key.equals(request.operationId())
                  ? "COLLECTION_OPERATION_REUSED"
                  : "COLLECTION_VERSION_CONFLICT");
    }
    var after = in(tenant, () -> instruments.findByTenantIdAndId(tenant, cheque).orElseThrow());
    assertThat(after.getVersion()).isEqualTo(before.getVersion() + 1);
    assertThat(after.getState()).isEqualTo(before.getState());
    assertThat(after.getClearedPaymentId()).isEqualTo(clearing.getPaymentId());
    assertThat(after.getDrawerName()).isEqualTo("Calder Mills");
    assertThat(version()).isEqualTo(obligationVersion);
    assertThat(in(tenant, () -> posting.settled(obligation))).isEqualByComparingTo("4000.00");
    assertThat(in(tenant, () -> posting.settled(second))).isEqualByComparingTo("1000.00");
    assertThat(
            in(
                tenant,
                () ->
                    matches.findByTenantIdAndPaymentIdAndReversedAtIsNull(
                        tenant, clearing.getPaymentId())))
        .extracting(m -> m.getId())
        .containsExactlyInAnyOrder(
            clearing.getMatchIds().getFirst(), later.getMatchIds().getFirst());
    lines("1000.00", "0.00");
    var history = chequeEventsOf(cheque);
    assertThat(history)
        .extracting(event -> event.get("action"))
        .containsExactly("RECEIVE", "APPLY", "CLEAR", "CORRECT_DRAWER");
    assertThat(history.getFirst().get("drawer_kind")).isEqualTo("OWN");
    assertThat(history.getLast().get("drawer_kind")).isEqualTo("ENDORSED");
  }

  @Test
  void anUnknownLegacyReceiptRemainsUnknownInHistoryAfterClassification() {
    UUID cheque = UUID.randomUUID();
    in(
        tenant,
        () -> {
          jdbc.update(
              """
              INSERT INTO finance.collection_cheque(
                id, tenant_id, partner_id, currency, amount, received_on, due_on, instrument_reference,
                state, created_at, updated_at, created_by)
              VALUES (?, ?, ?, 'GBP', 4284, ?, ?, 'NORTHBANK-LEGACY', 'RECEIVED', ?, ?, ?)
              """,
              cheque,
              tenant,
              partner,
              date,
              date.plusMonths(3),
              java.sql.Timestamp.from(now),
              java.sql.Timestamp.from(now),
              actor);
          jdbc.update(
              """
              INSERT INTO finance.collection_cheque_event(
                id, tenant_id, cheque_id, evidence_revision, action, state, effective_date, occurred_at,
                actor_id, reason, evidence, amount, received_on, due_on, instrument_reference,
                created_at, updated_at)
              VALUES (?, ?, ?, 0, 'RECEIVE', 'RECEIVED', ?, ?, ?, 'Cheque received', 'Signed cheque',
                4284, ?, ?, 'NORTHBANK-LEGACY', ?, ?)
              """,
              UUID.randomUUID(),
              tenant,
              cheque,
              date,
              java.sql.Timestamp.from(now),
              actor,
              date,
              date.plusMonths(3),
              java.sql.Timestamp.from(now),
              java.sql.Timestamp.from(now));
          return null;
        });

    var request =
        new CollectionRequests.Transition(
            UUID.randomUUID(),
            chequeVersion(cheque),
            ChequeAction.CORRECT_DRAWER,
            date,
            now,
            "Verified drawer identity",
            "Cheque image reviewed",
            null,
            null,
            null,
            new CollectionRequests.DrawerCorrection(
                com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind.ENDORSED,
                null,
                "Calder Mills"));
    in(tenant, () -> cheques.transition(cheque, request, actor));

    var current = in(tenant, () -> queries.cheque(cheque, actor));
    assertThat(current.drawerName()).isEqualTo("Calder Mills");
    var history = chequeEventsOf(cheque);
    assertThat(history).hasSize(2);
    assertThat(history.getFirst().get("drawer_kind")).isNull();
    assertThat(history.getLast().get("action")).isEqualTo("CORRECT_DRAWER");
    assertThat(history.getLast().get("drawer_kind")).isEqualTo("ENDORSED");
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void anEndorsedReceiptKeepsItsNameAndRejectsForeignDrawersRegardlessOfSplitTracking(
      boolean tracking) {
    system.executeInTransaction(
        sql ->
            sql.update(
                "UPDATE common_tenant.common_tenant SET settings = coalesce(settings, '{}'::jsonb)"
                    + " || jsonb_build_object('chequeSplitTracking', CAST(? AS boolean)) WHERE"
                    + " id=?",
                tracking,
                tenant));
    var endorsed = endorsedChequeFrom(partner);
    var saved = in(tenant, () -> cheques.receive(endorsed, actor));
    var stored =
        in(
            tenant,
            () -> instruments.findByTenantIdAndId(tenant, saved.getResourceId()).orElseThrow());
    assertThat(stored.getDrawerKind().name()).isEqualTo("ENDORSED");
    assertThat(stored.getDrawerName()).isEqualTo("Harrowgate Mills Ltd");
    assertThat(stored.getDrawerId()).isNull();
    var foreign =
        in(
            otherTenant,
            () ->
                drawerList.add(
                    otherPartner,
                    "Oliver Hartley",
                    com.fabricmanagement.platform.tradingpartner.domain.ChequeDrawerType
                        .INDIVIDUAL));
    var wrongDrawer =
        new CollectionRequests.ReceiveCheque(
            UUID.randomUUID(),
            partner,
            "GBP",
            endorsed.amount(),
            endorsed.receivedOn(),
            endorsed.dueOn(),
            "NORTHBANK-FOREIGN",
            endorsed.evidence(),
            endorsed.occurredAt(),
            endorsed.reason(),
            List.of(),
            com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind.OWN,
            foreign.drawer().id(),
            null);
    assertThatThrownBy(() -> in(tenant, () -> cheques.receive(wrongDrawer, actor)))
        .hasFieldOrPropertyWithValue("errorCode", "CHEQUE_DRAWER_INVALID");
    var missingKind =
        new CollectionRequests.ReceiveCheque(
            UUID.randomUUID(),
            partner,
            "GBP",
            endorsed.amount(),
            endorsed.receivedOn(),
            endorsed.dueOn(),
            "NORTHBANK-UNKNOWN",
            endorsed.evidence(),
            endorsed.occurredAt(),
            endorsed.reason(),
            List.of(),
            null,
            null,
            null);
    assertThatThrownBy(() -> in(tenant, () -> cheques.receive(missingKind, actor)))
        .hasFieldOrPropertyWithValue("errorCode", "CHEQUE_DRAWER_KIND_REQUIRED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM finance.collection_cheque WHERE tenant_id = ?",
                Integer.class,
                tenant))
        .isEqualTo(1);
  }

  private record LegacyCommand(String action, UUID target, Object body) {}

  private record LegacyChequeReceipt(
      UUID operationId,
      UUID partnerId,
      String currency,
      @com.fasterxml.jackson.annotation.JsonFormat(
              shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
          BigDecimal amount,
      LocalDate receivedOn,
      LocalDate dueOn,
      String instrumentReference,
      String evidence,
      Instant occurredAt,
      String reason,
      List<CollectionRequests.Application> applications) {}

  private record LegacyChequeCorrection(
      @com.fasterxml.jackson.annotation.JsonFormat(
              shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
          BigDecimal amount,
      LocalDate receivedOn,
      LocalDate dueOn,
      String instrumentReference) {}

  private record LegacyChequeTransition(
      UUID operationId,
      Long expectedVersion,
      ChequeAction action,
      LocalDate effectiveDate,
      Instant occurredAt,
      String reason,
      String evidence,
      UUID paymentId,
      LegacyChequeCorrection correction,
      List<CollectionRequests.Version> obligations) {}

  @Test
  void preTagReceiptAndOrdinaryCorrectionReplaysUseTheirPersistedCommandResults() {
    chequeSource();
    UUID cheque = receiveCheque();
    var legacyReceive =
        new CollectionRequests.ReceiveCheque(
            UUID.randomUUID(),
            partner,
            "GBP",
            new BigDecimal("4284.00"),
            date,
            date.plusMonths(3),
            "NORTHBANK-LEGACY",
            "Cheque image reviewed",
            now,
            "Cheque received",
            List.of(new CollectionRequests.Application(obligation, 0L, new BigDecimal("4284.00"))),
            null,
            null,
            null);
    var legacyCorrection = correct(cheque, date.plusMonths(4), null);
    var beforeTagging =
        new LegacyChequeReceipt(
            legacyReceive.operationId(),
            partner,
            legacyReceive.currency(),
            legacyReceive.amount(),
            legacyReceive.receivedOn(),
            legacyReceive.dueOn(),
            legacyReceive.instrumentReference(),
            legacyReceive.evidence(),
            legacyReceive.occurredAt(),
            legacyReceive.reason(),
            legacyReceive.applications());
    var correction = legacyCorrection.correction();
    var beforeCorrection =
        new LegacyChequeTransition(
            legacyCorrection.operationId(),
            legacyCorrection.expectedVersion(),
            ChequeAction.CORRECT,
            legacyCorrection.effectiveDate(),
            legacyCorrection.occurredAt(),
            legacyCorrection.reason(),
            legacyCorrection.evidence(),
            null,
            new LegacyChequeCorrection(
                correction.amount(),
                correction.receivedOn(),
                correction.dueOn(),
                correction.instrumentReference()),
            legacyCorrection.obligations());
    var receiveHash =
        com.fabricmanagement.common.infrastructure.serialization.CanonicalJsonFingerprint.of(
            new LegacyCommand("RECEIVE_CHEQUE", partner, beforeTagging));
    var correctionHash =
        com.fabricmanagement.common.infrastructure.serialization.CanonicalJsonFingerprint.of(
            new LegacyCommand("CHEQUE_TRANSITION", cheque, beforeCorrection));
    var receiveReceipt =
        in(
            tenant,
            () ->
                commands.record(
                    legacyReceive.operationId(),
                    actor,
                    receiveHash,
                    "CHEQUE",
                    cheque,
                    0L,
                    null,
                    List.of()));
    var correctionReceipt =
        in(
            tenant,
            () ->
                commands.record(
                    legacyCorrection.operationId(),
                    actor,
                    correctionHash,
                    "CHEQUE",
                    cheque,
                    1L,
                    null,
                    List.of()));

    assertThat(in(tenant, () -> cheques.receive(legacyReceive, actor)).getId())
        .isEqualTo(receiveReceipt.getId());
    assertThat(in(tenant, () -> cheques.transition(cheque, legacyCorrection, actor)).getId())
        .isEqualTo(correctionReceipt.getId());
    assertThat(chequeEventsOf(cheque)).hasSize(1);
  }

  /** B19: a cheque from before tagging keeps its unknown kind through an ordinary correction. */
  @Test
  void aChequeFromBeforeTaggingStaysUnknownThroughAnOrdinaryCorrection() {
    chequeSource();
    UUID cheque = receiveCheque();
    // As a row written before the drawer columns existed: nothing recorded.
    in(
        tenant,
        () ->
            jdbc.update(
                "UPDATE finance.collection_cheque SET drawer_kind = NULL, drawer_name = NULL,"
                    + " drawer_id = NULL WHERE id = ?",
                cheque));
    in(tenant, () -> cheques.transition(cheque, correct(cheque, date.plusMonths(4), null), actor));
    var stored = in(tenant, () -> instruments.findByTenantIdAndId(tenant, cheque).orElseThrow());
    assertThat(stored.getDrawerKind()).isNull();
    assertThat(stored.getDrawerName()).isNull();
    assertThat(stored.getDrawerId()).isNull();
    assertThat(stored.getDueOn()).isEqualTo(date.plusMonths(4));
    var history = chequeEventsOf(cheque);
    assertThat(history).hasSize(2);
    assertThat(history.get(1).get("action")).isEqualTo("CORRECT");
    assertThat(history.get(1).get("drawer_kind")).isNull();
  }

  @Test
  void aReceiptWithoutAKindIsRefusedAndNothingIsRecorded() {
    chequeSource();
    long version = version();
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        cheques.receive(
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
                                "No kind given",
                                List.of(
                                    new CollectionRequests.Application(
                                        obligation, version, new BigDecimal("4284.00"))),
                                null,
                                null,
                                null),
                            actor)))
        .isInstanceOfSatisfying(
            com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException.class,
            refused -> assertThat(refused.getErrorCode()).isEqualTo("CHEQUE_DRAWER_KIND_REQUIRED"));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM finance.collection_cheque WHERE tenant_id = ?",
                Integer.class,
                tenant))
        .isZero();
  }
}
