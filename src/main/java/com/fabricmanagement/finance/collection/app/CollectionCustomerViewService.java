package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.domain.CollectionAmounts;
import com.fabricmanagement.finance.collection.domain.CollectionCheque;
import com.fabricmanagement.finance.collection.domain.CollectionChequeApplication;
import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.ChequeState;
import com.fabricmanagement.finance.collection.dto.CollectionDtos;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeApplicationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionObligationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import com.fabricmanagement.finance.payment.domain.Payment;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PAYMENT-PLAN-9 R5: the customer's value that is not yet matched or applied to payment-plan
 * instalments. Payments and held cheques stay separate lines per currency and are never added
 * together (decision 3); nothing here allocates anything (decision 4). Payment availability is the
 * same calculation the match guard uses ({@link CollectionPaymentAvailability}). A cleared cheque's
 * remainder appears under payments, as its clearing payment, never under cheques.
 *
 * <p>Visibility follows R6: an item applied to nothing is reached through its creator under the
 * finance read scope; a cheque with active applications only when every obligation it covers is.
 */
@Service
@RequiredArgsConstructor
public class CollectionCustomerViewService {
  static final int PAYMENT_LIMIT = 500;
  static final int HISTORY_LIMIT = 50;
  private static final Set<ChequeState> HELD = Set.of(ChequeState.RECEIVED, ChequeState.PRESENTED);

  private final CollectionAccessService access;
  private final CollectionChequeDrawerResolver customers;
  private final CollectionPaymentMatchRepository matches;
  private final PaymentRepository payments;
  private final CollectionPaymentAvailability availabilities;
  private final CollectionChequeRepository cheques;
  private final CollectionChequeApplicationRepository applications;
  private final CollectionObligationRepository obligations;

  @Transactional(readOnly = true)
  public CollectionDtos.CustomerAvailability availability(UUID partnerId, UUID actor) {
    UUID tenant = TenantContext.requireTenantId();
    var read = access.reach(actor, "read");
    if (read.scope() == null) throw new AccessDeniedException("Missing finance permission: read");
    customers.requireCustomerToRead(partnerId);
    var lines = new TreeMap<String, Line>();

    // Payments: the shared availability, eligible payments with something left only.
    boolean allScope =
        read.scope() == com.fabricmanagement.platform.user.domain.DataScope.GLOBAL
            || read.scope() == com.fabricmanagement.platform.user.domain.DataScope.ORGANIZATION;
    var candidates =
        matches.unmatchedPaymentIds(
            tenant, partnerId, allScope, read.principals(), PAYMENT_LIMIT + 1);
    boolean paymentsTruncated = candidates.size() > PAYMENT_LIMIT;
    var ids = candidates.subList(0, Math.min(candidates.size(), PAYMENT_LIMIT));
    List<Payment> visible =
        (ids.isEmpty() ? List.<Payment>of() : payments.findByTenantIdAndIdIn(tenant, ids))
            .stream()
                .filter(payment -> read.permits(tenant, payment.getCreatedBy(), null))
                .sorted(
                    java.util.Comparator.comparing(Payment::getPaymentDate)
                        .reversed()
                        .thenComparing(Payment::getId))
                .toList();
    var available = availabilities.of(tenant, visible);
    Map<UUID, BigDecimal> invoiced =
        visible.isEmpty()
            ? Map.of()
            : matches
                .invoiceAllocated(tenant, visible.stream().map(Payment::getId).toList())
                .stream()
                .collect(
                    Collectors.toMap(
                        CollectionPaymentMatchRepository.Balance::getId,
                        CollectionPaymentMatchRepository.Balance::getAmount));
    for (var payment : visible) {
      var value = available.get(payment.getId());
      if (!value.eligible() || value.available().signum() <= 0) continue;
      String currency = payment.getCurrency();
      lines
          .computeIfAbsent(currency, Line::new)
          .payment(
              new CollectionDtos.AvailablePayment(
                  payment.getId(),
                  payment.getPaymentNumber(),
                  payment.getPaymentDate(),
                  payment.getMethod().name(),
                  CollectionAmounts.text(payment.getAmount().getAmount(), currency),
                  CollectionAmounts.text(value.matched(), currency),
                  CollectionAmounts.text(value.heldForRefund(), currency),
                  CollectionAmounts.text(value.available(), currency),
                  CollectionAmounts.text(
                      invoiced.getOrDefault(payment.getId(), BigDecimal.ZERO), currency),
                  value.clearedChequeId()),
              value.available());
    }

    // Cheques: held ones offer amount minus active applications; others are history only.
    var held = cheques.findByTenantIdAndPartnerIdAndStateIn(tenant, partnerId, HELD);
    var past =
        cheques.findByTenantIdAndPartnerIdAndStateNotIn(
            tenant,
            partnerId,
            HELD,
            PageRequest.of(
                0, HISTORY_LIMIT, Sort.by(Sort.Direction.DESC, "receivedOn", "createdAt", "id")));
    var all = Stream.concat(held.stream(), past.getContent().stream()).toList();
    var active =
        all.isEmpty()
            ? List.<CollectionChequeApplication>of()
            : applications.activeOf(tenant, all.stream().map(CollectionCheque::getId).toList());
    var byCheque =
        active.stream().collect(Collectors.groupingBy(CollectionChequeApplication::getChequeId));
    var targetIds =
        active.stream().map(CollectionChequeApplication::getObligationId).distinct().toList();
    Map<UUID, CollectionObligation> targets =
        (targetIds.isEmpty()
                ? List.<CollectionObligation>of()
                : obligations.findByTenantIdAndIdIn(tenant, targetIds))
            .stream().collect(Collectors.toMap(CollectionObligation::getId, Function.identity()));
    var history = new ArrayList<CollectionDtos.HeldCheque>();
    for (var cheque : held) {
      var rows = byCheque.getOrDefault(cheque.getId(), List.of());
      if (!visible(read, tenant, cheque, rows, targets)) continue;
      BigDecimal applied = total(rows);
      BigDecimal left = cheque.getAmount().subtract(applied).max(BigDecimal.ZERO);
      var item = cheque(cheque, applied, left);
      if (left.signum() > 0)
        lines.computeIfAbsent(cheque.getCurrency(), Line::new).cheque(item, left);
      else history.add(item);
    }
    for (var cheque : past.getContent()) {
      var rows = byCheque.getOrDefault(cheque.getId(), List.of());
      if (visible(read, tenant, cheque, rows, targets))
        history.add(cheque(cheque, total(rows), BigDecimal.ZERO));
    }
    history.sort(
        java.util.Comparator.comparing(CollectionDtos.HeldCheque::receivedOn)
            .reversed()
            .thenComparing(CollectionDtos.HeldCheque::id));
    return new CollectionDtos.CustomerAvailability(
        partnerId,
        lines.values().stream().map(Line::dto).toList(),
        history,
        paymentsTruncated,
        past.hasNext());
  }

  private static boolean visible(
      CollectionAccessService.Reach read,
      UUID tenant,
      CollectionCheque cheque,
      List<CollectionChequeApplication> rows,
      Map<UUID, CollectionObligation> targets) {
    if (rows.isEmpty()) return read.permits(tenant, cheque.getCreatedBy(), null);
    return rows.stream()
        .map(CollectionChequeApplication::getObligationId)
        .distinct()
        .allMatch(
            id -> {
              var row = targets.get(id);
              return row != null
                  && read.permits(tenant, row.getCreatedBy(), row.getResponsibleUserId());
            });
  }

  private static BigDecimal total(List<CollectionChequeApplication> rows) {
    return rows.stream()
        .map(CollectionChequeApplication::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static CollectionDtos.HeldCheque cheque(
      CollectionCheque cheque, BigDecimal applied, BigDecimal available) {
    String currency = cheque.getCurrency();
    return new CollectionDtos.HeldCheque(
        cheque.getId(),
        cheque.getVersion(),
        currency,
        cheque.getState(),
        cheque.getDrawerKind(),
        cheque.getDrawerName(),
        cheque.getInstrumentReference(),
        cheque.getReceivedOn(),
        cheque.getDueOn(),
        CollectionAmounts.text(cheque.getAmount(), currency),
        CollectionAmounts.text(applied, currency),
        CollectionAmounts.text(available, currency));
  }

  /** One currency's two lines, accumulated separately and never added together. */
  private static final class Line {
    private final String currency;
    private BigDecimal paymentsAvailable = BigDecimal.ZERO;
    private BigDecimal chequesAvailable = BigDecimal.ZERO;
    private final List<CollectionDtos.AvailablePayment> payments = new ArrayList<>();
    private final List<CollectionDtos.HeldCheque> cheques = new ArrayList<>();

    Line(String currency) {
      this.currency = currency;
    }

    void payment(CollectionDtos.AvailablePayment item, BigDecimal amount) {
      payments.add(item);
      paymentsAvailable = paymentsAvailable.add(amount);
    }

    void cheque(CollectionDtos.HeldCheque item, BigDecimal amount) {
      cheques.add(item);
      chequesAvailable = chequesAvailable.add(amount);
    }

    CollectionDtos.CurrencyAvailability dto() {
      return new CollectionDtos.CurrencyAvailability(
          currency,
          CollectionAmounts.text(paymentsAvailable, currency),
          CollectionAmounts.text(chequesAvailable, currency),
          List.copyOf(payments),
          List.copyOf(cheques));
    }
  }
}
