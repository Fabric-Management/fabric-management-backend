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
import java.time.Instant;
import java.time.LocalDate;
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
  private final CollectionChequeOfferRepository offers;
  private final CollectionObligationRepository obligations;
  private final CollectionPaymentMatchRepository matches;
  private final CollectionPostingService posting;
  private final CollectionAccessService access;
  private final PaymentService payments;
  private final FinancialPeriodGuard periods;
  private final CollectionEvents changes;
  private final CollectionChequeDrawerResolver drawers;
  private final CollectionRefundLedger refundLedger;

  public CollectionCommandReceipt receive(CollectionRequests.ReceiveCheque request, UUID actor) {
    commands.validate(request);
    // The finance write grant is checked here, not only through the targets: a cheque held for the
    // customer has none (PAYMENT-PLAN-9 R1, R6).
    access.requireGrant(actor, "write");
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
    // Checked after the replay lookup, so a receipt stored before tagging still replays (R16).
    drawers.requireCustomer(request.partnerId());
    var drawer = drawers.forReceipt(request);
    BigDecimal amount = CollectionAmounts.positive(request.amount(), request.currency());
    posting.freshAll(rows);
    // PAYMENT-PLAN-9 R1: a cheque may be received for the customer without any application.
    var targetIds = rows.stream().map(CollectionObligation::getId).distinct().toList();
    Map<UUID, BigDecimal> received =
        targetIds.isEmpty()
            ? Map.of()
            : applications.received(tenant, targetIds).stream()
                .collect(
                    Collectors.toMap(
                        CollectionPaymentMatchRepository.Balance::getId,
                        CollectionPaymentMatchRepository.Balance::getAmount));
    Map<UUID, BigDecimal> cash =
        targetIds.isEmpty()
            ? Map.of()
            : matches.nonChequeSettledFor(tenant, targetIds).stream()
                .collect(
                    Collectors.toMap(
                        CollectionPaymentMatchRepository.Balance::getId,
                        CollectionPaymentMatchRepository.Balance::getAmount));
    var ids = new HashSet<UUID>();
    var allocations = new ArrayList<ReceiptApplication>();
    BigDecimal applied = BigDecimal.ZERO;
    for (int i = 0; i < rows.size(); i++) {
      var row = rows.get(i);
      var input = request.applications().get(i);
      if (!ids.add(row.getId()))
        throw CollectionRuleException.invalid("Each obligation may appear only once per cheque");
      commands.version(input.expectedVersion(), row.getVersion());
      BigDecimal available =
          row.getAmount()
              .subtract(received.getOrDefault(row.getId(), BigDecimal.ZERO))
              .subtract(cash.getOrDefault(row.getId(), BigDecimal.ZERO))
              .max(BigDecimal.ZERO);
      BigDecimal value =
          validateReceiptApplication(row, request.partnerId(), request.currency(), input.amount());
      if (value.compareTo(available) > 0)
        throw CollectionRuleException.conflict(
            "CHEQUE_OVERAPPLICATION", "Cheque application exceeds the missing instrument amount");
      allocations.add(new ReceiptApplication(row.getId(), value));
      applied = applied.add(value);
    }
    if (applied.compareTo(amount) > 0)
      throw CollectionRuleException.invalid("Applications exceed the cheque amount");
    var evidence =
        new ReceiptEvidence(
            request.partnerId(),
            request.receivedOn(),
            request.occurredAt(),
            actor,
            request.reason(),
            request.evidence());
    var cheque =
        persistReceipt(
            evidence,
            request.currency(),
            amount,
            request.dueOn(),
            request.instrumentReference(),
            drawer,
            allocations,
            null);
    changeReceiptTargets(rows);
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

  CollectionCheque persistReceipt(
      ReceiptEvidence evidence,
      String currency,
      BigDecimal amount,
      LocalDate dueOn,
      String reference,
      CollectionCheque.Drawer drawer,
      List<ReceiptApplication> allocations,
      HandoverPosition position) {
    var cheque =
        cheques.saveAndFlush(
            position == null
                ? CollectionCheque.receive(
                    evidence.partner(),
                    currency,
                    amount,
                    evidence.receivedOn(),
                    dueOn,
                    reference.trim(),
                    evidence.evidence(),
                    drawer)
                : CollectionCheque.receiveInHandover(
                    evidence.partner(),
                    currency,
                    amount,
                    evidence.receivedOn(),
                    dueOn,
                    reference.trim(),
                    evidence.evidence(),
                    drawer,
                    position.id(),
                    position.ordinal()));
    for (var allocation : allocations) {
      // Applications made at receipt carry the same evidence as the RECEIVE event.
      applications.save(
          CollectionChequeApplication.create(
              cheque.getId(),
              allocation.obligationId(),
              allocation.amount(),
              evidence.receivedOn(),
              evidence.occurredAt(),
              evidence.actor(),
              evidence.reason()));
    }
    applications.flush();
    events.saveAndFlush(
        CollectionChequeEvent.record(
            cheque,
            "RECEIVE",
            evidence.receivedOn(),
            evidence.occurredAt(),
            evidence.actor(),
            evidence.reason(),
            evidence.evidence()));
    return cheque;
  }

  void changeReceiptTargets(List<CollectionObligation> rows) {
    for (var row : rows) {
      row.financialChange();
      obligations.save(row);
      changes.changed(row.getId());
    }
    obligations.flush();
  }

  BigDecimal validateReceiptApplication(
      CollectionObligation row, UUID partner, String currency, BigDecimal requested) {
    requireInstrumentTarget(row, partner, currency);
    return CollectionAmounts.positive(requested, currency);
  }

  record ReceiptEvidence(
      UUID partner,
      LocalDate receivedOn,
      Instant occurredAt,
      UUID actor,
      String reason,
      String evidence) {}

  record ReceiptApplication(UUID obligationId, BigDecimal amount) {}

  record HandoverPosition(UUID id, int ordinal) {}

  public CollectionCommandReceipt transition(
      UUID chequeId, CollectionRequests.Transition request, UUID actor) {
    commands.validate(request);
    commands.lock();
    UUID tenant = TenantContext.requireTenantId();
    var cheque =
        cheques
            .findByTenantIdAndId(tenant, chequeId)
            .orElseThrow(() -> new NotFoundException("Cheque not found"));
    // Current coverage only: a reversed application no longer ties the cheque to its obligation.
    var applied = applications.active(tenant, chequeId);
    var linked = requireChequeWrite(cheque, applied, actor);
    boolean drawerOnly = request.action() == ChequeAction.CORRECT_DRAWER;
    if (drawerOnly && linked.stream().anyMatch(row -> !Boolean.TRUE.equals(row.getIsActive())))
      throw new NotFoundException("Cheque not found");
    var rows =
        drawerOnly
            ? List.<CollectionObligation>of()
            : posting.writableBatch(obligationIds(applied), actor);
    requireClearingReversal(cheque, request.action(), rows, actor);
    String hash = commands.hash("CHEQUE_TRANSITION", chequeId, request);
    var replay = commands.replay(request.operationId(), actor, hash);
    if (replay.isPresent()) return replay.get();
    // PAYMENT-PLAN-9 R2: a cheque without (or with only reversed) applications has the same life;
    // only the cheque changes, and clearing records one payment with no match.
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
    if (!drawerOnly && request.drawerCorrection() != null)
      throw CollectionRuleException.invalid("Drawer correction is valid only for CORRECT_DRAWER");
    var matchIds = new ArrayList<UUID>();
    if (drawerOnly) {
      if (request.drawerCorrection() == null)
        throw CollectionRuleException.invalid("Corrected drawer classification is required");
      if (request.obligations() != null && !request.obligations().isEmpty())
        throw CollectionRuleException.invalid("Drawer correction takes no obligation versions");
      cheque.correctDrawer(
          drawers.forDrawerCorrection(cheque, request.drawerCorrection()), request.evidence());
    } else if (request.action() == ChequeAction.CLEAR) {
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
      // The floor is what the cheque currently covers; a reversed application no longer counts.
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
          request.evidence(),
          drawers.forCorrection(cheque, correction));
    } else {
      if (cheque.getState().reversesClearing(request.action())) {
        requireVersions(rows, request.obligations(), actor);
        reverseClearing(cheque, request, actor, matchIds);
      }
      cheque.transition(request.action(), null, request.evidence());
    }
    cheques.saveAndFlush(cheque);
    if (!drawerOnly) {
      // Financial transitions change instrument facts relevant to the expectation's version.
      rows.forEach(
          row -> {
            row.financialChange();
            obligations.save(row);
            changes.changed(row.getId());
          });
      obligations.flush();
    }
    events.saveAndFlush(
        CollectionChequeEvent.record(
            cheque,
            request.action().name(),
            request.effectiveDate(),
            request.occurredAt(),
            actor,
            request.reason(),
            request.evidence()));
    if (!drawerOnly) notifyOfferChange(tenant, chequeId);
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

  /**
   * PAYMENT-PLAN-9 R3: applies part of a held cheque to cheque instalments. Before clearing the
   * cheque itself is allocated; after clearing its payment is (decision 5), so a cleared or final
   * cheque is refused. Nothing is ever allocated without this explicit command (decision 4).
   */
  public CollectionCommandReceipt allocate(
      UUID chequeId, CollectionRequests.AllocateCheque request, UUID actor) {
    commands.validate(request);
    access.requireGrant(actor, "write");
    commands.lock();
    UUID tenant = TenantContext.requireTenantId();
    var cheque =
        cheques
            .findByTenantIdAndId(tenant, chequeId)
            .orElseThrow(() -> new NotFoundException("Cheque not found"));
    // The replay comes before the access checks that read the cheque's current applications: a
    // retried request returns its own result even when it was the command that changed them. The
    // stored receipt answers only the same actor with the same request.
    String hash = commands.hash("ALLOCATE_CHEQUE", chequeId, request);
    var replay = commands.replay(request.operationId(), actor, hash);
    if (replay.isPresent()) return replay.get();
    var applied = applications.active(tenant, chequeId);
    requireChequeWrite(cheque, applied, actor);
    var rows =
        posting.writableBatch(
            request.applications().stream()
                .map(CollectionRequests.Application::obligationId)
                .toList(),
            actor);
    commands.version(request.expectedVersion(), cheque.getVersion());
    commands.time(request.effectiveDate(), request.occurredAt());
    requireApplicationChange(cheque, tenant, request.effectiveDate());
    posting.freshAll(rows);
    var targetIds = rows.stream().map(CollectionObligation::getId).distinct().toList();
    var received = balances(applications.received(tenant, targetIds));
    var cash = balances(matches.nonChequeSettledFor(tenant, targetIds));
    BigDecimal unapplied = cheque.getAmount().subtract(total(applied));
    var seen = new HashSet<UUID>();
    BigDecimal requested = BigDecimal.ZERO;
    for (int i = 0; i < rows.size(); i++) {
      var row = rows.get(i);
      var input = request.applications().get(i);
      if (!seen.add(row.getId()))
        throw CollectionRuleException.invalid("Each obligation may appear only once per request");
      commands.version(input.expectedVersion(), row.getVersion());
      requireInstrumentTarget(row, cheque.getPartnerId(), cheque.getCurrency());
      BigDecimal value = CollectionAmounts.positive(input.amount(), row.getCurrency());
      if (value.compareTo(
              row.getAmount()
                  .subtract(received.getOrDefault(row.getId(), BigDecimal.ZERO))
                  .subtract(cash.getOrDefault(row.getId(), BigDecimal.ZERO))
                  .max(BigDecimal.ZERO))
          > 0)
        throw CollectionRuleException.conflict(
            "CHEQUE_OVERAPPLICATION", "Cheque application exceeds the missing instrument amount");
      requested = requested.add(value);
    }
    if (requested.compareTo(unapplied) > 0)
      throw CollectionRuleException.conflict(
          "CHEQUE_OVERAPPLICATION", "Applications exceed the cheque's unapplied amount");
    var created = new ArrayList<CollectionChequeApplication>();
    for (int i = 0; i < rows.size(); i++) {
      var row = rows.get(i);
      created.add(
          applications.save(
              CollectionChequeApplication.create(
                  cheque.getId(),
                  row.getId(),
                  CollectionAmounts.positive(
                      request.applications().get(i).amount(), cheque.getCurrency()),
                  request.effectiveDate(),
                  request.occurredAt(),
                  actor,
                  request.reason())));
      row.financialChange();
      obligations.save(row);
      changes.changed(row.getId());
    }
    applications.flush();
    obligations.flush();
    for (var application : created) {
      cheque.recordApplicationChange(request.evidence());
      events.save(
          CollectionChequeEvent.application(
              cheque,
              "APPLY",
              application.getId(),
              request.effectiveDate(),
              request.occurredAt(),
              actor,
              request.reason(),
              request.evidence()));
    }
    cheques.saveAndFlush(cheque);
    events.flush();
    notifyOfferChange(tenant, chequeId);
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

  /**
   * PAYMENT-PLAN-9 R3a: reverses one whole application of a held cheque that was applied wrongly.
   * Its coverage is released and returns to the cheque's unapplied amount; the application and its
   * reversal stay as evidence. The cheque keeps its amount and state: nothing is returned, received
   * again or paid. Use {@link #allocate} for the corrected amount or target.
   */
  public CollectionCommandReceipt reverseApplication(
      UUID chequeId,
      UUID applicationId,
      CollectionRequests.ReverseChequeApplication request,
      UUID actor) {
    commands.validate(request);
    access.requireGrant(actor, "write");
    commands.lock();
    UUID tenant = TenantContext.requireTenantId();
    // One answer for a missing cheque and for an application that is not this cheque's, so the
    // reply does not reveal which application ids belong to a cheque the actor cannot reach.
    var cheque =
        cheques
            .findByTenantIdAndId(tenant, chequeId)
            .orElseThrow(() -> new NotFoundException("Cheque application not found"));
    var application =
        applications
            .findByTenantIdAndId(tenant, applicationId)
            .filter(value -> value.getChequeId().equals(chequeId))
            .orElseThrow(() -> new NotFoundException("Cheque application not found"));
    // Replay before the access checks that read current applications: once this reversal removed
    // the cheque's last application, a retry must still return its result (D18).
    String hash = commands.hash("REVERSE_CHEQUE_APPLICATION", applicationId, request);
    var replay = commands.replay(request.operationId(), actor, hash);
    if (replay.isPresent()) return replay.get();
    try {
      requireChequeWrite(cheque, applications.active(tenant, chequeId), actor);
    } catch (NotFoundException hidden) {
      throw new NotFoundException("Cheque application not found");
    }
    var row = posting.writableBatch(List.of(application.getObligationId()), actor).getFirst();
    commands.version(request.expectedVersion(), cheque.getVersion());
    commands.version(request.expectedObligationVersion(), row.getVersion());
    commands.time(request.effectiveDate(), request.occurredAt());
    requireApplicationChange(cheque, tenant, request.effectiveDate());
    // Recorded on the wall clock, like the application's own created_at (S1 carry-over).
    application.reverse(
        request.effectiveDate(), request.occurredAt(), Instant.now(), actor, request.reason());
    applications.saveAndFlush(application);
    row.financialChange();
    obligations.saveAndFlush(row);
    changes.changed(row.getId());
    cheque.recordApplicationChange(request.evidence());
    cheques.saveAndFlush(cheque);
    events.saveAndFlush(
        CollectionChequeEvent.application(
            cheque,
            "REVERSE_APPLICATION",
            applicationId,
            request.effectiveDate(),
            request.occurredAt(),
            actor,
            request.reason(),
            request.evidence()));
    notifyOfferChange(tenant, chequeId);
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

  /**
   * Applications change only while the cheque is held (decision 5, D7, D19), and their events keep
   * the cheque's history in order: an application change cannot precede the receipt or a state
   * event already recorded, so the state it carries is the state on its date.
   */
  private void requireApplicationChange(CollectionCheque cheque, UUID tenant, LocalDate effective) {
    if (cheque.getState() != CollectionTypes.ChequeState.RECEIVED
        && cheque.getState() != CollectionTypes.ChequeState.PRESENTED)
      throw CollectionRuleException.conflict(
          "CHEQUE_APPLICATION_NOT_ALLOWED",
          "Applications can change only while the cheque is held; after clearing, its payment is"
              + " allocated instead");
    if (effective.isBefore(cheque.getReceivedOn()))
      throw CollectionRuleException.invalid("Cheque event cannot precede receipt");
    var latestEffective = events.latestEffectiveDate(tenant, cheque.getId());
    if (latestEffective != null && effective.isBefore(latestEffective))
      throw CollectionRuleException.invalid(
          "A cheque application cannot precede its recorded state history");
  }

  private static BigDecimal total(List<CollectionChequeApplication> applied) {
    return applied.stream()
        .map(CollectionChequeApplication::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static Map<UUID, BigDecimal> balances(
      List<CollectionPaymentMatchRepository.Balance> values) {
    return values.stream()
        .collect(
            Collectors.toMap(
                CollectionPaymentMatchRepository.Balance::getId,
                CollectionPaymentMatchRepository.Balance::getAmount));
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
    // The clearing cannot precede its money, whether or not a match is posted.
    if (request.effectiveDate().isBefore(payment.getPaymentDate()))
      throw CollectionRuleException.invalid("A clearing cannot precede the payment date");
    // The whole amount becomes one payment; the unapplied part stays on it, unmatched (R2).
    if (rows.isEmpty()) return paymentId;
    // One match per obligation for everything the cheque's active applications give it.
    var perObligation = amountsByObligation(applied);
    matchIds.addAll(
        posting.postChequeBatch(
            paymentId,
            cheque.getId(),
            rows,
            rows.stream().map(row -> perObligation.get(row.getId())).toList(),
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
    // Since a cleared cheque's remainder can be matched later (PAYMENT-PLAN-9 R4), a refund on its
    // payment may belong to an obligation the cheque never covered; undoing the clearing would
    // cancel
    // it without that obligation's access or version check, so it is refused like another match.
    var covered = obligationIds(applications.active(tenant, cheque.getId()));
    if (activeMatches.stream().anyMatch(value -> !cheque.getId().equals(value.getChequeId()))
        || refundLedger.holdingObligations(tenant, payment.getId()).stream()
            .anyMatch(obligation -> !covered.contains(obligation)))
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
    // A cheque that covers no obligation has no versions to check, and must be sent none.
    if (rows.isEmpty()) {
      if (versions != null && !versions.isEmpty())
        throw CollectionRuleException.invalid("Obligation versions do not match this cheque");
      return;
    }
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
    // A cheque that covers no obligation is reached through its creator, as for read and write
    // (R6).
    boolean hidden =
        rows.isEmpty()
            ? !reach.permits(cheque.getTenantId(), cheque.getCreatedBy(), null)
            : rows.stream()
                .anyMatch(
                    row ->
                        !reach.permits(
                            row.getTenantId(), row.getCreatedBy(), row.getResponsibleUserId()));
    if (hidden) throw new NotFoundException("Cheque not found");
  }

  /**
   * The obligations a cheque currently covers, each once and in the order first applied. Several
   * active applications of one cheque may name the same obligation; access and versions are checked
   * once per obligation.
   */
  static List<UUID> obligationIds(List<CollectionChequeApplication> applied) {
    return applied.stream().map(CollectionChequeApplication::getObligationId).distinct().toList();
  }

  /** The active applied amount per obligation, summed over the cheque's rows for it. */
  static Map<UUID, BigDecimal> amountsByObligation(List<CollectionChequeApplication> applied) {
    return applied.stream()
        .collect(
            Collectors.groupingBy(
                CollectionChequeApplication::getObligationId,
                java.util.LinkedHashMap::new,
                Collectors.reducing(
                    BigDecimal.ZERO, CollectionChequeApplication::getAmount, BigDecimal::add)));
  }

  private void notifyOfferChange(UUID tenant, UUID chequeId) {
    offers.find(tenant, chequeId).ifPresent(offer -> changes.changed(offer.obligationId()));
  }

  private List<CollectionObligation> requireChequeWrite(
      CollectionCheque cheque, List<CollectionChequeApplication> applied, UUID actor) {
    var reach = access.reach(actor, "write");
    var ids = obligationIds(applied);
    var linked =
        ids.isEmpty()
            ? List.<CollectionObligation>of()
            : obligations.findByTenantIdAndIdIn(cheque.getTenantId(), ids);
    // A missing obligation fails closed: the distinct count must match what was loaded.
    if (linked.size() != ids.size()
        || (ids.isEmpty() && !reach.permits(cheque.getTenantId(), cheque.getCreatedBy(), null))
        || linked.stream()
            .anyMatch(
                row ->
                    !reach.permits(
                        row.getTenantId(), row.getCreatedBy(), row.getResponsibleUserId())))
      throw new NotFoundException("Cheque not found");
    return linked;
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
