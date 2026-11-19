package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.finance.collection.domain.CollectionAmounts;
import com.fabricmanagement.finance.collection.domain.CollectionCheque;
import com.fabricmanagement.finance.collection.domain.CollectionChequeApplication;
import com.fabricmanagement.finance.collection.domain.CollectionChequeEvent;
import com.fabricmanagement.finance.collection.domain.CollectionChequeHandover;
import com.fabricmanagement.finance.collection.domain.CollectionWeightedDate;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.dto.ChequeHandoverDtos.*;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeApplicationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeEventRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeHandoverRepository;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CollectionChequeHandoverQueryService {
  private final CollectionAccessService access;
  private final CollectionChequeHandoverRepository handovers;
  private final CollectionChequeApplicationRepository applications;
  private final CollectionChequeEventRepository events;

  public Page<HandoverSummary> list(UUID actor, UUID partner, String number, Pageable page) {
    var reach = requireRead(actor);
    var newest = Sort.by(Sort.Direction.DESC, "receivedOn", "occurredAt", "createdAt", "id");
    var bounded = PageRequest.of(page.getPageNumber(), Math.min(page.getPageSize(), 100), newest);
    String filter =
        number == null || number.isBlank() ? null : number.trim().toUpperCase(Locale.ROOT);
    return handovers
        .visibleHandovers(
            reach.tenantId(), partner, filter, reach.unrestricted(), reach.principals(), bounded)
        .map(CollectionChequeHandoverQueryService::summary);
  }

  public HandoverDetail detail(UUID id, UUID actor) {
    var reach = requireRead(actor);
    UUID tenant = reach.tenantId();
    var handover =
        handovers
            .findByTenantIdAndId(tenant, id)
            .orElseThrow(CollectionChequeHandoverQueryService::notFound);
    var visible = handovers.visibleCheques(tenant, id, reach.unrestricted(), reach.principals());
    if (visible.isEmpty()) throw notFound();
    var ids = visible.stream().map(CollectionCheque::getId).toList();
    var receipts =
        events.findByTenantIdAndChequeIdInAndAction(tenant, ids, "RECEIVE").stream()
            .collect(Collectors.toMap(CollectionChequeEvent::getChequeId, Function.identity()));
    var corrected = events.correctedCheques(tenant, ids);
    var applied =
        applications.activeOf(tenant, ids).stream()
            .collect(
                Collectors.toMap(
                    CollectionChequeApplication::getChequeId,
                    CollectionChequeApplication::getAmount,
                    BigDecimal::add));
    var totals = new TreeMap<String, Total>();
    var rows = new ArrayList<HandoverCheque>();
    for (var cheque : visible) {
      var receipt = receipts.get(cheque.getId());
      if (receipt == null)
        throw CollectionRuleException.conflict(
            "CHEQUE_HANDOVER_RECEIPT_MISSING", "The original receipt evidence could not be read");
      totals.computeIfAbsent(cheque.getCurrency(), ignored -> new Total()).add(receipt);
      rows.add(
          row(
              cheque,
              applied.getOrDefault(cheque.getId(), BigDecimal.ZERO),
              corrected.contains(cheque.getId())));
    }
    return new HandoverDetail(
        handover.getId(),
        handover.getHandoverNumber(),
        handover.getPartnerId(),
        handover.getReceivedOn(),
        handover.getOccurredAt(),
        handover.getActorId(),
        handover.getReason(),
        handover.getEvidence(),
        visible.size() < handover.getChequeCount(),
        List.copyOf(rows),
        totals.entrySet().stream().map(entry -> entry.getValue().view(entry.getKey())).toList());
  }

  private CollectionAccessService.Reach requireRead(UUID actor) {
    var reach = access.reach(actor, "read");
    if (reach.scope() == null) throw new AccessDeniedException("Missing finance permission: read");
    return reach;
  }

  private static HandoverSummary summary(CollectionChequeHandover value) {
    return new HandoverSummary(
        value.getId(),
        value.getHandoverNumber(),
        value.getPartnerId(),
        value.getReceivedOn(),
        value.getOccurredAt());
  }

  private static HandoverCheque row(
      CollectionCheque cheque, BigDecimal applied, boolean corrected) {
    String currency = cheque.getCurrency();
    return new HandoverCheque(
        cheque.getId(),
        cheque.getHandoverOrdinal(),
        cheque.getInstrumentReference(),
        currency,
        CollectionAmounts.text(cheque.getAmount(), currency),
        cheque.getDueOn(),
        cheque.getDrawerKind(),
        cheque.getDrawerName(),
        cheque.getState(),
        corrected,
        CollectionAmounts.text(applied, currency),
        CollectionAmounts.text(
            cheque.getAmount().subtract(applied).max(BigDecimal.ZERO), currency));
  }

  private static NotFoundException notFound() {
    return new NotFoundException("Cheque handover not found");
  }

  private static final class Total {
    private int count;
    private BigDecimal amount = BigDecimal.ZERO;
    private final CollectionWeightedDate dates = new CollectionWeightedDate();

    void add(CollectionChequeEvent receipt) {
      count++;
      amount = amount.add(receipt.getAmount());
      dates.add(receipt.getAmount(), receipt.getDueOn());
    }

    ReceiptTotal view(String currency) {
      return new ReceiptTotal(
          currency, count, CollectionAmounts.text(amount, currency), dates.average());
    }
  }
}
