package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.tenant.TenantBusinessCalendarPort;
import com.fabricmanagement.finance.collection.app.port.CollectionSourcePort;
import com.fabricmanagement.finance.collection.domain.CollectionAmounts;
import com.fabricmanagement.finance.collection.dto.CollectionCustomerSummaryDtos.*;
import com.fabricmanagement.finance.collection.infra.repository.CollectionCustomerSummaryRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionCustomerSummaryRepository.Held;
import com.fabricmanagement.finance.collection.infra.repository.CollectionCustomerSummaryRepository.Plan;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollectionCustomerSummaryService {
  private static final int PAYMENT_BATCH_SIZE = 500;

  private final CollectionAccessService access;
  private final CollectionChequeDrawerResolver customers;
  private final CollectionCustomerSummaryRepository facts;
  private final CollectionSourcePort source;
  private final PaymentRepository payments;
  private final CollectionPaymentAvailability availability;
  private final CollectionReconciliationHealthService health;
  private final TenantBusinessCalendarPort calendar;
  private final Clock clock;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Summary summary(UUID partner, UUID actor) {
    var read = access.reach(actor, "read");
    if (read.scope() == null) throw new AccessDeniedException("Missing finance permission: read");
    customers.requireCustomerToRead(partner);
    var write = access.reach(actor, "write");
    var now = clock.instant();
    var zone = calendar.zone();
    var today = LocalDate.ofInstant(now, zone);
    var tenant = read.tenantId();
    var params =
        new MapSqlParameterSource()
            .addValue("tenant", tenant)
            .addValue("partner", partner)
            .addValue("allScope", read.unrestricted())
            .addValue("principals", read.principals())
            .addValue("asOf", today)
            .addValue("cutoff", Timestamp.from(now))
            .addValue("endAt", Timestamp.from(today.plusDays(1).atStartOfDay(zone).toInstant()))
            .addValue("owner", null)
            .addValue("orderId", null)
            .addValue("currency", null)
            .addValue("bucket", null)
            .addValue("fromDate", null)
            .addValue("toDate", null);
    boolean discoveryStale = health.stale();
    var lines = new TreeMap<String, Line>();
    var held = facts.held(params);
    var reviewByInstalment =
        held.stream()
            .filter(c -> c.offer() != null && c.applied().signum() == 0)
            .collect(Collectors.groupingBy(c -> c.offer().obligationId()));
    var plans = facts.plans(params);
    var orderNumbers =
        source.orderNumbers(tenant, plans.stream().map(Plan::orderId).distinct().toList());
    for (var row : plans) {
      lines
          .computeIfAbsent(row.currency(), Line::new)
          .plan(
              row,
              orderNumbers.get(row.orderId()),
              today,
              discoveryStale,
              write.permits(tenant, row.creator(), row.owner()),
              reviewByInstalment.getOrDefault(row.id(), List.of()));
    }
    for (var cheque : held) {
      if (cheque.visible()) {
        lines
            .computeIfAbsent(cheque.currency(), Line::new)
            .cheque(cheque, write.permits(tenant, cheque.creator(), null));
      }
    }
    addPayments(tenant, params, lines);
    return new Summary(
        partner,
        today,
        zone.getId(),
        now,
        read.unrestricted() ? Coverage.RECORDED_PLANS : Coverage.RESTRICTED_RECORDED_PLANS,
        discoveryStale,
        lines.values().stream().map(line -> line.dto(discoveryStale)).toList());
  }

  private void addPayments(UUID tenant, MapSqlParameterSource params, Map<String, Line> lines) {
    UUID after = null;
    List<UUID> ids;
    do {
      ids = facts.paymentPage(params, after, PAYMENT_BATCH_SIZE);
      if (ids.isEmpty()) return;
      var batch = payments.findByTenantIdAndIdIn(tenant, ids);
      var remaining = availability.of(tenant, batch);
      for (var payment : batch) {
        var value = remaining.get(payment.getId());
        if (value.eligible() && value.available().signum() > 0) {
          var line = lines.computeIfAbsent(payment.getCurrency(), Line::new);
          line.payments = line.payments.add(value.available());
        }
      }
      after = ids.getLast();
    } while (ids.size() == PAYMENT_BATCH_SIZE);
  }

  private static final class Line {
    private final String currency;
    private BigDecimal paymentDue = BigDecimal.ZERO;
    private BigDecimal deliveryDue = BigDecimal.ZERO;
    private BigDecimal unpaid = BigDecimal.ZERO;
    private BigDecimal applied = BigDecimal.ZERO;
    private BigDecimal payments = BigDecimal.ZERO;
    private BigDecimal cheques = BigDecimal.ZERO;
    private long undated;
    private long unavailable;
    private final List<Instalment> instalments = new ArrayList<>();
    private final List<Cheque> presented = new ArrayList<>();
    private final List<Cheque> offerable = new ArrayList<>();

    Line(String currency) {
      this.currency = currency;
    }

    void plan(
        Plan row,
        String orderNumber,
        LocalDate today,
        boolean discoveryStale,
        boolean writable,
        List<Held> offers) {
      if (row.expectedDate() == null) undated++;
      if (row.unavailable()) unavailable++;
      boolean trustworthy = !row.unavailable() && !discoveryStale;
      boolean transfer = "BANK_TRANSFER".equals(row.method());
      boolean due = row.expectedDate() != null && !row.expectedDate().isAfter(today);
      var action = NextAction.NONE;
      if (trustworthy) {
        unpaid = unpaid.add(row.outstanding());
        if (transfer && !offers.isEmpty()) {
          action = NextAction.REVIEW_PAYMENT_METHOD;
        } else if (due && !"CHEQUE".equals(row.method())) {
          paymentDue = paymentDue.add(row.outstanding());
          action = NextAction.PAYMENT_DUE;
        } else if (due && row.missing().signum() > 0) {
          deliveryDue = deliveryDue.add(row.missing());
          action = NextAction.CHEQUE_DELIVERY_DUE;
        }
      }
      instalments.add(
          new Instalment(
              row.id(),
              orderNumber,
              row.expectedDate(),
              amount(row.agreed()),
              trustworthy ? amount(row.outstanding()) : null,
              trustworthy ? amount(row.missing()) : null,
              action,
              trustworthy && transfer && writable,
              transfer
                  ? offers.stream()
                      .filter(Held::visible)
                      .map(c -> CollectionChequeOfferService.dto(c.offer()))
                      .toList()
                  : List.of()));
    }

    void cheque(Held held, boolean writable) {
      var item = new Cheque(held.id(), held.reference(), held.dueOn(), amount(held.amount()));
      applied = applied.add(held.applied());
      cheques = cheques.add(held.amount().subtract(held.applied()).max(BigDecimal.ZERO));
      if (held.presented()) presented.add(item);
      if (writable && held.offer() == null && held.applied().signum() == 0) offerable.add(item);
    }

    Currency dto(boolean stale) {
      boolean known = !stale && unavailable == 0;
      return new Currency(
          currency,
          undated,
          unavailable,
          known ? amount(paymentDue) : null,
          known ? amount(deliveryDue) : null,
          presented.size(),
          known ? amount(unpaid) : null,
          known ? amount(applied) : null,
          known ? amount(payments) : null,
          known ? amount(cheques) : null,
          List.copyOf(instalments),
          List.copyOf(presented),
          List.copyOf(offerable));
    }

    private String amount(BigDecimal number) {
      return CollectionAmounts.text(number, currency);
    }
  }
}
