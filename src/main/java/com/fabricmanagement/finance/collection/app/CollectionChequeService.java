package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.finance.collection.domain.*;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.ChequeAction;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.dto.CollectionRequests;
import com.fabricmanagement.finance.collection.infra.repository.*;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import com.fabricmanagement.finance.payment.app.PaymentService;
import com.fabricmanagement.finance.payment.domain.PaymentStatus;
import com.fabricmanagement.finance.payment.dto.CreatePaymentRequest;
import com.fabricmanagement.finance.payment.dto.ReversePaymentRequest;
import com.fabricmanagement.finance.period.app.port.FinancialPeriodGuard;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CollectionChequeService {
  private final CollectionCommandStore commands;
  private final CollectionChequeRepository cheques;
  private final CollectionChequeApplicationRepository applications;
  private final CollectionChequeEventRepository events;
  private final CollectionObligationRepository obligations;
  private final CollectionPaymentMatchRepository matches;
  private final CollectionPostingService posting;
  private final CollectionAccessService access;
  private final PaymentService payments;
  private final FinancialPeriodGuard periods;
  private final CollectionEvents changes;

  public CollectionCommandReceipt receive(CollectionRequests.ReceiveCheque request, UUID actor) {
    commands.validate(request);
    commands.lock();
    UUID tenant = TenantContext.requireTenantId();
    var rows =
        posting.writableBatch(
            request.applications().stream()
                .map(CollectionRequests.Application::obligationId)
                .toList(),
            actor);
    String hash = commands.hash("RECEIVE_CHEQUE", request.partnerId(), request);
    var replay = commands.replay(request.operationId(), actor, hash);
    if (replay.isPresent()) return replay.get();
    commands.time(request.receivedOn(), request.occurredAt());
    BigDecimal amount = CollectionAmounts.positive(request.amount(), request.currency());
    posting.freshAll(rows);
    var received =
        applications
            .received(tenant, rows.stream().map(CollectionObligation::getId).distinct().toList())
            .stream()
            .collect(
                Collectors.toMap(
                    CollectionPaymentMatchRepository.Balance::getId,
                    CollectionPaymentMatchRepository.Balance::getAmount));
    var cash =
        matches
            .nonChequeSettledFor(
                tenant, rows.stream().map(CollectionObligation::getId).distinct().toList())
            .stream()
            .collect(
                Collectors.toMap(
                    CollectionPaymentMatchRepository.Balance::getId,
                    CollectionPaymentMatchRepository.Balance::getAmount));
    var ids = new HashSet<UUID>();
    BigDecimal applied = BigDecimal.ZERO;
    for (int i = 0; i < rows.size(); i++) {
      var row = rows.get(i);
      var input = request.applications().get(i);
      if (!ids.add(row.getId()))
        throw CollectionRuleException.invalid("Each obligation may appear only once per cheque");
      commands.version(input.expectedVersion(), row.getVersion());
      requireInstrumentTarget(row, request.partnerId(), request.currency());
      BigDecimal value = CollectionAmounts.positive(input.amount(), row.getCurrency());
      if (value.compareTo(
              row.getAmount()
                  .subtract(received.getOrDefault(row.getId(), BigDecimal.ZERO))
                  .subtract(cash.getOrDefault(row.getId(), BigDecimal.ZERO))
                  .max(BigDecimal.ZERO))
          > 0)
        throw CollectionRuleException.conflict(
            "CHEQUE_OVERAPPLICATION", "Cheque application exceeds the missing instrument amount");
      applied = applied.add(value);
    }
    if (applied.compareTo(amount) > 0)
      throw CollectionRuleException.invalid("Applications exceed the cheque amount");
    var cheque =
        cheques.saveAndFlush(
            CollectionCheque.receive(
                request.partnerId(),
                request.currency(),
                amount,
                request.receivedOn(),
                request.dueOn(),
                request.instrumentReference().trim(),
                request.evidence()));
    for (int i = 0; i < rows.size(); i++) {
      applications.save(
          CollectionChequeApplication.create(
              cheque.getId(),
              rows.get(i).getId(),
              CollectionAmounts.positive(
                  request.applications().get(i).amount(), request.currency())));
      rows.get(i).financialChange();
      obligations.save(rows.get(i));
      changes.changed(rows.get(i).getId());
    }
    applications.flush();
    obligations.flush();
    events.saveAndFlush(
        CollectionChequeEvent.record(
            cheque,
            "RECEIVE",
            request.receivedOn(),
            request.occurredAt(),
            actor,
            request.reason(),
            request.evidence()));
    return commands.record(
        request.operationId(),
        actor,
        hash,
        "CHEQUE",
        cheque.getId(),
        cheque.getVersion(),
        null,
        List.of());
  }

  public CollectionCommandReceipt transition(
      UUID chequeId, CollectionRequests.Transition request, UUID actor) {
    commands.validate(request);
    commands.lock();
    UUID tenant = TenantContext.requireTenantId();
    var cheque =
        cheques
            .findByTenantIdAndId(tenant, chequeId)
            .orElseThrow(() -> new NotFoundException("Cheque not found"));
    requireChequeWrite(cheque, actor);
    var applied = applications.findByTenantIdAndChequeId(tenant, chequeId);
    var rows =
        posting.writableBatch(
            applied.stream().map(CollectionChequeApplication::getObligationId).toList(), actor);
    requireClearingReversal(cheque, request.action(), rows, actor);
    String hash = commands.hash("CHEQUE_TRANSITION", chequeId, request);
    var replay = commands.replay(request.operationId(), actor, hash);
    if (replay.isPresent()) return replay.get();
    if (applied.isEmpty())
      throw CollectionRuleException.conflict(
          "CHEQUE_APPLICATIONS_MISSING", "The instrument has no verified collection applications");
    commands.version(request.expectedVersion(), cheque.getVersion());
    commands.time(request.effectiveDate(), request.occurredAt());
    var latestEffective = events.latestEffectiveDate(tenant, chequeId);
    if (latestEffective != null && request.effectiveDate().isBefore(latestEffective))
      throw CollectionRuleException.invalid(
          "A cheque transition cannot precede its recorded state history");
    cheque.getState().next(request.action());
    if (request.effectiveDate().isBefore(cheque.getReceivedOn()))
      throw CollectionRuleException.invalid("Cheque event cannot precede receipt");
    if (request.action() != ChequeAction.CLEAR && request.paymentId() != null
        || request.action() != ChequeAction.CORRECT && request.correction() != null)
      throw CollectionRuleException.invalid(
          "Payment and correction fields are valid only for their specific action");
    var matchIds = new ArrayList<UUID>();
    if (request.action() == ChequeAction.CLEAR) {
      requireVersions(rows, request.obligations(), actor);
      var payment = clear(cheque, applied, rows, request, actor, matchIds);
      cheque.transition(ChequeAction.CLEAR, payment, request.evidence());
    } else if (request.action() == ChequeAction.CORRECT) {
      var correction = request.correction();
      if (correction == null)
        throw CollectionRuleException.invalid("Corrected instrument facts are required");
      commands.time(correction.receivedOn(), request.occurredAt());
      if (correction.receivedOn().isAfter(request.effectiveDate()))
        throw CollectionRuleException.invalid(
            "The corrected receipt date cannot follow the correction effective date");
      if (request.effectiveDate().isBefore(correction.receivedOn()))
        throw CollectionRuleException.invalid(
            "Correction effective date cannot precede corrected receipt");
      BigDecimal amount = CollectionAmounts.positive(correction.amount(), cheque.getCurrency());
      if (applied.stream()
              .map(CollectionChequeApplication::getAmount)
              .reduce(BigDecimal.ZERO, BigDecimal::add)
              .compareTo(amount)
          > 0)
        throw CollectionRuleException.conflict(
            "CHEQUE_OVERAPPLICATION", "Corrected amount is below its recorded applications");
      cheque.correct(
          amount,
          correction.receivedOn(),
          correction.dueOn(),
          correction.instrumentReference().trim(),
          request.evidence());
    } else {
      if (cheque.getState().reversesClearing(request.action())) {
        requireVersions(rows, request.obligations(), actor);
        reverseClearing(cheque, request, actor, matchIds);
      }
      cheque.transition(request.action(), null, request.evidence());
    }
    cheques.saveAndFlush(cheque);
    // Present/return/correct also change instrument facts relevant to the expectation's version.
    rows.forEach(
        row -> {
          row.financialChange();
          obligations.save(row);
          changes.changed(row.getId());
        });
    obligations.flush();
    events.saveAndFlush(
        CollectionChequeEvent.record(
            cheque,
            request.action().name(),
            request.effectiveDate(),
            request.occurredAt(),
            actor,
            request.reason(),
            request.evidence()));
    return commands.record(
        request.operationId(),
        actor,
        hash,
        "CHEQUE",
        cheque.getId(),
        cheque.getVersion(),
        cheque.getClearedPaymentId(),
        matchIds);
  }

  private UUID clear(
      CollectionCheque cheque,
      List<CollectionChequeApplication> applied,
      List<CollectionObligation> rows,
      CollectionRequests.Transition request,
      UUID actor,
      List<UUID> matchIds) {
    UUID tenant = TenantContext.requireTenantId();
    periods.assertPostingAllowed(tenant, request.effectiveDate());
    UUID paymentId = request.paymentId();
    if (paymentId == null)
      paymentId =
          payments
              .createPayment(
                  new CreatePaymentRequest(
                      cheque.getPartnerId(),
                      "INBOUND",
                      "CHEQUE",
                      cheque.getAmount(),
                      cheque.getCurrency(),
                      request.effectiveDate(),
                      cheque.getInstrumentReference(),
                      request.reason(),
                      null))
              .id();
    var payment = posting.payment(paymentId);
    if (payment.getDirection()
            != com.fabricmanagement.finance.payment.domain.PaymentDirection.INBOUND
        || payment.getStatus() == PaymentStatus.VOIDED
        || payment.isReversed()
        || payment.isReversal()
        || payment.getAmount().getAmount().compareTo(cheque.getAmount()) != 0
        || !payment.getTradingPartnerId().equals(cheque.getPartnerId())
        || !payment.getCurrency().equals(cheque.getCurrency()))
      throw CollectionRuleException.conflict(
          "CHEQUE_PAYMENT_INCOMPATIBLE",
          "Clearing requires one payment for exactly this instrument amount, partner and currency");
    if (matches.matchedPayment(tenant, paymentId).signum() > 0
        || posting.holdsRefund(tenant, paymentId)
        || cheques.existsByTenantIdAndClearedPaymentId(tenant, paymentId))
      throw CollectionRuleException.conflict(
          "CHEQUE_PAYMENT_ALREADY_USED",
          "This payment is already matched or used by another instrument");
    rows.forEach(row -> requireInstrumentTarget(row, cheque.getPartnerId(), cheque.getCurrency()));
    matchIds.addAll(
        posting.postChequeBatch(
            paymentId,
            cheque.getId(),
            rows,
            applied.stream().map(CollectionChequeApplication::getAmount).toList(),
            request.effectiveDate(),
            request.occurredAt(),
            actor,
            request.reason()));
    return paymentId;
  }

  private void reverseClearing(
      CollectionCheque cheque,
      CollectionRequests.Transition request,
      UUID actor,
      List<UUID> matchIds) {
    UUID tenant = TenantContext.requireTenantId();
    var payment = posting.payment(cheque.getClearedPaymentId());
    var activeMatches =
        matches.findByTenantIdAndPaymentIdAndReversedAtIsNull(tenant, payment.getId());
    if (activeMatches.stream().anyMatch(value -> !cheque.getId().equals(value.getChequeId())))
      throw CollectionRuleException.conflict(
          "CHEQUE_CLEARING_HAS_OTHER_BINDINGS",
          "Explicitly reconcile other collection matches before reversing this clearing");
    periods.assertPostingAllowed(tenant, request.effectiveDate());
    for (var match : activeMatches) {
      posting.reverseMatch(
          match, request.effectiveDate(), request.occurredAt(), actor, request.reason());
      matchIds.add(match.getId());
    }
    // The payment is cancelled on a dated reversal, never voided in place, so a clearing in a
    // closed period can still be dishonoured on an open date. The reversal also releases the
    // payment's invoice allocations.
    if (payment.getStatus() != PaymentStatus.VOIDED && !payment.isReversed())
      payments.reverseReleasedInstrument(
          payment.getId(),
          new ReversePaymentRequest(request.effectiveDate(), request.reason(), null),
          actor);
  }

  private void requireVersions(
      List<CollectionObligation> rows, List<CollectionRequests.Version> versions, UUID actor) {
    if (versions == null || versions.size() != rows.size())
      throw CollectionRuleException.invalid("All affected obligation versions are required");
    Map<UUID, CollectionRequests.Version> byId;
    try {
      byId =
          versions.stream()
              .collect(
                  Collectors.toMap(CollectionRequests.Version::obligationId, Function.identity()));
    } catch (IllegalStateException duplicate) {
      throw CollectionRuleException.invalid("Duplicate obligation version");
    }
    posting.freshAll(rows);
    var reach = access.reach(actor, "write");
    rows.forEach(
        row -> {
          if (!reach.permits(row.getTenantId(), row.getCreatedBy(), row.getResponsibleUserId()))
            throw new NotFoundException("Collection obligation not found");
          var version = byId.get(row.getId());
          if (version == null)
            throw CollectionRuleException.invalid("Obligation versions do not match this cheque");
          commands.version(version.expectedVersion(), row.getVersion());
        });
  }

  /**
   * Undoing a clearing undoes recorded money: the matches and the payment are reversed. That takes
   * the reverse grant over every linked row, exactly as reversing a match or cancelling a refund
   * does; the write grant alone must not reach the same effect through the instrument.
   */
  private void requireClearingReversal(
      CollectionCheque cheque, ChequeAction action, List<CollectionObligation> rows, UUID actor) {
    if (!cheque.getState().reversesClearing(action)) return;
    var reach = access.reach(actor, "reverse");
    if (reach.scope() == null)
      throw new AccessDeniedException("Missing finance permission: reverse");
    boolean hidden =
        rows.stream()
            .anyMatch(
                row ->
                    !reach.permits(
                        row.getTenantId(), row.getCreatedBy(), row.getResponsibleUserId()));
    if (hidden) throw new NotFoundException("Cheque not found");
  }

  private void requireChequeWrite(CollectionCheque cheque, UUID actor) {
    var reach = access.reach(actor, "write");
    var applied = applications.findByTenantIdAndChequeId(cheque.getTenantId(), cheque.getId());
    var linked =
        obligations.findByTenantIdAndIdIn(
            cheque.getTenantId(),
            applied.stream().map(CollectionChequeApplication::getObligationId).toList());
    if (linked.size() != applied.size()
        || (applied.isEmpty() && !reach.permits(cheque.getTenantId(), cheque.getCreatedBy(), null))
        || linked.stream()
            .anyMatch(
                row ->
                    !reach.permits(
                        row.getTenantId(), row.getCreatedBy(), row.getResponsibleUserId())))
      throw new NotFoundException("Cheque not found");
  }

  private void requireInstrumentTarget(CollectionObligation row, UUID partner, String currency) {
    if (row.isCancelled()
        || row.getMethod() != CollectionTypes.Method.CHEQUE
        || !row.getPartnerId().equals(partner)
        || !row.getCurrency().equals(currency))
      throw CollectionRuleException.conflict(
          "CHEQUE_APPLICATION_INCOMPATIBLE",
          "Choose an active cheque expectation for the same partner and currency");
  }
}
