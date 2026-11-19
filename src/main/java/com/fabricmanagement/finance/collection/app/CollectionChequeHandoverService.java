package com.fabricmanagement.finance.collection.app;

import static com.fabricmanagement.finance.collection.dto.ChequeHandoverDtos.MAX_APPLICATIONS;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.app.CollectionChequeService.HandoverPosition;
import com.fabricmanagement.finance.collection.app.CollectionChequeService.ReceiptApplication;
import com.fabricmanagement.finance.collection.app.CollectionChequeService.ReceiptEvidence;
import com.fabricmanagement.finance.collection.domain.CollectionAmounts;
import com.fabricmanagement.finance.collection.domain.CollectionCheque;
import com.fabricmanagement.finance.collection.domain.CollectionChequeHandover;
import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.dto.ChequeHandoverDtos.*;
import com.fabricmanagement.finance.collection.dto.CollectionDtos;
import com.fabricmanagement.finance.collection.dto.CollectionRequests;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeHandoverRepository;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import com.fabricmanagement.finance.common.app.FinanceDocumentNumberGenerator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollectionChequeHandoverService {
  private final CollectionCommandStore commands;
  private final CollectionAccessService access;
  private final CollectionPostingService posting;
  private final CollectionProjectionService projections;
  private final CollectionChequeTargets targets;
  private final CollectionChequeDrawerResolver drawers;
  private final CollectionChequeService cheques;
  private final CollectionChequeHandoverRepository handovers;
  private final FinanceDocumentNumberGenerator numbers;

  @Transactional
  public CollectionDtos.Result save(SaveRequest request, UUID actor) {
    commands.validate(request);
    var versions = validateShape(request);
    access.requireGrant(actor, "write");
    commands.lock();
    String hash = commands.hash("RECEIVE_CHEQUE_HANDOVER", request.partnerId(), request);
    var replay = commands.replay(request.operationId(), actor, hash);
    if (replay.isPresent()) return CollectionQueryService.result(replay.get());

    UUID tenant = TenantContext.requireTenantId();
    var obligations = posting.writableBatch(List.copyOf(versions.keySet()), actor);
    posting.synchronizeTargets(obligations);
    var stale =
        obligations.stream()
            .filter(row -> !Objects.equals(versions.get(row.getId()), row.getVersion()))
            .map(CollectionObligation::getId)
            .toList();
    if (!stale.isEmpty())
      throw CollectionRuleException.conflict(
              "CHEQUE_HANDOVER_STALE",
              "Review the instalments that changed before saving the handover")
          .withDetail("obligationIds", stale);

    commands.time(request.receivedOn(), request.occurredAt());
    drawers.requireCustomer(request.partnerId());
    var prepared = prepare(request, actor, tenant, obligations);

    var handover =
        handovers.saveAndFlush(
            CollectionChequeHandover.record(
                request.partnerId(),
                numbers.nextNumber(tenant, "CHB", request.receivedOn().getYear()),
                request.receivedOn(),
                request.occurredAt(),
                actor,
                request.reason(),
                request.evidence(),
                prepared.size()));
    var evidence =
        new ReceiptEvidence(
            request.partnerId(),
            request.receivedOn(),
            request.occurredAt(),
            actor,
            request.reason(),
            request.evidence());
    for (int row = 0; row < prepared.size(); row++) {
      var cheque = prepared.get(row);
      cheques.persistReceipt(
          evidence,
          cheque.input().currency(),
          cheque.amount(),
          cheque.input().dueOn(),
          cheque.input().instrumentReference(),
          cheque.drawer(),
          cheque.applications(),
          new HandoverPosition(handover.getId(), row + 1));
    }
    cheques.changeReceiptTargets(obligations);
    return CollectionQueryService.result(
        commands.record(
            request.operationId(),
            actor,
            hash,
            "CHEQUE_HANDOVER",
            handover.getId(),
            0,
            null,
            List.of()));
  }

  @Transactional
  public List<TargetRefresh> refreshTargets(RefreshRequest request, UUID actor) {
    commands.validate(request);
    access.requireGrant(actor, "write");
    var ids = request.obligationIds().stream().distinct().toList();
    // Reassignments take this lock too. Read targets after trying it, and check the whole
    // request even when busy so the answer never names an order outside current write scope.
    boolean canRefresh = commands.tryLock();
    var orders =
        posting.writableBatch(ids, actor).stream()
            .map(CollectionObligation::getOrderId)
            .distinct()
            .sorted(java.util.Comparator.comparing(UUID::toString))
            .toList();
    if (!canRefresh)
      return orders.stream().map(order -> new TargetRefresh(order, RefreshStatus.BUSY)).toList();
    return orders.stream()
        .map(
            order ->
                new TargetRefresh(
                    order,
                    switch (projections.refreshIfFree(order)) {
                      case REFRESHED -> RefreshStatus.REFRESHED;
                      case BUSY -> RefreshStatus.BUSY;
                      case UNREADABLE -> RefreshStatus.UNREADABLE;
                    }))
        .toList();
  }

  private static Map<UUID, Long> validateShape(SaveRequest request) {
    var keys = new HashSet<String>();
    var targets = new HashSet<UUID>();
    int applications = 0;
    for (int index = 0; index < request.rows().size(); index++) {
      var row = request.rows().get(index);
      if (!keys.add(row.rowKey()))
        throw atRow(
            CollectionRuleException.invalid("Each cheque row must have a distinct key"),
            row,
            index);
      var once = new HashSet<UUID>();
      for (var application : row.applications()) {
        if (!once.add(application.obligationId()))
          throw atRow(
              CollectionRuleException.invalid(
                  "CHEQUE_APPLICATION_DUPLICATE",
                  "Each obligation may appear only once per cheque"),
              row,
              index);
        targets.add(application.obligationId());
      }
      applications += row.applications().size();
    }
    if (applications > MAX_APPLICATIONS)
      throw CollectionRuleException.invalid(
          "CHEQUE_HANDOVER_APPLICATION_LIMIT", "A handover can have at most 500 applications");
    var versions = new LinkedHashMap<UUID, Long>();
    for (CollectionRequests.Version version : request.obligationVersions()) {
      if (versions.putIfAbsent(version.obligationId(), version.expectedVersion()) != null)
        throw invalidVersions();
    }
    if (!versions.keySet().equals(targets)) throw invalidVersions();
    return versions;
  }

  private List<Prepared> prepare(
      SaveRequest request, UUID actor, UUID tenant, List<CollectionObligation> obligations) {
    var byId =
        obligations.stream()
            .collect(Collectors.toMap(CollectionObligation::getId, Function.identity()));
    var facts = targets.load(tenant, obligations);
    var remaining = new HashMap<UUID, BigDecimal>();
    obligations.forEach(row -> remaining.put(row.getId(), facts.get(row.getId()).lacking(row)));
    var read = obligations.isEmpty() ? null : access.reach(actor, "read");
    var write = obligations.isEmpty() ? null : access.reach(actor, "write");
    var result = new ArrayList<Prepared>();
    for (int index = 0; index < request.rows().size(); index++) {
      var row = request.rows().get(index);
      try {
        var drawer =
            drawers.forReceipt(
                request.partnerId(), row.drawerKind(), row.drawerId(), row.drawerName());
        BigDecimal amount = CollectionAmounts.positive(row.amount(), row.currency());
        BigDecimal total = BigDecimal.ZERO;
        var applications = new ArrayList<ReceiptApplication>();
        for (var input : row.applications()) {
          UUID id = input.obligationId();
          var target = byId.get(id);
          if (!facts.get(id).eligible(target, read, write))
            throw CollectionRuleException.invalid(
                "CHEQUE_APPLICATION_INCOMPATIBLE",
                "Choose a readable open cheque instalment within your write scope");
          BigDecimal applied =
              cheques.validateReceiptApplication(
                  target, request.partnerId(), row.currency(), input.amount());
          BigDecimal available = remaining.get(id);
          if (applied.compareTo(available) > 0)
            throw CollectionRuleException.invalid(
                "CHEQUE_OVERAPPLICATION",
                "Cheque application exceeds the missing instrument amount");
          remaining.put(id, available.subtract(applied));
          total = total.add(applied);
          applications.add(new ReceiptApplication(id, applied));
        }
        if (total.compareTo(amount) > 0)
          throw CollectionRuleException.invalid(
              "CHEQUE_OVERAPPLICATION", "Applications exceed the cheque amount");
        result.add(new Prepared(row, drawer, amount, applications));
      } catch (CollectionRuleException refused) {
        throw atRow(refused, row, index);
      }
    }
    return result;
  }

  private static CollectionRuleException atRow(
      CollectionRuleException refused, SaveRowInput input, int index) {
    return refused.withDetail("rowKey", input.rowKey()).withDetail("row", index);
  }

  private static CollectionRuleException invalidVersions() {
    return CollectionRuleException.invalid(
        "CHEQUE_HANDOVER_VERSIONS_INVALID",
        "Provide one version for each applied instalment and no others");
  }

  private record Prepared(
      SaveRowInput input,
      CollectionCheque.Drawer drawer,
      BigDecimal amount,
      List<ReceiptApplication> applications) {}
}
