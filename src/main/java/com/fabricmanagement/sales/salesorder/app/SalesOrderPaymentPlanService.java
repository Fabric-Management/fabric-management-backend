package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.serialization.CanonicalJsonFingerprint;
import com.fabricmanagement.common.infrastructure.tenant.TenantBusinessCalendarPort;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.CustomerApproval;
import com.fabricmanagement.sales.salesorder.domain.CustomerApprovalStatus;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.OrderVersion;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.OrderPaymentPlan;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.OrderPaymentPlanRevision;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.OrderPaymentPlanSettlementAck;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanContent;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanInput;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.State;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.Capability;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.Revision;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.Save;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.Submit;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.View;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderFlowEventRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderPaymentPlanRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderPaymentPlanRevisionRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderPaymentPlanSettlementAckRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The order row is always the first lock; plan changes never bypass customer agreement. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SalesOrderPaymentPlanService {
  /** Said to the sender when the customer's approval can no longer be agreed over recorded cash. */
  static final String UNACKNOWLEDGED_REDUCTION =
      "Cash was recorded after this revision was submitted, so it now lowers obligations"
          + " below cash the sender did not acknowledge; submit the revision again";

  private final PaymentPlanStore store;
  private final OrderPaymentPlanRepository plans;
  private final OrderPaymentPlanRevisionRepository revisions;
  private final SalesOrderEditAccess access;
  private final SalesOrderRevision orderRevision;
  private final OrderApprovalInvalidator invalidator;
  private final EntityManager entityManager;
  private final DomainEventPublisher events;
  private final Clock clock;
  private final TenantBusinessCalendarPort calendar;
  private final Validator validator;
  private final OrderFlowEventRepository flowEvents;
  private final PaymentPlanSettlementGuard settlement;
  private final OrderPaymentPlanSettlementAckRepository acknowledgements;

  @Transactional(readOnly = true)
  public View read(UUID orderId, UUID actor) {
    access.requireReadable(orderId, actor);
    return view(orderId, actor);
  }

  private View view(UUID orderId, UUID actor) {
    var order = entityManager.find(SalesOrder.class, orderId);
    var found = store.plan(orderId);
    var root = found.orElse(null);
    var draft = root == null ? null : store.revision(root, root.getCurrentDraftRevisionId());
    var agreed =
        root == null || root.getActiveAgreedRevisionId() == null
            ? null
            : store.revision(root, root.getActiveAgreedRevisionId());
    boolean writable;
    try {
      access.requireWritable(orderId, actor);
      writable = true;
    } catch (org.springframework.security.access.AccessDeniedException denied) {
      writable = false;
    }
    String reason =
        !writable
            ? "PERMISSION_DENIED"
            : !editable(order)
                ? "WRONG_STATUS"
                : root != null && root.isCancelled() ? "PAYMENT_PLAN_CANCELLED" : null;
    String submitReason =
        !writable
            ? "PERMISSION_DENIED"
            : !submittable(order)
                ? "WRONG_STATUS"
                : root != null && root.isCancelled()
                    ? "PAYMENT_PLAN_CANCELLED"
                    : draft == null
                        ? "PAYMENT_PLAN_MISSING"
                        : draft.getState() != State.DRAFT && draft.getState() != State.PROPOSED
                            ? "PAYMENT_PLAN_REVISION_CLOSED"
                            : store.stale(order, draft)
                                ? "PAYMENT_PLAN_STALE"
                                : !draft.getValidationIssues().isEmpty()
                                    ? "PAYMENT_PLAN_INCOMPLETE"
                                    : draft.getState() == State.DRAFT
                                            && !shortfalls(orderId, root, draft).isEmpty()
                                        ? "PAYMENT_PLAN_REDUCES_SETTLED"
                                        : null;
    return new View(
        orderId,
        order.getVersion(),
        root == null ? null : root.getId(),
        root == null ? 0 : root.getVersion(),
        root != null && root.isCancelled(),
        draft == null ? null : store.dto(draft),
        agreed == null ? null : store.dto(agreed),
        draft != null && store.stale(order, draft),
        List.of(
            new Capability("SAVE_REVISION", reason == null, reason, null, false),
            new Capability(
                "SUBMIT_FOR_APPROVAL",
                submitReason == null,
                submitReason,
                "CUSTOMER_APPROVAL",
                true)));
  }

  @Transactional(readOnly = true)
  public Page<Revision> history(UUID orderId, UUID actor, Pageable pageable) {
    access.requireReadable(orderId, actor);
    var plan = store.plan(orderId);
    if (plan.isEmpty()) return Page.empty(pageable);
    var page =
        revisions.findByTenantIdAndPlanId(
            TenantContext.requireTenantId(),
            plan.get().getId(),
            PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), 100),
                Sort.by("revisionNo").descending()));
    var content = store.contents(page.getContent());
    return page.map(value -> store.dto(value, content.get(value.getId())));
  }

  public void create(SalesOrder order, PaymentPlanInput input, UUID operation, UUID actor) {
    if (input == null) return;
    validate(input);
    var root = plans.save(OrderPaymentPlan.create(order.getId()));
    var initial =
        append(
            order,
            root,
            input,
            operation == null ? UUID.randomUUID() : operation,
            CanonicalJsonFingerprint.of(input),
            actor,
            "Initial payment plan");
    entityManager.flush();
    initial.resultOrderVersion(order.getVersion());
  }

  /** Creation already authorised the write; do not introduce a sales:read requirement on create. */
  View createdView(SalesOrder order) {
    var root = store.plan(order.getId()).orElseThrow();
    var draft = store.revision(root, root.getCurrentDraftRevisionId());
    return new View(
        order.getId(),
        order.getVersion(),
        root.getId(),
        root.getVersion(),
        false,
        store.dto(draft),
        null,
        store.stale(order, draft),
        List.of());
  }

  public Revision save(UUID orderId, Save request, UUID actor) {
    validate(request);
    var order = access.requireWritable(orderId, actor);
    orderRevision.lockFresh(order);
    access.requireWritable(orderId, actor);
    var root = store.plan(orderId).orElse(null);
    if (root != null) entityManager.refresh(root);
    String hash = CanonicalJsonFingerprint.of(request);
    if (root != null) {
      var replay =
          revisions.findByTenantIdAndPlanIdAndOperationId(
              TenantContext.requireTenantId(), root.getId(), request.operationId());
      if (replay.isPresent()) {
        if (!replay.get().getActorId().equals(actor) || !replay.get().getRequestHash().equals(hash))
          throw OrderDomainException.conflict(
              "PAYMENT_PLAN_KEY_REUSED", "This operation key has different content or actor");
        return store.dto(replay.get());
      }
    }
    if (!editable(order) || root != null && root.isCancelled())
      throw OrderDomainException.conflict(
          "PAYMENT_PLAN_CLOSED", "This order cannot change its payment plan");
    if (order.getVersion() != request.expectedOrderVersion().longValue()
        || (root == null ? 0 : root.getVersion()) != request.expectedPlanVersion().longValue())
      throw OrderDomainException.conflict(
              "PAYMENT_PLAN_VERSION_CONFLICT", "Reload the payment plan before retrying")
          .withDetail("currentOrderVersion", order.getVersion())
          .withDetail("currentPlanVersion", root == null ? 0 : root.getVersion());
    if (root == null) root = plans.save(OrderPaymentPlan.create(orderId));
    var revision =
        append(
            order,
            root,
            request.paymentPlan(),
            request.operationId(),
            hash,
            actor,
            request.changeReason());
    invalidator.withdrawOpen(orderId, "The payment plan changed", actor);
    revision.resultOrderVersion(
        orderRevision.advanceOnce(order, request.expectedOrderVersion().longValue()));
    return store.dto(revision);
  }

  private OrderPaymentPlanRevision append(
      SalesOrder order,
      OrderPaymentPlan root,
      PaymentPlanInput input,
      UUID operation,
      String hash,
      UUID actor,
      String reason) {
    requireRowByRow(root, input);
    var generated = store.generate(order, input);
    // Only identities already belonging to this plan may be carried into a replacement revision.
    Map<UUID, String> known = new HashMap<>();
    if (root.getActiveAgreedRevisionId() != null)
      for (var previous :
          store.content(store.revision(root, root.getActiveAgreedRevisionId())).currencyGroups())
        for (var row : previous.lines()) known.put(row.obligationId(), previous.currency());
    if (root.getCurrentDraftRevisionId() != null)
      for (var previous :
          store.content(store.revision(root, root.getCurrentDraftRevisionId())).currencyGroups())
        for (var row : previous.lines()) known.put(row.obligationId(), previous.currency());
    for (var group : input.currencyGroups())
      if (group.rows() != null)
        for (var row : group.rows())
          if (row.obligationId() != null && !group.currency().equals(known.get(row.obligationId())))
            throw OrderDomainException.rule(
                "PAYMENT_PLAN_FOREIGN_OBLIGATION",
                "An obligation identity must belong to this plan");
    var revision =
        revisions.save(
            OrderPaymentPlanRevision.create(
                root.getId(),
                root.nextRevision(),
                order.getVersion(),
                store.fingerprint(order),
                operation,
                hash,
                actor,
                reason,
                generated.validationIssues()));
    store.storeContent(revision, generated);
    // The replaced draft or proposal stops being a live-looking history row; an agreement stays.
    if (root.getCurrentDraftRevisionId() != null)
      store.revision(root, root.getCurrentDraftRevisionId()).replaced();
    root.draft(revision.getId());
    revision.resultOrderVersion(order.getVersion());
    if (root.getActiveAgreedRevisionId() == null) order.applyPaymentPlanSummary(summary(generated));
    return revision;
  }

  public PaymentPlanContent snapshot(SalesOrder order, boolean requireReady) {
    var root = store.plan(order.getId()).orElse(null);
    if (root == null) return null;
    var revision = store.revision(root, root.getCurrentDraftRevisionId());
    if (requireReady) store.assertReady(order, revision);
    return store.content(revision);
  }

  public void proposed(SalesOrder order, OrderVersion version) {
    if (version.getContent().paymentPlan() == null) return;
    var root = store.plan(order.getId()).orElseThrow();
    var revision = store.revision(root, root.getCurrentDraftRevisionId());
    if (!revision.getId().equals(version.getContent().paymentPlan().revisionId())) throw stale();
    store.assertReady(order, revision);
    revision.propose(version.getId());
  }

  public void assertApprovalCurrent(SalesOrder order, OrderVersion version) {
    var frozen = version.getContent().paymentPlan();
    var root = store.plan(order.getId()).orElse(null);
    if (root == null && frozen == null) return;
    if (root == null
        || root.isCancelled()
        || frozen == null
        || !root.getCurrentDraftRevisionId().equals(frozen.revisionId())) throw stale();
    var revision = store.revision(root, frozen.revisionId());
    if (revision.getState() != State.PROPOSED
        || !version.getId().equals(revision.getApprovalVersionId())) throw stale();
    store.assertReady(order, revision);
  }

  public void agreed(SalesOrder order, OrderVersion version, CustomerApproval approval) {
    if (version.getContent().paymentPlan() == null) return;
    if (order.getStatus() == OrderStatus.DRAFT
        || !editable(order)
        || approval.getStatus() != CustomerApprovalStatus.APPROVED
        || !version.getId().equals(approval.getOrderVersionId()))
      throw OrderDomainException.conflict(
          "PAYMENT_PLAN_NOT_CONFIRMED",
          "Only a fulfilled customer approval activates a payment plan");
    assertApprovalCurrent(order, version);
    var root = store.plan(order.getId()).orElseThrow();
    var revision = store.revision(root, root.getCurrentDraftRevisionId());
    requireAcknowledged(order.getId(), root, revision);
    UUID previous = root.getActiveAgreedRevisionId();
    if (previous != null) store.revision(root, previous).supersede();
    revision.agree(version.getId(), approval.getId(), clock.instant());
    flowEvents
        .findFirstByTenantIdAndSalesOrderIdAndToStageOrderByOccurredAtAsc(
            TenantContext.requireTenantId(), order.getId(), OrderFlowStage.CUSTOMER_APPROVED)
        .ifPresent(
            proof -> root.confirmOn(LocalDate.ofInstant(proof.getOccurredAt(), calendar.zone())));
    revision.activationDate(root.getConfirmationDate());
    root.agree(revision.getId());
    order.applyPaymentPlanSummary(summary(version.getContent().paymentPlan()));
    if (previous == null)
      events.publish(
          new com.fabricmanagement.sales.salesorder.domain.event.SalesOrderPaymentPlanAgreedEvent(
              TenantContext.requireTenantId(),
              order.getId(),
              root.getId(),
              revision.getId(),
              null));
    else
      events.publish(
          new com.fabricmanagement.sales.salesorder.domain.event.SalesOrderPaymentPlanRevisedEvent(
              TenantContext.requireTenantId(),
              order.getId(),
              root.getId(),
              revision.getId(),
              previous));
  }

  public void cancel(SalesOrder order) {
    store
        .plan(order.getId())
        .ifPresent(
            root -> {
              if (root.isCancelled()) return;
              root.cancel();
              store.revision(root, root.getCurrentDraftRevisionId()).cancel(clock.instant());
              if (root.getActiveAgreedRevisionId() != null
                  && !root.getActiveAgreedRevisionId().equals(root.getCurrentDraftRevisionId()))
                store.revision(root, root.getActiveAgreedRevisionId()).cancel(clock.instant());
              events.publish(
                  new com.fabricmanagement.sales.salesorder.domain.event
                      .SalesOrderPaymentPlanCancelledEvent(
                      TenantContext.requireTenantId(), order.getId(), root.getId()));
            });
  }

  public OrderPaymentPlanRevision requireSubmission(
      UUID orderId, UUID revisionId, Submit request, UUID actor) {
    validate(request);
    var order = access.requireWritable(orderId, actor);
    orderRevision.lockFresh(order);
    access.requireWritable(orderId, actor);
    var root = store.plan(orderId).orElseThrow(() -> stale());
    if (root.isCancelled() || !revisionId.equals(root.getCurrentDraftRevisionId())) throw stale();
    var revision = store.revision(root, revisionId);
    // A repeat observes the recorded submission, even after the first submission moved the flow.
    if (revision.getState() == State.PROPOSED || revision.getState() == State.AGREED)
      return revision;
    if (!submittable(order) || revision.getState() != State.DRAFT) throw stale();
    if (order.getVersion() != request.expectedOrderVersion().longValue()
        || root.getVersion() != request.expectedPlanVersion().longValue()) throw stale();
    store.assertReady(order, revision);
    requireSettlementDecision(orderId, root, revision, request, actor);
    return revision;
  }

  /**
   * Lowering an obligation below cash finance has already recorded is allowed, but never silently:
   * the sender has to say so, and the statement is kept (who, when, which obligations) in the same
   * transaction as the submission. The report keeps flagging the line until finance decides.
   */
  private void requireSettlementDecision(
      UUID orderId,
      OrderPaymentPlan root,
      OrderPaymentPlanRevision revision,
      Submit request,
      UUID actor) {
    var lowered = shortfalls(orderId, root, revision);
    if (lowered.isEmpty()) return;
    if (!Boolean.TRUE.equals(request.acknowledgeSettledReduction()))
      throw OrderDomainException.conflict(
              "PAYMENT_PLAN_REDUCES_SETTLED",
              "This revision lowers collected obligations below the cash already recorded")
          .withDetail("obligationCount", lowered.size());
    recordAcknowledgement(orderId, root, revision, actor, lowered);
    log.warn(
        "Payment plan revision {} of order {} submitted over recorded cash by {}: {} obligation(s)",
        revision.getId(),
        orderId,
        actor,
        lowered.size());
  }

  /**
   * Cash can be recorded between the sender's submission and the customer's approval. What the
   * sender acknowledged is what they saw then: an obligation that now sits further below cash than
   * it did, or was not below cash at all, was never accepted and cannot be agreed behind their
   * back.
   */
  void requireAcknowledged(UUID orderId, OrderPaymentPlan root, OrderPaymentPlanRevision revision) {
    long unaccepted = unacknowledged(orderId, root, revision);
    if (unaccepted == 0) return;
    throw OrderDomainException.conflict("PAYMENT_PLAN_REDUCES_SETTLED", UNACKNOWLEDGED_REDUCTION)
        .withDetail("obligationCount", unaccepted);
  }

  /**
   * Asked before the customer's approval is recorded, so a plan that can no longer be agreed
   * becomes a recorded "approved, but not fulfillable" outcome the sender sees, instead of a
   * conflict on the customer's link that nobody but the customer ever reads. Returns why, or null
   * when the plan can be agreed. {@link #agreed} still enforces the same rule as the last word.
   */
  public String unacknowledgedReduction(SalesOrder order, OrderVersion version) {
    if (version.getContent().paymentPlan() == null) return null;
    var root = store.plan(order.getId()).orElse(null);
    if (root == null || root.getCurrentDraftRevisionId() == null) return null;
    var revision = store.revision(root, root.getCurrentDraftRevisionId());
    return unacknowledged(order.getId(), root, revision) == 0 ? null : UNACKNOWLEDGED_REDUCTION;
  }

  private long unacknowledged(
      UUID orderId, OrderPaymentPlan root, OrderPaymentPlanRevision revision) {
    var lowered = shortfalls(orderId, root, revision);
    if (lowered.isEmpty()) return 0;
    var seen =
        acknowledgements
            .findByTenantIdAndRevisionId(TenantContext.requireTenantId(), revision.getId())
            .map(OrderPaymentPlanSettlementAck::getShortfalls)
            .orElse(List.of());
    return lowered.stream()
        .filter(
            now ->
                seen.stream()
                    .noneMatch(
                        then ->
                            then.obligationId().equals(now.obligationId())
                                && then.settled().compareTo(now.settled()) >= 0))
        .count();
  }

  private void recordAcknowledgement(
      UUID orderId,
      OrderPaymentPlan root,
      OrderPaymentPlanRevision revision,
      UUID actor,
      List<PaymentPlanSettlementGuard.Shortfall> lowered) {
    // A revision is submitted once; a repeat never reaches here, but the record must stay unique.
    if (acknowledgements.existsByTenantIdAndRevisionId(
        TenantContext.requireTenantId(), revision.getId())) return;
    acknowledgements.save(
        OrderPaymentPlanSettlementAck.create(
            orderId,
            root.getId(),
            revision.getId(),
            actor,
            clock.instant(),
            lowered.stream()
                .map(
                    shortfall ->
                        new OrderPaymentPlanSettlementAck.Shortfall(
                            shortfall.obligationId(),
                            shortfall.currency(),
                            shortfall.settled(),
                            shortfall.agreed(),
                            shortfall.proposed()))
                .toList()));
  }

  /**
   * An agreed plan's instalments carry the cash recorded against them. Generating a currency from
   * rules again would give every instalment a new identity and strand that cash on cancelled rows,
   * so a currency that has agreed instalments is only ever revised row by row.
   */
  private void requireRowByRow(OrderPaymentPlan root, PaymentPlanInput input) {
    if (root.getActiveAgreedRevisionId() == null) return;
    var agreed = new HashSet<String>();
    var content = store.content(store.revision(root, root.getActiveAgreedRevisionId()));
    for (var group : content.currencyGroups()) agreed.add(group.currency());
    for (var group : input.currencyGroups())
      if (group.rows() == null && agreed.contains(group.currency()))
        throw OrderDomainException.conflict(
            "PAYMENT_PLAN_AGREED_REQUIRES_ROWS",
            "An agreed plan is revised row by row so its instalments keep their identity and the"
                + " cash recorded against them; regenerate from rules only before agreement");
  }

  /** Only an amendment of an agreed plan can fall below cash; a first plan has none recorded. */
  private List<PaymentPlanSettlementGuard.Shortfall> shortfalls(
      UUID orderId, OrderPaymentPlan root, OrderPaymentPlanRevision draft) {
    if (root.getActiveAgreedRevisionId() == null) return List.of();
    return settlement.shortfalls(
        TenantContext.requireTenantId(),
        orderId,
        store.content(store.revision(root, root.getActiveAgreedRevisionId())),
        store.content(draft));
  }

  private static boolean submittable(SalesOrder order) {
    return Boolean.TRUE.equals(order.getIsActive())
        && (order.getStatus() == OrderStatus.DRAFT
            ? order.getFlowStage() == OrderFlowStage.PLANNED
            : editable(order));
  }

  public static boolean editable(SalesOrder order) {
    return Boolean.TRUE.equals(order.getIsActive())
        && order.getStatus() != OrderStatus.CANCELLED
        && order.getStatus() != OrderStatus.REJECTED
        && order.getStatus() != OrderStatus.PENDING_APPROVAL
        && (order.getStatus() != OrderStatus.DRAFT || order.getFlowStage() == OrderFlowStage.DRAFT);
  }

  private void validate(Object value) {
    if (!validator.validate(value).isEmpty())
      throw OrderDomainException.rule("PAYMENT_PLAN_INVALID", "Invalid payment plan request");
  }

  private static OrderDomainException stale() {
    return OrderDomainException.conflict(
        "PAYMENT_PLAN_STALE", "The order or payment plan changed after this version was prepared");
  }

  static String summary(PaymentPlanContent content) {
    String text =
        content.currencyGroups().stream()
            .map(
                group ->
                    group.currency()
                        + " "
                        + (group.baseAmount() == null
                            ? "amount pending"
                            : group.baseAmount().toPlainString())
                        + ": "
                        + group.lines().size()
                        + (group.lines().size() == 1 ? " payment" : " payments"))
            .collect(java.util.stream.Collectors.joining("; "));
    if (text.isEmpty()) text = "Payment plan incomplete";
    return text.substring(0, Math.min(text.length(), 200));
  }
}
