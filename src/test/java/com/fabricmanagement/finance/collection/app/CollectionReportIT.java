package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantBusinessCalendarPort;
import com.fabricmanagement.finance.collection.dto.CollectionReportDtos.Report;
import com.fabricmanagement.finance.collection.dto.CollectionReportDtos.Row;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Real PostgreSQL acceptance: immutable evidence, effective and knowledge cuts, scope before
 * totals.
 */
@Import(CollectionReportIT.FixedTime.class)
class CollectionReportIT extends AbstractIntegrationTest {
  @TestConfiguration
  static class FixedTime {
    @Bean
    @Primary
    Clock reportClock() {
      return Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired CollectionReportService reports;
  @Autowired SystemTransactionExecutor system;
  @MockitoBean CollectionAccessService access;
  @MockitoBean TradingPartnerResolver partners;
  @MockitoBean UserQueryService users;
  @MockitoBean TenantBusinessCalendarPort calendar;
  UUID tenant, actor, partner, order, plan, revision, obligation, logical, payment, registry;

  static Timestamp at(String value) {
    return Timestamp.from(Instant.parse(value));
  }

  @BeforeEach
  void seed() {
    tenant = UUID.randomUUID();
    actor = UUID.randomUUID();
    partner = UUID.randomUUID();
    order = UUID.randomUUID();
    plan = UUID.randomUUID();
    revision = UUID.randomUUID();
    obligation = UUID.randomUUID();
    logical = UUID.randomUUID();
    payment = UUID.randomUUID();
    registry = UUID.randomUUID();
    when(calendar.zone()).thenReturn(ZoneId.of("Europe/London"));
    when(access.reach(actor, "read"))
        .thenAnswer(
            i ->
                new CollectionAccessService.Reach(
                    TenantContext.requireTenantId(), DataScope.ORGANIZATION, Set.of(actor)));
    when(partners.resolveDisplayNames(any(), any()))
        .thenReturn(Map.of(partner, "Northbridge Cloth"));
    when(users.findRecordedActorNames(any(), any())).thenReturn(Map.of());
    system.executeInTransaction(
        sql -> {
          sql.update(
              "INSERT INTO common_tenant.common_tenant(id,uid,slug,name,status)"
                  + " VALUES(?,?,?,'Northbridge Cloth','ACTIVE')",
              tenant,
              tenant.toString(),
              tenant.toString());
          sql.update(
              "INSERT INTO common_company.trading_partner_registry(id,uid,official_name,country)"
                  + " VALUES(?,?,'Pennine Cloth','GBR')",
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
          obligation(
              sql,
              obligation,
              logical,
              order,
              plan,
              revision,
              "GBP",
              "BANK_TRANSFER",
              "2026-10-30",
              actor);
          source(
              sql,
              order,
              plan,
              revision,
              logical,
              false,
              "2026-10-20T12:00:00Z",
              "2026-10-20T12:00:00Z",
              1);
          sql.update(
              "INSERT INTO"
                  + " finance.finance_payment(id,tenant_id,uid,trading_partner_id,payment_number,direction,method,status,amount,currency,payment_date,created_at,updated_at)"
                  + " VALUES(?,?,?,?,?,'INBOUND','BANK_TRANSFER','RECEIVED',1000,'GBP','2026-10-30','2026-11-02T12:00Z','2026-11-02T12:00Z')",
              payment,
              tenant,
              payment.toString(),
              partner,
              "S6-" + payment);
          return null;
        });
  }

  private void obligation(
      JdbcTemplate sql,
      UUID id,
      UUID row,
      UUID orderId,
      UUID planId,
      UUID rev,
      String currency,
      String method,
      String due,
      UUID owner) {
    sql.update(
        "INSERT INTO"
            + " finance.collection_obligation(id,tenant_id,uid,order_id,plan_id,logical_id,partner_id,currency,revision_id,amount,kind,method,expected_date,responsible_user_id,created_at,updated_at)"
            + " VALUES(?,?,?,?,?,?,?,?,?,4284,'INSTALMENT',?,CAST(? AS"
            + " date),?,'2026-10-20T12:00Z','2026-10-20T12:00Z')",
        id,
        tenant,
        id.toString(),
        orderId,
        planId,
        row,
        partner,
        currency,
        rev,
        method,
        due,
        owner);
    sql.update(
        "INSERT INTO"
            + " finance.collection_expectation(id,tenant_id,obligation_id,revision_id,approval_id,agreed_at,activated_at,amount,kind,method,expected_date,created_at,updated_at)"
            + " VALUES(?,?,?,?,?,'2026-10-20T12:00Z','2026-10-20T12:00Z',4284,'INSTALMENT',?,CAST(?"
            + " AS date),'2026-10-20T12:00Z','2026-10-20T12:00Z')",
        UUID.randomUUID(),
        tenant,
        id,
        rev,
        UUID.randomUUID(),
        method,
        due);
  }

  private void source(
      JdbcTemplate sql,
      UUID orderId,
      UUID planId,
      UUID rev,
      UUID row,
      boolean cancelled,
      String effective,
      String recorded,
      int version) {
    sql.update(
        "INSERT INTO"
            + " finance.collection_source_observation(id,tenant_id,order_id,plan_id,revision_id,source_version,cancelled,stale,source_missing,effective_at,observed_at,active_logical_ids,created_at,updated_at)"
            + " VALUES(?,?,?,?,?,?,?,false,false,?,?,CAST(? AS jsonb),?,?)",
        UUID.randomUUID(),
        tenant,
        orderId,
        planId,
        rev,
        version,
        cancelled,
        at(effective),
        at(recorded),
        cancelled ? "[]" : "[\"" + row + "\"]",
        at(recorded),
        at(recorded));
  }

  private UUID match() {
    return system.executeInTransaction(
        sql -> {
          UUID id = UUID.randomUUID();
          sql.update(
              "INSERT INTO"
                  + " finance.collection_payment_match(id,tenant_id,obligation_id,payment_id,amount,effective_date,occurred_at,actor_id,reason,created_at,updated_at)"
                  + " VALUES(?,?,?,?,1000,'2026-10-30','2026-10-30T12:00Z',?,'Bank credit"
                  + " verified','2026-11-02T12:00Z','2026-11-02T12:00Z')",
              id,
              tenant,
              obligation,
              payment,
              actor);
          return id;
        });
  }

  private Report report(String date, String cutoff, String bucket, int page, int size) {
    return TenantContext.executeInTenantContext(
        tenant,
        () -> {
          TenantContext.setCurrentUserId(actor);
          return reports.report(
              actor,
              new CollectionReportService.Filter(
                  LocalDate.parse(date),
                  Instant.parse(cutoff),
                  null,
                  null,
                  null,
                  null,
                  bucket,
                  null,
                  null,
                  page,
                  size));
        });
  }

  private Row row(Report result) {
    return result.content().stream()
        .filter(r -> r.id().equals(obligation))
        .findFirst()
        .orElseThrow();
  }

  private void money(String actual, String expected) {
    assertThat(new BigDecimal(actual)).isEqualByComparingTo(expected);
  }

  @Test
  void lateReceiptChangesRestatedPastButNotWhatWasKnownBeforeRecording() {
    match();
    var before = report("2026-10-30", "2026-11-01T12:00:00Z", null, 0, 20);
    money(row(before).outstandingAmount(), "4284");
    var after = report("2026-10-30", "2026-11-03T12:00:00Z", null, 0, 20);
    money(row(after).recordedCashAmount(), "1000");
    money(row(after).outstandingAmount(), "3284");
    money(
        row(report("2026-10-29", "2026-11-03T12:00:00Z", null, 0, 20)).outstandingAmount(), "4284");
  }

  @Test
  void reversalRequiresBothEffectiveAndRecordedCuts() {
    UUID match = match();
    system.executeInTransaction(
        sql -> {
          sql.update(
              "UPDATE finance.collection_payment_match SET"
                  + " reversed_on='2026-11-04',reversal_occurred_at='2026-11-04T12:00Z',reversed_at='2026-11-05T12:00Z',reversed_by=?,reversal_reason='Wrong"
                  + " receipt attribution' WHERE id=?",
              actor,
              match);
          return null;
        });
    money(
        row(report("2026-11-03", "2026-11-06T12:00:00Z", null, 0, 20)).outstandingAmount(), "3284");
    money(
        row(report("2026-11-04", "2026-11-04T13:00:00Z", null, 0, 20)).outstandingAmount(), "3284");
    money(
        row(report("2026-11-04", "2026-11-06T12:00:00Z", null, 0, 20)).outstandingAmount(), "4284");
  }

  @Test
  void observedLegacyVoidHasUnknownEffectiveDateAndNeverSilentlyRewritesEarlierKnowledge() {
    UUID match = match();
    system.executeInTransaction(
        sql -> {
          sql.update(
              "UPDATE finance.collection_payment_match SET"
                  + " payment_invalidated_at='2026-11-04T12:00Z',invalidation_recorded_at='2026-11-05T12:00Z'"
                  + " WHERE id=?",
              match);
          sql.update("UPDATE finance.finance_payment SET status='VOIDED' WHERE id=?", payment);
          return null;
        });
    money(
        row(report("2026-10-30", "2026-11-03T12:00:00Z", null, 0, 20)).outstandingAmount(), "3284");
    var uncertain = report("2026-10-30", "2026-11-06T12:00:00Z", null, 0, 20);
    assertThat(row(uncertain).outstandingAmount()).isNull();
    assertThat(uncertain.currencies().getFirst().outstandingAmount()).isNull();
    assertThat(uncertain.currencies().getFirst().unknownBalanceRows()).isEqualTo(1);
  }

  @Test
  void cancellationRetainsCashAndShowsReviewWithoutRefunding() {
    match();
    system.executeInTransaction(
        sql -> {
          source(
              sql,
              order,
              plan,
              revision,
              logical,
              true,
              "2026-11-03T12:00:00Z",
              "2026-11-04T12:00:00Z",
              2);
          return null;
        });
    var cancelled = row(report("2026-11-03", "2026-11-05T12:00:00Z", null, 0, 20));
    money(cancelled.plannedAmount(), "0");
    money(cancelled.recordedCashAmount(), "1000");
    assertThat(cancelled.buckets()).contains("CANCELLED_REVIEW");
    assertThat(row(report("2026-11-02", "2026-11-05T12:00:00Z", null, 0, 20)).cancelled())
        .isFalse();
  }

  @Test
  void aLaterReductionBelowRecordedCashIsFlaggedInsteadOfJustLookingSettled() {
    match();
    system.executeInTransaction(
        sql -> {
          UUID reduced = UUID.randomUUID();
          sql.update(
              "INSERT INTO"
                  + " finance.collection_expectation(id,tenant_id,obligation_id,revision_id,approval_id,agreed_at,activated_at,amount,kind,method,expected_date,created_at,updated_at)"
                  + " VALUES(?,?,?,?,?,'2026-10-31T12:00Z','2026-10-31T12:00Z',800,'INSTALMENT','BANK_TRANSFER',CAST('2026-10-30'"
                  + " AS date),'2026-10-31T12:00Z','2026-10-31T12:00Z')",
              UUID.randomUUID(),
              tenant,
              obligation,
              reduced,
              UUID.randomUUID());
          source(
              sql,
              order,
              plan,
              reduced,
              logical,
              false,
              "2026-10-31T12:00:00Z",
              "2026-10-31T12:00:00Z",
              2);
          return null;
        });
    var after = row(report("2026-11-03", "2026-11-04T12:00:00Z", null, 0, 20));
    money(after.plannedAmount(), "800");
    money(after.recordedCashAmount(), "1000");
    money(after.outstandingAmount(), "0");
    assertThat(after.buckets()).contains("OVER_SETTLED_REVIEW", "SETTLED");
  }

  @Test
  void aRefundIsOwedFromWhenItWasRecordedUntilTheCutKnowsItWasPaidOrCancelled() {
    match();
    UUID refund = UUID.randomUUID(), outbound = UUID.randomUUID();
    system.executeInTransaction(
        sql -> {
          sql.update(
              "INSERT INTO"
                  + " finance.finance_payment(id,tenant_id,uid,trading_partner_id,payment_number,direction,method,status,amount,currency,payment_date,created_at,updated_at)"
                  + " VALUES(?,?,?,?,?,'OUTBOUND','BANK_TRANSFER','RECEIVED',200,'GBP','2026-11-06','2026-11-06T12:00Z','2026-11-06T12:00Z')",
              outbound,
              tenant,
              outbound.toString(),
              partner,
              "S6-OUT-" + outbound);
          sql.update(
              "INSERT INTO"
                  + " finance.collection_refund(id,tenant_id,created_at,updated_at,obligation_id,payment_id,partner_id,currency,amount,effective_date,occurred_at,actor_id,reason)"
                  + " VALUES(?,?,'2026-11-04T12:00Z','2026-11-04T12:00Z',?,?,?,'GBP',200,'2026-11-03','2026-11-03T12:00Z',?,'Buyer"
                  + " overpaid')",
              refund,
              tenant,
              obligation,
              payment,
              partner,
              actor);
          sql.update(
              "INSERT INTO"
                  + " finance.collection_refund_event(id,tenant_id,created_at,updated_at,refund_id,step,action,effective_date,occurred_at,actor_id,reason,payment_id)"
                  + " VALUES(?,?,'2026-11-07T12:00Z','2026-11-07T12:00Z',?,1,'SETTLE','2026-11-06','2026-11-06T12:00Z',?,'Refund"
                  + " sent',?)",
              UUID.randomUUID(),
              tenant,
              refund,
              actor,
              outbound);
          sql.update(
              "INSERT INTO"
                  + " finance.collection_refund_event(id,tenant_id,created_at,updated_at,refund_id,step,action,effective_date,occurred_at,actor_id,reason,payment_id)"
                  + " VALUES(?,?,'2026-11-08T12:00Z','2026-11-08T12:00Z',?,2,'REOPEN','2026-11-08','2026-11-08T12:00Z',?,'Settling"
                  + " payment reversed',?)",
              UUID.randomUUID(),
              tenant,
              refund,
              actor,
              outbound);
          return null;
        });

    var unknown = report("2026-11-02", "2026-11-03T12:00:00Z", null, 0, 20);
    money(row(unknown).refundPendingAmount(), "0");
    assertThat(row(unknown).buckets()).doesNotContain("REFUND_PENDING");

    var owed = report("2026-11-04", "2026-11-05T12:00:00Z", "REFUND_PENDING", 0, 20);
    money(row(owed).refundPendingAmount(), "200");
    money(owed.currencies().getFirst().refundPendingAmount(), "200");

    var paid = report("2026-11-07", "2026-11-07T18:00:00Z", null, 0, 20);
    money(row(paid).refundPendingAmount(), "0");
    assertThat(row(paid).buckets()).doesNotContain("REFUND_PENDING");

    // Paid on 6 November but only recorded on the 7th: what was known on the 6th still owed it.
    var beforeRecorded = report("2026-11-07", "2026-11-06T18:00:00Z", null, 0, 20);
    money(row(beforeRecorded).refundPendingAmount(), "200");

    var reopened = report("2026-11-09", "2026-11-09T12:00:00Z", "REFUND_PENDING", 0, 20);
    money(row(reopened).refundPendingAmount(), "200");
    assertThat(row(reopened).buckets()).contains("REFUND_PENDING");
  }

  @Test
  void chequeDeliverySatisfiesInstrumentOnlyAndOverdueClearingIsSeparate() {
    UUID cheque = UUID.randomUUID();
    system.executeInTransaction(
        sql -> {
          sql.update(
              "UPDATE finance.collection_obligation SET method='CHEQUE' WHERE id=?", obligation);
          // Append a newly agreed revision rather than modifying the immutable original
          // expectation.
          UUID next = UUID.randomUUID();
          sql.update(
              "INSERT INTO"
                  + " finance.collection_expectation(id,tenant_id,obligation_id,revision_id,approval_id,agreed_at,activated_at,amount,kind,method,expected_date,cheque_payment_date,created_at,updated_at)"
                  + " VALUES(?,?,?,?,?,'2026-10-21T12:00Z','2026-10-21T12:00Z',4284,'INSTALMENT','CHEQUE','2026-10-30','2026-11-02','2026-10-21T12:00Z','2026-10-21T12:00Z')",
              UUID.randomUUID(),
              tenant,
              obligation,
              next,
              UUID.randomUUID());
          source(
              sql,
              order,
              plan,
              next,
              logical,
              false,
              "2026-10-21T12:00:00Z",
              "2026-10-21T12:00:00Z",
              2);
          sql.update(
              "INSERT INTO"
                  + " finance.collection_cheque(id,tenant_id,partner_id,currency,amount,received_on,due_on,instrument_reference,state,created_at,updated_at)"
                  + " VALUES(?,?,?,'GBP',4284,'2026-10-30','2026-11-02','NORTHBANK-42','RECEIVED','2026-10-30T12:00Z','2026-10-30T12:00Z')",
              cheque,
              tenant,
              partner);
          sql.update(
              "INSERT INTO"
                  + " finance.collection_cheque_application(id,tenant_id,cheque_id,obligation_id,amount,effective_date,occurred_at,actor_id,reason,created_at,updated_at)"
                  + " VALUES(?,?,?,?,4284,'2026-10-30','2026-10-30T12:00Z',?,'Cheque"
                  + " received','2026-10-30T12:00Z','2026-10-30T12:00Z')",
              UUID.randomUUID(),
              tenant,
              cheque,
              obligation,
              actor);
          sql.update(
              "INSERT INTO"
                  + " finance.collection_cheque_event(id,tenant_id,cheque_id,evidence_revision,action,state,effective_date,occurred_at,actor_id,reason,evidence,amount,received_on,due_on,instrument_reference,created_at,updated_at)"
                  + " VALUES(?,?,?,0,'RECEIVE','RECEIVED','2026-10-30','2026-10-30T12:00Z',?,'Cheque"
                  + " received','Physical instrument"
                  + " verified',4284,'2026-10-30','2026-11-02','NORTHBANK-42','2026-10-30T12:00Z','2026-10-30T12:00Z')",
              UUID.randomUUID(),
              tenant,
              cheque,
              actor);
          return null;
        });
    var delivered = row(report("2026-11-03", "2026-11-04T12:00:00Z", null, 0, 20));
    money(delivered.receivedChequeAmount(), "4284");
    money(delivered.recordedCashAmount(), "0");
    money(delivered.outstandingAmount(), "4284");
    money(delivered.missingInstrumentAmount(), "0");
    assertThat(delivered.buckets())
        .contains("CHEQUE_CASH_OVERDUE", "CHEQUE_AWAITING_CLEARING")
        .doesNotContain("CHEQUE_DELIVERY_OVERDUE", "CASH_OVERDUE");
    // Delivered in full, not yet due, agreed delivery day passed: no bucket claimed this before.
    var notYetDue = row(report("2026-10-31", "2026-11-04T12:00:00Z", null, 0, 20));
    assertThat(notYetDue.buckets())
        .contains("CHEQUE_AWAITING_CLEARING")
        .doesNotContain(
            "CHEQUE_CASH_OVERDUE",
            "CHEQUE_DELIVERY_OVERDUE",
            "CASH_OVERDUE",
            "DUE_TODAY",
            "FUTURE");
  }

  @Test
  void dueBucketsUseAgreedDateAndTotalsAreFilteredBeforePageAndCurrencyNeverCombined() {
    UUID other = UUID.randomUUID(),
        otherOrder = UUID.randomUUID(),
        otherPlan = UUID.randomUUID(),
        otherRev = UUID.randomUUID(),
        otherLogical = UUID.randomUUID();
    system.executeInTransaction(
        sql -> {
          obligation(
              sql,
              other,
              otherLogical,
              otherOrder,
              otherPlan,
              otherRev,
              "EUR",
              "BANK_TRANSFER",
              "2026-10-30",
              actor);
          source(
              sql,
              otherOrder,
              otherPlan,
              otherRev,
              otherLogical,
              false,
              "2026-10-20T12:00:00Z",
              "2026-10-20T12:00:00Z",
              1);
          return null;
        });
    var report = report("2026-10-30", "2026-11-01T12:00:00Z", "DUE_TODAY", 0, 1);
    assertThat(report.content()).hasSize(1);
    assertThat(report.totalElements()).isEqualTo(2);
    assertThat(report.currencies()).extracting(c -> c.currency()).containsExactly("EUR", "GBP");
    assertThat(report("2026-10-30", "2026-11-01T12:00:00Z", "CASH_OVERDUE", 0, 20).totalElements())
        .isZero();
    assertThat(report("2026-10-31", "2026-11-01T12:00:00Z", "CASH_OVERDUE", 0, 20).totalElements())
        .isEqualTo(2);
  }

  @Test
  void currentOwnScopeHidesOtherOwnersBeforeTotalsAndHistoricalCutDoesNotRestoreRevokedScope() {
    // doReturn: when(access.reach(..)) would run the seed's answer, which needs a bound tenant.
    doReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(UUID.randomUUID())))
        .when(access)
        .reach(actor, "read");
    assertThat(report("2026-10-30", "2026-11-01T12:00:00Z", null, 0, 20).totalElements()).isZero();
    doReturn(new CollectionAccessService.Reach(tenant, null, Set.of(actor)))
        .when(access)
        .reach(actor, "read");
    assertThatThrownBy(() -> report("2026-10-30", "2026-11-01T12:00:00Z", null, 0, 20))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void missingSourceHealthPreservesKnownAgreementAndReportsUnknownFreshness() {
    system.executeInTransaction(
        sql -> {
          sql.update(
              "INSERT INTO"
                  + " finance.collection_source_observation(id,tenant_id,order_id,plan_id,revision_id,source_version,cancelled,stale,source_missing,effective_at,observed_at,active_logical_ids,created_at,updated_at)"
                  + " VALUES(?,?,?,?,?,1,false,true,true,NULL,'2026-11-03T12:00Z','[]','2026-11-03T12:00Z','2026-11-03T12:00Z')",
              UUID.randomUUID(),
              tenant,
              order,
              plan,
              revision);
          return null;
        });
    var result = report("2026-10-30", "2026-11-04T12:00:00Z", null, 0, 20);
    money(row(result).outstandingAmount(), "4284");
    assertThat(row(result).warnings()).contains("SOURCE_STALE", "SOURCE_MISSING");
    assertThat(result.oldestSourceCheckedAt()).isNull();
  }

  @Test
  void aTenantWithNoKnownEvidenceHasUnknownCoverageAndCannotSeeAnotherTenantsTotals() {
    UUID otherTenant = UUID.randomUUID();
    var result =
        TenantContext.executeInTenantContext(
            otherTenant,
            () ->
                reports.report(
                    actor,
                    new CollectionReportService.Filter(
                        LocalDate.parse("2026-10-30"),
                        Instant.parse("2026-11-01T12:00:00Z"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        20)));
    assertThat(result.totalElements()).isZero();
    assertThat(result.currencies()).isEmpty();
    assertThat(result.legacyOrderCount()).isNull();
    assertThat(result.oldestSourceCheckedAt()).isNull();
    assertThat(result.warnings()).contains("LEGACY_AND_UNPROJECTED_COVERAGE_UNKNOWN");
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
    system.executeInTransaction(
        sql -> {
          sql.queryForObject(
              "SELECT set_config('app.collection_purge_tenant',?,true)",
              String.class,
              tenant.toString());
          for (String table :
              List.of(
                  "collection_source_observation",
                  "collection_cheque_event",
                  "collection_refund_event",
                  "collection_refund",
                  "collection_payment_match",
                  "collection_cheque_application",
                  "collection_cheque",
                  "collection_expectation",
                  "collection_obligation",
                  "finance_payment"))
            sql.update("DELETE FROM finance." + table + " WHERE tenant_id=?", tenant);
          return null;
        });
  }

  private UUID chequeEvidence() {
    UUID cheque = UUID.randomUUID();
    system.executeInTransaction(
        sql -> {
          sql.update(
              "UPDATE finance.collection_obligation SET method='CHEQUE' WHERE id=?", obligation);
          // Append a newly agreed revision rather than modifying the immutable original
          // expectation.
          UUID next = UUID.randomUUID();
          sql.update(
              "INSERT INTO"
                  + " finance.collection_expectation(id,tenant_id,obligation_id,revision_id,approval_id,agreed_at,activated_at,amount,kind,method,expected_date,cheque_payment_date,created_at,updated_at)"
                  + " VALUES(?,?,?,?,?,'2026-10-21T12:00Z','2026-10-21T12:00Z',4284,'INSTALMENT','CHEQUE','2026-10-30','2026-11-02','2026-10-21T12:00Z','2026-10-21T12:00Z')",
              UUID.randomUUID(),
              tenant,
              obligation,
              next,
              UUID.randomUUID());
          source(
              sql,
              order,
              plan,
              next,
              logical,
              false,
              "2026-10-21T12:00:00Z",
              "2026-10-21T12:00:00Z",
              2);
          sql.update(
              "INSERT INTO"
                  + " finance.collection_cheque(id,tenant_id,partner_id,currency,amount,received_on,due_on,instrument_reference,state,created_at,updated_at)"
                  + " VALUES(?,?,?,'GBP',4284,'2026-10-30','2026-11-02','NORTHBANK-42','RECEIVED','2026-10-30T12:00Z','2026-10-30T12:00Z')",
              cheque,
              tenant,
              partner);
          sql.update(
              "INSERT INTO"
                  + " finance.collection_cheque_application(id,tenant_id,cheque_id,obligation_id,amount,effective_date,occurred_at,actor_id,reason,created_at,updated_at)"
                  + " VALUES(?,?,?,?,4284,'2026-10-30','2026-10-30T12:00Z',?,'Cheque"
                  + " received','2026-10-30T12:00Z','2026-10-30T12:00Z')",
              UUID.randomUUID(),
              tenant,
              cheque,
              obligation,
              actor);
          sql.update(
              "INSERT INTO"
                  + " finance.collection_cheque_event(id,tenant_id,cheque_id,evidence_revision,action,state,effective_date,occurred_at,actor_id,reason,evidence,amount,received_on,due_on,instrument_reference,created_at,updated_at)"
                  + " VALUES(?,?,?,0,'RECEIVE','RECEIVED','2026-10-30','2026-10-30T12:00Z',?,'Cheque"
                  + " received','Physical instrument"
                  + " verified',4284,'2026-10-30','2026-11-02','NORTHBANK-42','2026-10-30T12:00Z','2026-10-30T12:00Z')",
              UUID.randomUUID(),
              tenant,
              cheque,
              actor);
          return null;
        });
    return cheque;
  }

  // ---- PAYMENT-PLAN-9 S1: applications cut by effective date and recording time ----------------
  // The ticket's dates (5, 7, 9, 11 and 12 Oct) are moved to 23, 25, 27, 29 and 30 Oct, keeping
  // their order and the exact 10:00 UTC boundaries: this fixture's agreement starts on 20-21 Oct.

  /** A cheque obligation, and a cheque held since 22 Oct with no application yet. */
  private UUID heldCheque() {
    return heldCheque("2026-11-30");
  }

  private UUID heldCheque(String due) {
    UUID cheque = UUID.randomUUID();
    system.executeInTransaction(
        sql -> {
          sql.update(
              "UPDATE finance.collection_obligation SET method='CHEQUE' WHERE id=?", obligation);
          UUID next = UUID.randomUUID();
          sql.update(
              "INSERT INTO"
                  + " finance.collection_expectation(id,tenant_id,obligation_id,revision_id,approval_id,agreed_at,activated_at,amount,kind,method,expected_date,cheque_payment_date,created_at,updated_at)"
                  + " VALUES(?,?,?,?,?,'2026-10-21T12:00Z','2026-10-21T12:00Z',4284,'INSTALMENT','CHEQUE','2026-10-30','2026-11-30','2026-10-21T12:00Z','2026-10-21T12:00Z')",
              UUID.randomUUID(),
              tenant,
              obligation,
              next,
              UUID.randomUUID());
          source(
              sql,
              order,
              plan,
              next,
              logical,
              false,
              "2026-10-21T12:00:00Z",
              "2026-10-21T12:00:00Z",
              2);
          sql.update(
              "INSERT INTO"
                  + " finance.collection_cheque(id,tenant_id,partner_id,currency,amount,received_on,due_on,instrument_reference,state,created_at,updated_at)"
                  + " VALUES(?,?,?,'GBP',4284,'2026-10-22',CAST(? AS"
                  + " date),'NORTHBANK-77','RECEIVED','2026-10-22T12:00Z','2026-10-22T12:00Z')",
              cheque,
              tenant,
              partner,
              due);
          sql.update(
              "INSERT INTO"
                  + " finance.collection_cheque_event(id,tenant_id,cheque_id,evidence_revision,action,state,effective_date,occurred_at,actor_id,reason,evidence,amount,received_on,due_on,instrument_reference,created_at,updated_at)"
                  + " VALUES(?,?,?,0,'RECEIVE','RECEIVED','2026-10-22','2026-10-22T12:00Z',?,'Cheque"
                  + " received','Physical instrument verified',4284,'2026-10-22',CAST(? AS"
                  + " date),'NORTHBANK-77','2026-10-22T12:00Z','2026-10-22T12:00Z')",
              UUID.randomUUID(),
              tenant,
              cheque,
              actor,
              due);
          return null;
        });
    return cheque;
  }

  private void applied(UUID cheque, UUID id, String amount, String effective, String recorded) {
    system.executeInTransaction(
        sql ->
            sql.update(
                "INSERT INTO"
                    + " finance.collection_cheque_application(id,tenant_id,cheque_id,obligation_id,amount,effective_date,occurred_at,actor_id,reason,created_at,updated_at)"
                    + " VALUES(?,?,?,?,CAST(? AS numeric),CAST(? AS date),?,?,'Applied to the"
                    + " instalment',?,?)",
                id,
                tenant,
                cheque,
                obligation,
                amount,
                effective,
                at(recorded),
                actor,
                at(recorded),
                at(recorded)));
  }

  private void reversed(UUID id, String effective, String recorded) {
    system.executeInTransaction(
        sql ->
            sql.update(
                "UPDATE finance.collection_cheque_application SET reversed_on=CAST(? AS date),"
                    + "reversed_at=?,reversal_occurred_at=?,reversed_by=?,"
                    + "reversal_reason='Applied to the wrong instalment' WHERE id=?",
                effective,
                at(recorded),
                at(recorded),
                actor,
                id));
  }

  private Row cut(String asOf, String cutoff) {
    return row(report(asOf, cutoff, null, 0, 20));
  }

  /** Received cheque coverage at the cut; no money is collected while the cheque is only held. */
  private void held(String asOf, String cutoff, String received) {
    var value = cut(asOf, cutoff);
    money(value.receivedChequeAmount(), received);
    money(value.recordedCashAmount(), "0");
    money(value.outstandingAmount(), "4284");
  }

  @Test
  void anApplicationCountsOnceEffectiveAndRecordedAndItsReversalOnceBothItsCutsAreReached() {
    UUID cheque = heldCheque(), application = UUID.randomUUID();
    applied(cheque, application, "2000", "2026-10-23", "2026-10-25T10:00:00Z");
    reversed(application, "2026-10-27", "2026-10-29T10:00:00Z");
    held("2026-10-22", "2026-11-10T00:00:00Z", "0"); // not yet effective
    held("2026-10-24", "2026-10-25T09:59:59Z", "0"); // effective, not yet recorded
    held("2026-10-24", "2026-10-25T10:00:00Z", "2000"); // both reached, exact boundary
    held("2026-10-26", "2026-11-10T00:00:00Z", "2000"); // reversal recorded but not yet effective
    held("2026-10-28", "2026-10-29T09:59:59Z", "2000"); // reversal effective, not yet recorded
    held("2026-10-27", "2026-10-29T10:00:00Z", "0"); // both reversal cuts reached exactly
    held("2026-11-03", "2026-11-10T00:00:00Z", "0");
  }

  @Test
  void dueFlagsComeOnlyFromApplicationsVisibleAtTheCut() {
    UUID cheque = heldCheque("2026-10-24"), application = UUID.randomUUID();
    applied(cheque, application, "4284", "2026-10-22", "2026-10-22T12:00:00Z");
    reversed(application, "2026-10-27", "2026-10-27T12:00:00Z");
    assertThat(cut("2026-10-24", "2026-11-10T00:00:00Z").buckets()).contains("DUE_TODAY");
    assertThat(cut("2026-10-26", "2026-11-10T00:00:00Z").buckets()).contains("CHEQUE_CASH_OVERDUE");
    // Once its only application is reversed, the held cheque says nothing about the instalment.
    assertThat(cut("2026-10-28", "2026-11-10T00:00:00Z").buckets())
        .doesNotContain("CHEQUE_CASH_OVERDUE", "DUE_TODAY", "CHEQUE_AWAITING_CLEARING");
  }

  /**
   * PAYMENT-PLAN-9 S3 (F2): an APPLY event carries the cheque's state as it stands, so the cheque's
   * state at a cut after it is the state before it.
   */
  @Test
  void anApplyEventDoesNotMoveTheChequesStateAtACut() {
    UUID cheque = heldCheque(), application = UUID.randomUUID();
    system.executeInTransaction(
        sql ->
            sql.update(
                "INSERT INTO"
                    + " finance.collection_cheque_event(id,tenant_id,cheque_id,evidence_revision,action,state,effective_date,occurred_at,actor_id,reason,evidence,amount,received_on,due_on,instrument_reference,created_at,updated_at)"
                    + " VALUES(?,?,?,1,'PRESENT','PRESENTED','2026-10-24','2026-10-24T12:00Z',?,'Presented','Bank"
                    + " slip',4284,'2026-10-22','2026-11-30','NORTHBANK-77','2026-10-24T12:00Z','2026-10-24T12:00Z')",
                UUID.randomUUID(),
                tenant,
                cheque,
                actor));
    applied(cheque, application, "2000", "2026-10-25", "2026-10-25T12:00:00Z");
    system.executeInTransaction(
        sql ->
            sql.update(
                "INSERT INTO"
                    + " finance.collection_cheque_event(id,tenant_id,cheque_id,evidence_revision,action,state,effective_date,occurred_at,actor_id,reason,evidence,amount,received_on,due_on,instrument_reference,application_id,created_at,updated_at)"
                    + " VALUES(?,?,?,2,'APPLY','PRESENTED','2026-10-25','2026-10-25T12:00Z',?,'Applied','Buyer"
                    + " instruction',4284,'2026-10-22','2026-11-30','NORTHBANK-77',?,'2026-10-25T12:00Z','2026-10-25T12:00Z')",
                UUID.randomUUID(),
                tenant,
                cheque,
                actor,
                application));
    held("2026-10-24", "2026-11-10T00:00:00Z", "0");
    // Still presented, so the application counts as held coverage.
    held("2026-10-26", "2026-11-10T00:00:00Z", "2000");
  }

  @Test
  void aDrawerCorrectionPreservesCoverageAcrossReportCuts() {
    UUID cheque = heldCheque();
    applied(cheque, UUID.randomUUID(), "2000", "2026-10-22", "2026-10-22T12:00:00Z");
    system.executeInTransaction(
        sql ->
            sql.update(
                """
                INSERT INTO finance.collection_cheque_event(
                  id, tenant_id, cheque_id, evidence_revision, action, state, effective_date, occurred_at,
                  actor_id, reason, evidence, amount, received_on, due_on, instrument_reference,
                  drawer_kind, drawer_name, created_at, updated_at)
                VALUES (?, ?, ?, 1, 'CORRECT_DRAWER', 'RECEIVED', '2026-10-24', '2026-10-24T12:00Z',
                  ?, 'Verified drawer', 'Cheque image reviewed', 4284, '2026-10-22', '2026-11-30',
                  'NORTHBANK-77', 'ENDORSED', 'Calder Mills', '2026-10-24T12:00Z', '2026-10-24T12:00Z')
                """,
                UUID.randomUUID(),
                tenant,
                cheque,
                actor));
    held("2026-10-23", "2026-11-10T00:00:00Z", "2000");
    held("2026-10-25", "2026-11-10T00:00:00Z", "2000");
  }

  @Test
  void aReplacementOnTheSameInstalmentCountsOnlyFromItsOwnCut() {
    UUID cheque = heldCheque(), original = UUID.randomUUID();
    applied(cheque, original, "2000", "2026-10-23", "2026-10-25T10:00:00Z");
    reversed(original, "2026-10-27", "2026-10-29T10:00:00Z");
    applied(cheque, UUID.randomUUID(), "1500", "2026-10-30", "2026-10-30T10:00:00Z");
    held("2026-10-26", "2026-11-10T00:00:00Z", "2000"); // before the reversal
    held("2026-10-29", "2026-11-10T00:00:00Z", "0"); // between reversal and replacement
    held("2026-10-31", "2026-10-29T12:00:00Z", "0"); // replacement not known yet
    held("2026-10-31", "2026-11-10T00:00:00Z", "1500"); // after the replacement
  }

  @Test
  void aClearedPairIsCappedOnceAtWhatItsMatchesStillHold() {
    UUID cheque = heldCheque(), clearing = UUID.randomUUID();
    applied(cheque, UUID.randomUUID(), "2000", "2026-10-22", "2026-10-22T12:00:00Z");
    applied(cheque, UUID.randomUUID(), "1000", "2026-10-22", "2026-10-22T12:00:00Z");
    system.executeInTransaction(
        sql -> {
          sql.update(
              "INSERT INTO"
                  + " finance.finance_payment(id,tenant_id,uid,trading_partner_id,payment_number,direction,method,status,amount,currency,payment_date,created_at,updated_at)"
                  + " VALUES(?,?,?,?,?,'INBOUND','CHEQUE','RECEIVED',4284,'GBP','2026-10-24','2026-10-24T12:00Z','2026-10-24T12:00Z')",
              clearing,
              tenant,
              clearing.toString(),
              partner,
              "S1-" + clearing);
          sql.update(
              "UPDATE finance.collection_cheque SET state='CLEARED',cleared_payment_id=?,"
                  + "evidence_revision=1 WHERE id=?",
              clearing,
              cheque);
          sql.update(
              "INSERT INTO"
                  + " finance.collection_cheque_event(id,tenant_id,cheque_id,evidence_revision,action,state,effective_date,occurred_at,actor_id,reason,evidence,amount,received_on,due_on,instrument_reference,payment_id,created_at,updated_at)"
                  + " VALUES(?,?,?,1,'CLEAR','CLEARED','2026-10-24','2026-10-24T12:00Z',?,'Cheque"
                  + " cleared','Bank"
                  + " statement',4284,'2026-10-22','2026-11-30','NORTHBANK-77',?,'2026-10-24T12:00Z','2026-10-24T12:00Z')",
              UUID.randomUUID(),
              tenant,
              cheque,
              actor,
              clearing);
          // The clearing match for both applications, then a refund of 2,000 leaves 1,000 held.
          sql.update(
              "INSERT INTO"
                  + " finance.collection_payment_match(id,tenant_id,obligation_id,payment_id,cheque_id,amount,effective_date,occurred_at,actor_id,reason,created_at,updated_at,reversed_on,reversed_at,reversal_occurred_at,reversed_by,reversal_reason)"
                  + " VALUES(?,?,?,?,?,3000,'2026-10-24','2026-10-24T12:00Z',?,'Cheque cleared',"
                  + "'2026-10-24T12:00Z','2026-10-24T12:00Z','2026-10-26','2026-10-26T12:00Z','2026-10-26T12:00Z',?,'Part"
                  + " refunded to the buyer')",
              UUID.randomUUID(),
              tenant,
              obligation,
              clearing,
              cheque,
              actor,
              actor);
          sql.update(
              "INSERT INTO"
                  + " finance.collection_payment_match(id,tenant_id,obligation_id,payment_id,cheque_id,amount,effective_date,occurred_at,actor_id,reason,created_at,updated_at)"
                  + " VALUES(?,?,?,?,?,1000,'2026-10-26','2026-10-26T12:00Z',?,'Remainder after"
                  + " refund','2026-10-26T12:00Z','2026-10-26T12:00Z')",
              UUID.randomUUID(),
              tenant,
              obligation,
              clearing,
              cheque,
              actor);
          return null;
        });
    var before = cut("2026-10-25", "2026-11-10T00:00:00Z");
    money(before.receivedChequeAmount(), "3000");
    money(before.recordedCashAmount(), "3000");
    var after = cut("2026-10-27", "2026-11-10T00:00:00Z");
    // Capping each row would give 2,000 + 1,000 capped to 1,000 + 1,000 = 2,000.
    money(after.receivedChequeAmount(), "1000");
    money(after.recordedCashAmount(), "1000");
  }

  /**
   * PAYMENT-PLAN-9 S2 (D12): cheques applied to nothing, one held and one cleared, sit beside the
   * obligation. The report measures obligations (decision 10): it runs, counts neither as collected
   * money, and the clearing payment stays an ordinary received payment.
   */
  @Test
  void unlinkedChequesNeitherBreakTheReportNorCountAsCollectedMoney() {
    UUID held = UUID.randomUUID(), cleared = UUID.randomUUID(), clearing = UUID.randomUUID();
    system.executeInTransaction(
        sql -> {
          sql.update(
              "INSERT INTO"
                  + " finance.finance_payment(id,tenant_id,uid,trading_partner_id,payment_number,direction,method,status,amount,currency,payment_date,created_at,updated_at)"
                  + " VALUES(?,?,?,?,?,'INBOUND','CHEQUE','RECEIVED',6000,'GBP','2026-10-28','2026-10-28T12:00Z','2026-10-28T12:00Z')",
              clearing,
              tenant,
              clearing.toString(),
              partner,
              "S2-" + clearing);
          for (UUID cheque : List.of(held, cleared)) {
            boolean isCleared = cheque.equals(cleared);
            sql.update(
                "INSERT INTO"
                    + " finance.collection_cheque(id,tenant_id,partner_id,currency,amount,received_on,due_on,instrument_reference,state,cleared_payment_id,evidence_revision,created_at,updated_at)"
                    + " VALUES(?,?,?,'GBP',6000,'2026-10-25','2026-10-27','NORTHBANK-HELD',?,?,?,'2026-10-25T12:00Z','2026-10-25T12:00Z')",
                cheque,
                tenant,
                partner,
                isCleared ? "CLEARED" : "RECEIVED",
                isCleared ? clearing : null,
                isCleared ? 1 : 0);
            sql.update(
                "INSERT INTO"
                    + " finance.collection_cheque_event(id,tenant_id,cheque_id,evidence_revision,action,state,effective_date,occurred_at,actor_id,reason,evidence,amount,received_on,due_on,instrument_reference,created_at,updated_at)"
                    + " VALUES(?,?,?,0,'RECEIVE','RECEIVED','2026-10-25','2026-10-25T12:00Z',?,'Kept"
                    + " for future purchases','Physical instrument"
                    + " verified',6000,'2026-10-25','2026-10-27','NORTHBANK-HELD','2026-10-25T12:00Z','2026-10-25T12:00Z')",
                UUID.randomUUID(),
                tenant,
                cheque,
                actor);
          }
          sql.update(
              "INSERT INTO"
                  + " finance.collection_cheque_event(id,tenant_id,cheque_id,evidence_revision,action,state,effective_date,occurred_at,actor_id,reason,evidence,amount,received_on,due_on,instrument_reference,payment_id,created_at,updated_at)"
                  + " VALUES(?,?,?,1,'CLEAR','CLEARED','2026-10-28','2026-10-28T12:00Z',?,'Cheque"
                  + " cleared','Bank"
                  + " statement',6000,'2026-10-25','2026-10-27','NORTHBANK-HELD',?,'2026-10-28T12:00Z','2026-10-28T12:00Z')",
              UUID.randomUUID(),
              tenant,
              cleared,
              actor,
              clearing);
          return null;
        });
    var report = report("2026-11-03", "2026-11-04T12:00:00Z", null, 0, 20);
    var value = row(report);
    money(value.receivedChequeAmount(), "0");
    money(value.recordedCashAmount(), "0");
    money(value.outstandingAmount(), "4284");
    assertThat(report.totalElements()).isEqualTo(1);
    assertThat(report.currencies())
        .singleElement()
        .satisfies(
            total -> {
              money(total.recordedCashAmount(), "0");
              money(total.receivedChequeAmount(), "0");
            });
    assertThat(value.buckets()).doesNotContain("CHEQUE_CASH_OVERDUE", "DISHONOURED");
  }

  @Test
  void equalRecordingTimesUseCapturedChequeExecutionOrder() {
    UUID cheque = chequeEvidence();
    system.executeInTransaction(
        sql -> {
          sql.update(
              "INSERT INTO"
                  + " finance.collection_cheque_event(id,tenant_id,cheque_id,evidence_revision,action,state,effective_date,occurred_at,actor_id,reason,evidence,amount,received_on,due_on,instrument_reference,created_at,updated_at)"
                  + " VALUES(?,?,?,1,'RETURN','RETURNED','2026-10-30','2026-10-30T12:00Z',?,'Cheque"
                  + " returned','Signed return"
                  + " evidence',4284,'2026-10-30','2026-11-02','NORTHBANK-42','2026-10-30T12:00Z','2026-10-30T12:00Z')",
              UUID.randomUUID(),
              tenant,
              cheque,
              actor);
          return null;
        });
    var value = row(report("2026-11-03", "2026-11-04T12:00:00Z", null, 0, 20));
    money(value.receivedChequeAmount(), "0");
    money(value.missingInstrumentAmount(), "4284");
    assertThat(value.buckets())
        .contains("CHEQUE_DELIVERY_OVERDUE")
        .doesNotContain("CHEQUE_CASH_OVERDUE");
  }
}
