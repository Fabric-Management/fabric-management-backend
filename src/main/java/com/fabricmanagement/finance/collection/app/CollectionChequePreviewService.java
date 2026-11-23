package com.fabricmanagement.finance.collection.app;

import static com.fabricmanagement.finance.collection.dto.ChequeHandoverDtos.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.app.port.CollectionSourcePort;
import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Method;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionObligationRepository;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollectionChequePreviewService {
  private static final int QUERY_BATCH = 500;
  private static final Comparator<LocalDate> DATES =
      Comparator.nullsLast(Comparator.naturalOrder());
  private static final Comparator<CollectionChequeProposal.Candidate> PROPOSAL_ORDER =
      Comparator.comparing(
              (CollectionChequeProposal.Candidate target) -> target.row().getExpectedDate(), DATES)
          .thenComparing(target -> target.row().getChequePaymentDate(), DATES)
          .thenComparing(
              CollectionChequeProposal.Candidate::orderNumber,
              Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(target -> target.id().toString());

  private final CollectionAccessService access;
  private final CollectionObligationRepository obligations;
  private final CollectionChequeRepository cheques;
  private final CollectionChequeTargets targets;
  private final CollectionSourcePort source;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Preview preview(PreviewRequest request, UUID actor) {
    var write = access.reach(actor, "write");
    if (write.scope() == null) throw new AccessDeniedException("Missing finance permission: write");
    List<PreviewRowInput> rows = request.rows();
    validateShape(rows);
    UUID tenant = TenantContext.requireTenantId();
    var currencies =
        rows.stream()
            .filter(row -> row.intent() != RowIntent.NONE && row.currency() != null)
            .map(PreviewRowInput::currency)
            .collect(Collectors.toSet());
    var read = currencies.isEmpty() ? null : access.reach(actor, "read");
    var candidates =
        currencies.isEmpty()
            ? List.<CollectionChequeProposal.Candidate>of()
            : candidates(tenant, request, read, write, currencies);
    var proposal =
        new CollectionChequeProposal(rows, candidates, recordedReferences(tenant, request));
    var coveredOrders = proposal.coveredOrders();
    if (coveredOrders.isEmpty()) return proposal.result(List.of());

    var agreementFilter =
        agreementRows(tenant, request.partnerId(), currencies)
            .and((root, query, cb) -> root.get("orderId").in(coveredOrders));
    var agreementRows = new ArrayList<CollectionObligation>();
    var facts = new HashMap<UUID, CollectionChequeTargets.Facts>();
    forEachBatch(
        agreementFilter,
        batch -> {
          agreementRows.addAll(batch);
          var readable =
              batch.stream().filter(row -> CollectionChequeTargets.permits(read, row)).toList();
          facts.putAll(targets.load(tenant, readable));
        });
    return proposal.result(proposal.compare(agreementRows, read, facts));
  }

  private static void validateShape(List<PreviewRowInput> rows) {
    if (rows.stream().map(PreviewRowInput::rowKey).distinct().count() != rows.size())
      throw CollectionRuleException.invalid("Each cheque row must have a distinct key");
    if (rows.stream()
        .anyMatch(row -> row.intent() != RowIntent.FIXED && !row.applications().isEmpty()))
      throw CollectionRuleException.invalid("Only fixed cheque rows may contain applications");
    if (rows.stream().mapToInt(row -> row.applications().size()).sum() > MAX_APPLICATIONS)
      throw CollectionRuleException.invalid(
          "CHEQUE_HANDOVER_APPLICATION_LIMIT", "A handover can have at most 500 applications");
  }

  private List<CollectionChequeProposal.Candidate> candidates(
      UUID tenant,
      PreviewRequest request,
      CollectionAccessService.Reach read,
      CollectionAccessService.Reach write,
      Set<String> currencies) {
    if (read.scope() == null) return List.of();
    var filter =
        agreementRows(tenant, request.partnerId(), currencies)
            .and(read.restriction())
            .and(write.restriction());
    if (request.targetObligationId() != null && !request.includeOtherObligations())
      filter =
          filter.and((root, query, cb) -> cb.equal(root.get("id"), request.targetObligationId()));
    var requested =
        request.rows().stream()
            .flatMap(row -> row.applications().stream())
            .map(ApplicationInput::obligationId)
            .collect(Collectors.toSet());
    var fixed = new HashMap<UUID, CollectionChequeProposal.Candidate>();
    var leadingByCurrency = new HashMap<String, TreeSet<CollectionChequeProposal.Candidate>>();
    forEachBatch(
        filter,
        rows -> {
          var facts = targets.load(tenant, rows);
          var eligible =
              rows.stream()
                  .filter(row -> facts.get(row.getId()).eligible(row, read, write))
                  .toList();
          if (eligible.isEmpty()) return;
          var numbers =
              source.orderNumbers(
                  tenant,
                  eligible.stream().map(CollectionObligation::getOrderId).distinct().toList());
          for (var row : eligible) {
            var candidate =
                new CollectionChequeProposal.Candidate(
                    row, numbers.get(row.getOrderId()), facts.get(row.getId()).lacking(row));
            if (requested.contains(row.getId())) fixed.put(row.getId(), candidate);
            var leading =
                leadingByCurrency.computeIfAbsent(
                    row.getCurrency(), ignored -> new TreeSet<>(PROPOSAL_ORDER));
            leading.add(candidate);
            // Retain enough overflow to know whether capacity remains after up to 500 fixed uses.
            if (leading.size() > MAX_APPLICATIONS * 2 + 1) leading.pollLast();
          }
        });
    return Stream.concat(
            leadingByCurrency.values().stream().flatMap(Collection::stream),
            fixed.values().stream())
        .distinct()
        .sorted(PROPOSAL_ORDER)
        .toList();
  }

  private void forEachBatch(
      Specification<CollectionObligation> filter, Consumer<List<CollectionObligation>> consume) {
    var batchFilter = filter;
    while (true) {
      var rows =
          obligations.findBy(
              batchFilter, query -> query.sortBy(Sort.by("id")).limit(QUERY_BATCH).all());
      if (rows.isEmpty()) return;
      consume.accept(rows);
      if (rows.size() < QUERY_BATCH) return;
      UUID last = rows.getLast().getId();
      batchFilter = filter.and((root, query, cb) -> cb.greaterThan(root.get("id"), last));
    }
  }

  private Set<CollectionChequeProposal.Reference> recordedReferences(
      UUID tenant, PreviewRequest request) {
    var references =
        request.rows().stream()
            .map(CollectionChequeProposal.Reference::of)
            .filter(java.util.Objects::nonNull)
            .toList();
    if (references.isEmpty()) return Set.of();
    return cheques
        .references(
            tenant,
            request.partnerId(),
            references.stream()
                .map(CollectionChequeProposal.Reference::currency)
                .collect(Collectors.toSet()),
            references.stream()
                .map(CollectionChequeProposal.Reference::value)
                .collect(Collectors.toSet()))
        .stream()
        .map(
            row ->
                new CollectionChequeProposal.Reference(
                    row.getCurrency(), row.getInstrumentReference()))
        .collect(Collectors.toSet());
  }

  private static Specification<CollectionObligation> agreementRows(
      UUID tenant, UUID partner, Collection<String> currencies) {
    return (root, query, cb) ->
        cb.and(
            cb.equal(root.get("tenantId"), tenant),
            cb.equal(root.get("partnerId"), partner),
            root.get("currency").in(currencies),
            cb.isTrue(root.get("isActive")),
            cb.isFalse(root.get("cancelled")),
            cb.equal(root.get("method"), Method.CHEQUE));
  }
}
