package com.fabricmanagement.finance.collection.app;

import static com.fabricmanagement.finance.collection.dto.ChequeHandoverDtos.*;

import com.fabricmanagement.finance.collection.domain.CollectionAmounts;
import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind;
import com.fabricmanagement.finance.collection.domain.CollectionWeightedDate;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.security.CollectionAccessService.Reach;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

final class CollectionChequeProposal {
  private final List<Row> rows;
  private final Map<UUID, Candidate> byId;
  private final Map<String, List<Candidate>> byCurrency;
  private final Map<UUID, BigDecimal> remaining = new java.util.HashMap<>();
  private final Map<OrderCurrency, Comparison> comparisons = new LinkedHashMap<>();
  private int slots;
  private boolean applicationLimitReached;

  CollectionChequeProposal(
      List<PreviewRowInput> input, List<Candidate> candidates, Set<Reference> recorded) {
    byId = candidates.stream().collect(Collectors.toMap(Candidate::id, Function.identity()));
    byCurrency = new TreeMap<>();
    input.stream()
        .filter(row -> row.intent() != RowIntent.NONE && row.currency() != null)
        .forEach(row -> byCurrency.putIfAbsent(row.currency(), new ArrayList<>()));
    candidates.forEach(
        target -> {
          byCurrency.get(target.currency()).add(target);
          remaining.put(target.id(), target.lacking());
        });
    var counts =
        input.stream()
            .map(Reference::of)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    rows = input.stream().map(row -> new Row(row, counts, recorded)).toList();
    slots =
        MAX_APPLICATIONS
            - rows.stream()
                .filter(Row::complete)
                .mapToInt(row -> row.input.applications().size())
                .sum();
    rows.stream().filter(row -> row.input.intent() == RowIntent.FIXED).forEach(this::reserve);
    rows.stream().filter(row -> row.input.intent() == RowIntent.AUTO).forEach(this::propose);
  }

  Set<UUID> coveredOrders() {
    return rows.stream()
        .flatMap(row -> row.covered.stream())
        .map(coverage -> coverage.target.row().getOrderId())
        .collect(Collectors.toSet());
  }

  private void reserve(Row row) {
    if (row.input.applications().isEmpty()) row.issue(IssueCode.CHEQUE_APPLICATION_REQUIRED, null);
    var used = new HashSet<UUID>();
    BigDecimal applied = BigDecimal.ZERO;
    for (var input : row.input.applications()) {
      var target = byId.get(input.obligationId());
      boolean compatible = target != null && target.currency().equals(row.input.currency());
      row.applications.add(
          new PreviewApplication(
              input.obligationId(), input.amount().toString(), compatible ? target.view() : null));
      if (!row.complete()) continue;
      if (!used.add(input.obligationId())) {
        row.issue(IssueCode.CHEQUE_APPLICATION_DUPLICATE, input.obligationId());
        continue;
      }
      BigDecimal amount = positive(input.amount(), row.input.currency());
      if (amount == null) {
        row.issue(IssueCode.CHEQUE_APPLICATION_AMOUNT_INVALID, input.obligationId());
        continue;
      }
      applied = applied.add(amount);
      if (!compatible) {
        row.issue(IssueCode.CHEQUE_APPLICATION_INCOMPATIBLE, input.obligationId());
        continue;
      }
      row.warnIfLate(target);
      BigDecimal available = remaining.get(target.id());
      if (amount.compareTo(available) > 0) row.issue(IssueCode.CHEQUE_OVERAPPLICATION, target.id());
      else row.covered.add(new Coverage(target, amount));
      // Even when the fixed allocation no longer fits, AUTO cannot take its remaining room.
      remaining.put(target.id(), available.subtract(amount).max(BigDecimal.ZERO));
    }
    if (row.complete()) {
      if (applied.compareTo(row.amount) > 0) {
        row.issue(IssueCode.CHEQUE_OVERAPPLICATION, null);
        row.covered.clear();
      }
      row.unallocated = row.amount.subtract(applied).max(BigDecimal.ZERO);
    }
  }

  private void propose(Row row) {
    if (!row.complete()) return;
    var candidates = byCurrency.get(row.input.currency());
    for (Candidate target : candidates.subList(0, Math.min(candidates.size(), MAX_APPLICATIONS))) {
      if (row.unallocated.signum() == 0) return;
      BigDecimal available = remaining.get(target.id());
      if (available.signum() == 0) continue;
      if (slots == 0) {
        applicationLimitReached = true;
        return;
      }
      BigDecimal amount = available.min(row.unallocated);
      remaining.put(target.id(), available.subtract(amount));
      row.unallocated = row.unallocated.subtract(amount);
      row.applications.add(
          new PreviewApplication(
              target.id(), CollectionAmounts.text(amount, target.currency()), target.view()));
      row.covered.add(new Coverage(target, amount));
      row.warnIfLate(target);
      slots--;
    }
    if (slots == 0 && row.unallocated.signum() > 0 && candidates.size() > MAX_APPLICATIONS)
      applicationLimitReached |=
          candidates.subList(MAX_APPLICATIONS, candidates.size()).stream()
              .anyMatch(target -> remaining.get(target.id()).signum() > 0);
  }

  List<MaturityComparison> compare(
      List<CollectionObligation> agreementRows,
      Reach read,
      Map<UUID, CollectionChequeTargets.Facts> facts) {
    var byOrderCurrency = agreementRows.stream().collect(Collectors.groupingBy(OrderCurrency::of));
    for (Row row : rows) {
      for (Coverage application : row.covered) {
        Candidate target = application.target();
        if (!CollectionChequeTargets.permits(read, target.row())) continue;
        var key = OrderCurrency.of(target.row());
        comparisons
            .computeIfAbsent(key, ignored -> new Comparison(target.orderNumber()))
            .add(target.row().getChequePaymentDate(), row.input.dueOn(), application.amount());
      }
    }
    return comparisons.entrySet().stream()
        .map(
            entry ->
                entry
                    .getValue()
                    .view(
                        entry.getKey(),
                        byOrderCurrency.getOrDefault(entry.getKey(), List.of()),
                        facts))
        .toList();
  }

  Preview result(List<MaturityComparison> maturity) {
    var candidates =
        byCurrency.entrySet().stream()
            .map(
                entry ->
                    new Candidates(
                        entry.getKey(),
                        entry.getValue().stream()
                            .limit(MAX_APPLICATIONS)
                            .map(Candidate::view)
                            .toList(),
                        entry.getValue().size() > MAX_APPLICATIONS))
            .toList();
    Map<String, Totals> totals = new TreeMap<>();
    rows.stream()
        .filter(Row::complete)
        .forEach(
            row -> totals.computeIfAbsent(row.input.currency(), ignored -> new Totals()).add(row));
    return new Preview(
        candidates,
        rows.stream().map(Row::view).toList(),
        totals.entrySet().stream().map(entry -> entry.getValue().view(entry.getKey())).toList(),
        maturity,
        applicationLimitReached ? PreviewNotice.CHEQUE_HANDOVER_APPLICATION_LIMIT : null);
  }

  private static BigDecimal positive(BigDecimal amount, String currency) {
    try {
      return CollectionAmounts.positive(amount, currency);
    } catch (CollectionRuleException invalidAmount) {
      return null;
    }
  }

  record Candidate(CollectionObligation row, String orderNumber, BigDecimal lacking) {
    UUID id() {
      return row.getId();
    }

    String currency() {
      return row.getCurrency();
    }

    Target view() {
      return new Target(
          id(),
          row.getVersion(),
          orderNumber,
          row.getExpectedDate(),
          row.getChequePaymentDate(),
          CollectionAmounts.text(lacking, currency()));
    }
  }

  record Reference(String currency, String value) {
    static Reference of(PreviewRowInput row) {
      return row.currency() == null
              || row.instrumentReference() == null
              || row.instrumentReference().isBlank()
          ? null
          : new Reference(row.currency(), row.instrumentReference().trim());
    }
  }

  private record Coverage(Candidate target, BigDecimal amount) {}

  private record OrderCurrency(UUID orderId, String currency) {
    static OrderCurrency of(CollectionObligation row) {
      return new OrderCurrency(row.getOrderId(), row.getCurrency());
    }
  }

  private static final class Row {
    private final PreviewRowInput input;
    private final boolean incomplete;
    private final BigDecimal amount;
    private final List<PreviewApplication> applications = new ArrayList<>();
    private final List<RowIssue> issues = new ArrayList<>();
    private final List<RowWarning> warnings = new ArrayList<>();
    private final List<Coverage> covered = new ArrayList<>();
    private BigDecimal unallocated;

    Row(PreviewRowInput input, Map<Reference, Long> counts, Set<Reference> recorded) {
      this.input = input;
      incomplete = input.currency() == null || input.amount() == null || input.dueOn() == null;
      amount = incomplete ? null : positive(input.amount(), input.currency());
      unallocated = amount;
      if (!incomplete && amount == null) issue(IssueCode.CHEQUE_AMOUNT_INVALID, null);
      if (input.instrumentReference() == null || input.instrumentReference().isBlank())
        issue(IssueCode.CHEQUE_REFERENCE_REQUIRED, null);
      if (input.drawerKind() == null) issue(IssueCode.CHEQUE_DRAWER_KIND_REQUIRED, null);
      Reference reference = Reference.of(input);
      if (reference != null && (counts.get(reference) > 1 || recorded.contains(reference)))
        warnings.add(new RowWarning(WarningCode.POSSIBLE_DUPLICATE_CHEQUE_REFERENCE, null, null));
    }

    boolean complete() {
      return amount != null;
    }

    void issue(IssueCode code, UUID obligationId) {
      issues.add(new RowIssue(code, obligationId));
    }

    void warnIfLate(Candidate target) {
      LocalDate agreed = target.row().getChequePaymentDate();
      if (agreed != null && input.dueOn().isAfter(agreed))
        warnings.add(new RowWarning(WarningCode.CHEQUE_DUE_AFTER_AGREED_DATE, target.id(), agreed));
    }

    PreviewRow view() {
      return new PreviewRow(
          input.rowKey(),
          incomplete,
          applications,
          unallocated == null ? null : CollectionAmounts.text(unallocated, input.currency()),
          issues,
          warnings);
    }
  }

  private static final class Totals {
    private final CollectionWeightedDate dates = new CollectionWeightedDate();
    private int count;
    private BigDecimal total = BigDecimal.ZERO;
    private BigDecimal own = BigDecimal.ZERO;
    private BigDecimal endorsed = BigDecimal.ZERO;
    private BigDecimal unallocated = BigDecimal.ZERO;

    void add(Row row) {
      count++;
      dates.add(row.amount, row.input.dueOn());
      total = total.add(row.amount);
      if (row.input.drawerKind() == DrawerKind.OWN) own = own.add(row.amount);
      if (row.input.drawerKind() == DrawerKind.ENDORSED) endorsed = endorsed.add(row.amount);
      unallocated = unallocated.add(row.unallocated);
    }

    CurrencyTotal view(String currency) {
      return new CurrencyTotal(
          currency,
          count,
          CollectionAmounts.text(total, currency),
          CollectionAmounts.text(own, currency),
          CollectionAmounts.text(endorsed, currency),
          CollectionAmounts.text(unallocated, currency),
          dates.average());
    }
  }

  private static final class Comparison {
    private final String orderNumber;
    private final CollectionWeightedDate agreedDates = new CollectionWeightedDate();
    private final CollectionWeightedDate chequeDates = new CollectionWeightedDate();
    private BigDecimal applied = BigDecimal.ZERO;
    private BigDecimal undated = BigDecimal.ZERO;

    Comparison(String orderNumber) {
      this.orderNumber = orderNumber;
    }

    void add(LocalDate agreed, LocalDate actual, BigDecimal amount) {
      applied = applied.add(amount);
      if (agreed == null) {
        undated = undated.add(amount);
        return;
      }
      agreedDates.add(amount, agreed);
      chequeDates.add(amount, actual);
    }

    MaturityComparison view(
        OrderCurrency key,
        List<CollectionObligation> rows,
        Map<UUID, CollectionChequeTargets.Facts> facts) {
      BigDecimal agreed = BigDecimal.ZERO;
      BigDecimal previouslyCovered = BigDecimal.ZERO;
      boolean partial = false;
      for (CollectionObligation row : rows) {
        var balance = facts.get(row.getId());
        if (balance == null) {
          partial = true;
          continue;
        }
        agreed = agreed.add(row.getAmount());
        previouslyCovered = previouslyCovered.add(row.getAmount().subtract(balance.lacking(row)));
      }
      LocalDate agreedDate = agreedDates.average();
      LocalDate chequeDate = chequeDates.average();
      String currency = key.currency();
      return new MaturityComparison(
          key.orderId(),
          orderNumber,
          currency,
          partial,
          CollectionAmounts.text(agreed, currency),
          CollectionAmounts.text(previouslyCovered, currency),
          CollectionAmounts.text(applied, currency),
          CollectionAmounts.text(undated, currency),
          agreedDate,
          chequeDate,
          agreedDate == null ? null : ChronoUnit.DAYS.between(agreedDate, chequeDate));
    }
  }
}
