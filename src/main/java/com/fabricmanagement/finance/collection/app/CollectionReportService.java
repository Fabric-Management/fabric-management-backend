package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.tenant.TenantBusinessCalendarPort;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.dto.CollectionReportDtos.*;
import com.fabricmanagement.finance.collection.infra.repository.CollectionReportRepository;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollectionReportService {
  private final CollectionReportRepository reports;
  private final CollectionAccessService access;
  private final TenantBusinessCalendarPort calendar;
  private final TradingPartnerResolver partners;
  private final UserQueryService users;
  private final Clock clock;
  private final CollectionReconciliationHealthService health;

  public record Filter(
      LocalDate asOfDate,
      Instant knowledgeCutoff,
      UUID partnerId,
      UUID responsibleUserId,
      UUID orderId,
      String currency,
      String bucket,
      LocalDate from,
      LocalDate to,
      int page,
      int size) {}

  private static final Set<String> BUCKETS =
      Set.of(
          "UNKNOWN_BALANCE",
          "CANCELLED_REVIEW",
          "OVER_SETTLED_REVIEW",
          "REFUND_PENDING",
          "SETTLED",
          "CASH_OVERDUE",
          "CHEQUE_DELIVERY_OVERDUE",
          "CHEQUE_CASH_OVERDUE",
          "CHEQUE_AWAITING_CLEARING",
          "DUE_TODAY",
          "FUTURE",
          "UNDATED",
          "DISHONOURED");

  /**
   * One database snapshot for page, count and totals; current access is checked even for old cuts.
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Report report(UUID actor, Filter f) {
    var reach = access.reach(actor, "read");
    if (reach.scope() == null)
      throw new org.springframework.security.access.AccessDeniedException(
          "Finance read access is required");
    var now = clock.instant();
    var zone = calendar.zone();
    var asOf = f.asOfDate() == null ? LocalDate.ofInstant(now, zone) : f.asOfDate();
    var cutoff = f.knowledgeCutoff() == null ? now : f.knowledgeCutoff();
    if (cutoff.isAfter(now)
        || f.page() < 0
        || f.size() < 1
        || f.size() > 100
        || (f.currency() != null && !f.currency().matches("[A-Z]{3}"))
        || (f.bucket() != null && !BUCKETS.contains(f.bucket()))
        || (f.from() != null && f.to() != null && f.from().isAfter(f.to())))
      throw CollectionRuleException.invalid("Invalid collection report cut, filter or page");
    var p =
        new MapSqlParameterSource()
            .addValue("tenant", reach.tenantId())
            .addValue(
                "allScope",
                reach.scope() == DataScope.GLOBAL || reach.scope() == DataScope.ORGANIZATION)
            .addValue(
                "principals", reach.principals().isEmpty() ? Set.of(actor) : reach.principals())
            .addValue("asOf", asOf)
            .addValue("cutoff", Timestamp.from(cutoff))
            .addValue("endAt", Timestamp.from(asOf.plusDays(1).atStartOfDay(zone).toInstant()))
            .addValue("partner", f.partnerId())
            .addValue("owner", f.responsibleUserId())
            .addValue("orderId", f.orderId())
            .addValue("currency", f.currency())
            .addValue("bucket", f.bucket())
            .addValue("fromDate", f.from())
            .addValue("toDate", f.to())
            .addValue("limit", f.size())
            .addValue("offset", (long) f.page() * f.size());
    var rows = reports.rows(p);
    var names =
        partners.resolveDisplayNames(
            reach.tenantId(),
            rows.stream()
                .map(
                    com.fabricmanagement.finance.collection.domain.CollectionReportFacts.Row
                        ::partnerId)
                .distinct()
                .toList());
    var owners =
        users.findRecordedActorNames(
            reach.tenantId(),
            rows.stream()
                .map(
                    com.fabricmanagement.finance.collection.domain.CollectionReportFacts.Row
                        ::responsibleUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    var labelled =
        rows.stream()
            .map(
                r ->
                    new Row(
                        r.id(),
                        r.orderId(),
                        r.partnerId(),
                        names.get(r.partnerId()),
                        r.responsibleUserId(),
                        r.responsibleUserId() == null ? null : owners.get(r.responsibleUserId()),
                        r.currency(),
                        r.method(),
                        r.expectedDate(),
                        r.chequePaymentDate(),
                        r.plannedAmount(),
                        r.recordedCashAmount(),
                        r.outstandingAmount(),
                        r.receivedChequeAmount(),
                        r.missingInstrumentAmount(),
                        r.refundPendingAmount(),
                        r.cancelled(),
                        r.sourceStale(),
                        r.sourceMissing(),
                        r.sourceObservedAt(),
                        r.buckets(),
                        r.warnings()))
            .toList();
    var warnings =
        new ArrayList<>(
            List.of(
                "RECORDED_AGREED_PLANS_ONLY",
                "LEGACY_AND_UNPROJECTED_COVERAGE_UNKNOWN",
                "NO_INVOICE_FORECAST_INCLUDED",
                "NO_FX_CONSOLIDATED_TOTAL",
                "CURRENT_RESPONSIBILITY_AND_ACCESS",
                "PAYMENT_VOID_OBSERVATION_MAY_LAG"));
    if (health.stale()) warnings.add("SOURCE_DISCOVERY_STALE");
    // GROUP BY keeps every filtered row in exactly one group: the page total is their sum.
    var totals = reports.totals(p);
    return new Report(
        asOf,
        cutoff,
        now,
        zone.getId(),
        labelled,
        f.page(),
        f.size(),
        totals.stream().mapToLong(c -> c.rows()).sum(),
        totals.stream()
            .map(
                c ->
                    new com.fabricmanagement.finance.collection.dto.CollectionReportDtos.Currency(
                        c.currency(),
                        c.rows(),
                        c.unknownBalanceRows(),
                        c.plannedAmount(),
                        c.recordedCashAmount(),
                        c.outstandingAmount(),
                        c.receivedChequeAmount(),
                        c.missingInstrumentAmount(),
                        c.refundPendingAmount()))
            .toList(),
        reports.oldestSourceCheck(p),
        null,
        List.copyOf(warnings));
  }
}
