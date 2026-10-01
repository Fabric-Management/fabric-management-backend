package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.sales.common.app.WorkScopeResolver;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.DeliveryProposal;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowEvent;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkAssignment;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkKind;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.OrderFlowDtos;
import com.fabricmanagement.sales.salesorder.dto.OrderWorkDtos;
import com.fabricmanagement.sales.salesorder.infra.repository.DeliveryProposalRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderFlowEventRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sales prepares → planning evaluates → customer approves → processing. Sales hands the draft to
 * planning and may withdraw it; planning evaluates, proposes the date for the delivery term's event
 * with a validity, completes or returns it. Every move is kept. A proposal is not a promise: the
 * customer's approval of the sent version (next slice) makes it the committed date.
 */
@Service
@RequiredArgsConstructor
public class OrderFlowService {

  /** The stages planning works on; sales does not edit the order while it is here. */
  static final String WRONG_STAGE = "WRONG_STAGE";

  /** Unassigned planning is taken while waiting, during the evaluation or awaiting the customer. */
  static final Set<OrderFlowStage> CLAIMABLE =
      EnumSet.of(
          OrderFlowStage.AWAITING_PLANNING,
          OrderFlowStage.IN_PLANNING,
          OrderFlowStage.PLANNED,
          OrderFlowStage.AWAITING_CUSTOMER_APPROVAL);

  private static final Set<OrderWorkDtos.Action> PLANNING_ACTIONS =
      EnumSet.of(
          OrderWorkDtos.Action.CLAIM,
          OrderWorkDtos.Action.ASSIGN,
          OrderWorkDtos.Action.RELEASE,
          OrderWorkDtos.Action.START_EVALUATION,
          OrderWorkDtos.Action.PROPOSE,
          OrderWorkDtos.Action.COMPLETE,
          OrderWorkDtos.Action.RETURN,
          OrderWorkDtos.Action.REOPEN);

  static final Set<OrderFlowStage> WITH_PLANNING =
      EnumSet.of(
          OrderFlowStage.AWAITING_PLANNING, OrderFlowStage.IN_PLANNING, OrderFlowStage.PLANNED);

  private final SalesOrderRepository orders;
  private final SalesOrderLineRepository lines;
  private final DeliveryProposalRepository proposals;
  private final OrderFlowEventRepository events;
  private final SalesOrderAccessPolicy accessPolicy;
  private final TradingPartnerService partners;
  private final com.fabricmanagement.sales.orderintake.infra.repository.IntakeAttachmentRepository
      attachments;
  private final OrderWorkService work;
  private final Clock clock;

  // ── Sales ──────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public OrderFlowDtos.FlowView view(UUID orderId, UUID actor) {
    SalesOrder order = salesReadable(orderId, actor);
    UUID tenantId = TenantContext.requireTenantId();
    return new OrderFlowDtos.FlowView(
        order.getId(),
        order.getFlowStage(),
        currentProposal(order),
        work.viewContext(actor).of(work.find(order.getId(), OrderWorkKind.PLANNING)),
        events.findByTenantIdAndSalesOrderIdOrderByOccurredAtDesc(tenantId, order.getId()).stream()
            .map(OrderFlowService::eventView)
            .toList());
  }

  /** Sales hands the finished draft to planning. */
  @Transactional
  public OrderFlowDtos.FlowView submit(UUID orderId, UUID actor) {
    SalesOrder order = salesWritableLocked(orderId, actor);
    if (!order.getStatus().canEdit()) {
      throw new OrderDomainException(
          "Order " + order.getOrderNumber() + " is " + order.getStatus() + ": not a draft", 409);
    }
    // A new hand-over: proposals from an earlier one are not reused.
    order.startPlanningRound(clock.instant());
    move(order, OrderFlowStage.AWAITING_PLANNING, null, actor);
    // The planning team's queue; a planner takes it explicitly.
    work.route(order.getId(), OrderWorkKind.PLANNING, actor);
    return view(orderId, actor);
  }

  /**
   * Sales takes the order back to the draft, for example to change it. From the moment planning has
   * started, the reason is required: planning's work on it is set aside.
   */
  @Transactional
  public OrderFlowDtos.FlowView withdraw(UUID orderId, String reason, UUID actor) {
    SalesOrder order = salesWritableLocked(orderId, actor);
    if (order.getFlowStage() != OrderFlowStage.AWAITING_PLANNING && isBlank(reason)) {
      throw new OrderDomainException("Say why the order is taken back from planning");
    }
    move(order, OrderFlowStage.DRAFT, reason, actor);
    work.releaseForFlow(order.getId(), OrderWorkKind.PLANNING, reason, actor);
    return view(orderId, actor);
  }

  // ── Planning ───────────────────────────────────────────────────────────

  /**
   * Orders with planning the user may see, oldest first, with what planning needs to evaluate them
   * and the actions the user may take. Unassigned orders appear to those who may take or assign
   * them; assigned ones within the user's scope.
   */
  @Transactional(readOnly = true)
  public List<OrderFlowDtos.QueueItem> planningQueue(UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    List<SalesOrder> queued =
        orders
            .findByTenantIdAndFlowStageInAndIsActiveTrueOrderByCreatedAtAsc(tenantId, WITH_PLANNING)
            .stream()
            // A cancelled or closed order has nothing left to plan.
            .filter(order -> !order.getStatus().isTerminal())
            .toList();
    if (queued.isEmpty()) {
      return List.of();
    }
    WorkScopeResolver.WorkActor viewer = work.actor(actor, false);
    Map<UUID, OrderWorkAssignment> assigned =
        work.assignments(OrderWorkKind.PLANNING, queued.stream().map(SalesOrder::getId).toList());
    queued =
        queued.stream()
            .filter(
                order ->
                    work.access(assigned.get(order.getId()), OrderWorkKind.PLANNING, viewer)
                        .visible())
            .toList();
    if (queued.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = queued.stream().map(SalesOrder::getId).toList();
    Map<UUID, DeliveryProposal> latest = new LinkedHashMap<>();
    proposals
        .findByTenantIdAndSalesOrderIdInOrderBySequenceDesc(tenantId, ids)
        .forEach(proposal -> latest.putIfAbsent(proposal.getSalesOrderId(), proposal));
    Map<UUID, Integer> rounds = new java.util.HashMap<>();
    queued.forEach(order -> rounds.put(order.getId(), order.getPlanningRound()));
    latest
        .values()
        .removeIf(proposal -> !proposal.belongsToRound(rounds.get(proposal.getSalesOrderId())));
    Map<UUID, List<SalesOrderLine>> linesByOrder = new LinkedHashMap<>();
    lines
        .findByTenantIdAndSalesOrderIdInAndIsActiveTrue(tenantId, ids)
        .forEach(
            line ->
                linesByOrder
                    .computeIfAbsent(line.getSalesOrderId(), key -> new java.util.ArrayList<>())
                    .add(line));
    Instant now = clock.instant();
    OrderWorkService.ViewContext views = work.viewContext(actor);
    return queued.stream()
        .map(
            order ->
                toQueueItem(
                    tenantId,
                    order,
                    linesByOrder.getOrDefault(order.getId(), List.of()),
                    latest.get(order.getId()),
                    now,
                    assigned.get(order.getId()),
                    viewer,
                    views))
        .toList();
  }

  private OrderFlowDtos.QueueItem toQueueItem(
      UUID tenantId,
      SalesOrder order,
      List<SalesOrderLine> orderLines,
      DeliveryProposal proposal,
      Instant now,
      OrderWorkAssignment assignment,
      WorkScopeResolver.WorkActor viewer,
      OrderWorkService.ViewContext views) {
    OrderWorkService.WorkAccess access = work.access(assignment, OrderWorkKind.PLANNING, viewer);
    return new OrderFlowDtos.QueueItem(
        order.getId(),
        order.getOrderNumber(),
        partners
            .findById(tenantId, order.getTradingPartnerId())
            .map(TradingPartnerDto::getDisplayName)
            .orElse(null),
        order.getFlowStage(),
        order.getRequestedDeliveryDate(),
        order.getDeliveryTerm(),
        order.getDeliveryPlace(),
        order.getIncotermsVersion(),
        order.getDeliveryTermStatus(),
        order.getDeliveryEvent(),
        orderLines.stream()
            .map(
                line ->
                    new OrderFlowDtos.QueueLine(
                        line.getId(),
                        line.getProductDesc(),
                        line.getRequestedQty(),
                        line.getUnit(),
                        line.getRequestedDeliveryDate()))
            .toList(),
        proposal == null ? null : proposalView(proposal, order, now),
        documentsAddedSince(tenantId, order),
        views.of(assignment),
        actions(order, proposal, now, access));
  }

  /**
   * The planning actions the user may take on the order now: the work permission, scope over the
   * order's planning and the flow stage must all allow it. A null reason means allowed.
   */
  private static List<OrderWorkDtos.Capability> actions(
      SalesOrder order,
      DeliveryProposal proposal,
      Instant now,
      OrderWorkService.WorkAccess access) {
    OrderFlowStage stage = order.getFlowStage();
    if (OrderWorkService.closedFor(OrderWorkKind.PLANNING, order)) {
      // Nothing is planned on a closed order; only releasing the person stays possible.
      return java.util.Arrays.stream(OrderWorkDtos.Action.values())
          .filter(PLANNING_ACTIONS::contains)
          .map(
              action ->
                  OrderWorkDtos.Capability.of(
                      action,
                      action == OrderWorkDtos.Action.RELEASE
                          ? access.releaseReason()
                          : "ORDER_CLOSED"))
          .toList();
    }
    String workReason = access.workReason();
    List<OrderWorkDtos.Capability> result =
        new java.util.ArrayList<>(OrderWorkService.responsibilityActions(access));
    result.set(
        0,
        OrderWorkDtos.Capability.of(
            OrderWorkDtos.Action.CLAIM,
            !CLAIMABLE.contains(stage) ? WRONG_STAGE : access.claimReason()));
    result.add(
        OrderWorkDtos.Capability.of(
            OrderWorkDtos.Action.START_EVALUATION,
            stage != OrderFlowStage.AWAITING_PLANNING ? WRONG_STAGE : workReason));
    result.add(
        OrderWorkDtos.Capability.of(
            OrderWorkDtos.Action.PROPOSE,
            stage != OrderFlowStage.IN_PLANNING ? WRONG_STAGE : workReason));
    String completeReason =
        stage != OrderFlowStage.IN_PLANNING
            ? WRONG_STAGE
            : workReason != null
                ? workReason
                : proposal == null
                    ? "PROPOSAL_MISSING"
                    : proposal.predatesEvaluation(order.getPlanningEvaluation())
                        ? "PROPOSAL_BEFORE_REOPEN"
                        : !proposal.appliesTo(order.getDeliveryTerms())
                            ? "PROPOSAL_STALE"
                            : proposal.isExpiredAt(now) ? "PROPOSAL_EXPIRED" : null;
    result.add(OrderWorkDtos.Capability.of(OrderWorkDtos.Action.COMPLETE, completeReason));
    result.add(
        OrderWorkDtos.Capability.of(
            OrderWorkDtos.Action.RETURN,
            !WITH_PLANNING.contains(stage) ? WRONG_STAGE : workReason));
    result.add(
        OrderWorkDtos.Capability.of(
            OrderWorkDtos.Action.REOPEN,
            stage != OrderFlowStage.PLANNED ? WRONG_STAGE : workReason));
    return List.copyOf(result);
  }

  /**
   * Documents added while the order is with planning do not change the evaluation silently: the
   * queue shows them. A change that needs a new evaluation takes the order back to the draft.
   */
  private long documentsAddedSince(UUID tenantId, SalesOrder order) {
    return order.getPlanningSubmittedAt() == null
        ? 0
        : attachments.countByTenantIdAndSalesOrderIdAndUploadedAtAfter(
            tenantId, order.getId(), order.getPlanningSubmittedAt());
  }

  /**
   * "Take it": the planner becomes responsible for the order's planning. An order waiting for
   * planning starts its evaluation; one whose planner was released mid-way is taken over where it
   * stands. Of two planners taking the same order at once only one succeeds.
   */
  @Transactional
  public OrderFlowDtos.QueueItem claim(UUID orderId, UUID actor) {
    SalesOrder order = planningLocked(orderId);
    work.claim(
        order.getId(),
        OrderWorkKind.PLANNING,
        actor,
        () -> {
          if (!CLAIMABLE.contains(order.getFlowStage())) {
            throw OrderDomainException.stage(
                WRONG_STAGE,
                "Order "
                    + order.getOrderNumber()
                    + " is "
                    + order.getFlowStage()
                    + ": its planning cannot be taken now");
          }
        });
    if (order.getFlowStage() == OrderFlowStage.AWAITING_PLANNING) {
      move(order, OrderFlowStage.IN_PLANNING, null, actor);
    }
    return queueItem(order, actor);
  }

  /** The planner the order was assigned to starts the evaluation. */
  @Transactional
  public OrderFlowDtos.QueueItem startEvaluation(UUID orderId, UUID actor) {
    SalesOrder order = planningWork(orderId, actor);
    requireStage(order, OrderFlowStage.AWAITING_PLANNING);
    move(order, OrderFlowStage.IN_PLANNING, null, actor);
    return queueItem(order, actor);
  }

  /**
   * The responsible planner reopens a finished evaluation with a reason, before changing what the
   * proposal rests on. The proposal stays in the history; completing needs a current one again.
   */
  @Transactional
  public OrderFlowDtos.QueueItem reopen(UUID orderId, String reason, UUID actor) {
    SalesOrder order = planningWork(orderId, actor);
    requireStage(order, OrderFlowStage.PLANNED);
    if (isBlank(reason)) {
      throw new OrderDomainException("Say why the evaluation is reopened");
    }
    // The earlier proposal stays in the history but no longer completes planning.
    order.startNewEvaluation();
    move(order, OrderFlowStage.IN_PLANNING, reason, actor);
    return queueItem(order, actor);
  }

  @Transactional
  public OrderFlowDtos.QueueItem assignPlanner(
      UUID orderId, OrderWorkDtos.Assign input, UUID actor) {
    SalesOrder order = planningLocked(orderId);
    work.assign(order.getId(), OrderWorkKind.PLANNING, input.assigneeId(), input.reason(), actor);
    return queueItem(order, actor);
  }

  @Transactional
  public OrderFlowDtos.QueueItem releasePlanner(UUID orderId, String reason, UUID actor) {
    SalesOrder order = planningLocked(orderId);
    work.release(order.getId(), OrderWorkKind.PLANNING, reason, actor);
    return queueItem(order, actor);
  }

  @Transactional(readOnly = true)
  public List<OrderWorkDtos.Candidate> plannerCandidates(UUID orderId, UUID actor) {
    return work.candidates(orderId, OrderWorkKind.PLANNING, actor);
  }

  @Transactional(readOnly = true)
  public List<OrderWorkDtos.EventView> planningHistory(UUID orderId, UUID actor) {
    // Visible to those who see the order's planning work.
    OrderWorkAssignment assignment = work.find(orderId, OrderWorkKind.PLANNING);
    if (assignment == null
        || !work.access(assignment, OrderWorkKind.PLANNING, work.actor(actor, false)).visible()) {
      throw new NotFoundException("Sales order not found: " + orderId);
    }
    return work.history(orderId, OrderWorkKind.PLANNING);
  }

  /** Planning proposes the date for the delivery term's event, valid until a set time. */
  @Transactional
  public OrderFlowDtos.QueueItem propose(
      UUID orderId, OrderFlowDtos.ProposeDelivery input, UUID actor) {
    SalesOrder order = planningWork(orderId, actor);
    if (order.getFlowStage() != OrderFlowStage.IN_PLANNING) {
      throw new OrderDomainException("Start the evaluation before proposing a date", 409);
    }
    UUID tenantId = TenantContext.requireTenantId();
    DeliveryProposal previous =
        proposals
            .findFirstByTenantIdAndSalesOrderIdOrderBySequenceDesc(tenantId, order.getId())
            .orElse(null);
    Instant now = clock.instant();
    proposals.save(
        DeliveryProposal.propose(
            order.getId(),
            previous,
            order.getPlanningRound(),
            order.getPlanningEvaluation(),
            input.proposedOn(),
            input.validUntil(),
            order.getDeliveryTerms(),
            input.note(),
            actor,
            now,
            LocalDate.now(clock)));
    return queueItem(order, actor);
  }

  /**
   * Planning finishes: the order can go to the customer. It needs a proposal made under the order's
   * current delivery term and place that is still valid; otherwise the date's meaning or basis may
   * have changed and it has to be proposed again.
   */
  @Transactional
  public OrderFlowDtos.QueueItem complete(UUID orderId, UUID actor) {
    SalesOrder order = planningWork(orderId, actor);
    requireStage(order, OrderFlowStage.IN_PLANNING);
    DeliveryProposal proposal =
        proposals
            .findFirstByTenantIdAndSalesOrderIdOrderBySequenceDesc(
                TenantContext.requireTenantId(), order.getId())
            .filter(value -> value.belongsToRound(order.getPlanningRound()))
            .orElseThrow(() -> new OrderDomainException("Propose a date before completing"));
    if (proposal.predatesEvaluation(order.getPlanningEvaluation())) {
      throw OrderDomainException.stage(
          "PROPOSAL_BEFORE_REOPEN",
          "The evaluation was reopened after this proposal; propose or confirm the date again");
    }
    if (!proposal.appliesTo(order.getDeliveryTerms())) {
      throw new OrderDomainException(
          "The delivery term or place changed after the proposal; propose the date again", 409);
    }
    if (proposal.isExpiredAt(clock.instant())) {
      throw new OrderDomainException(
          "The proposal is no longer valid; check the basis and propose again", 409);
    }
    move(order, OrderFlowStage.PLANNED, null, actor);
    return queueItem(order, actor);
  }

  /** Planning returns the order to sales with the reason (missing data, not feasible, ...). */
  @Transactional
  public OrderFlowDtos.QueueItem returnToSales(UUID orderId, String reason, UUID actor) {
    SalesOrder order = planningWork(orderId, actor);
    if (!WITH_PLANNING.contains(order.getFlowStage())) {
      throw new OrderDomainException("The order is not with planning", 409);
    }
    if (isBlank(reason)) {
      throw new OrderDomainException("Say why the order goes back to sales");
    }
    move(order, OrderFlowStage.DRAFT, reason, actor);
    work.releaseForFlow(order.getId(), OrderWorkKind.PLANNING, reason, actor);
    return queueItem(order, actor);
  }

  // ── Internals ──────────────────────────────────────────────────────────

  private void move(SalesOrder order, OrderFlowStage next, String reason, UUID actor) {
    if (reason != null && reason.trim().length() > OrderFlowEvent.MAX_REASON_LENGTH) {
      throw new OrderDomainException("The reason is too long");
    }
    OrderFlowStage from = order.moveFlowTo(next);
    events.save(OrderFlowEvent.of(order.getId(), from, next, reason, actor, clock.instant()));
  }

  /** The order as planning sees it, also after it left the queue (returned to sales). */
  private OrderFlowDtos.QueueItem queueItem(SalesOrder order, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    return toQueueItem(
        tenantId,
        order,
        lines.findByTenantIdAndSalesOrderIdInAndIsActiveTrue(tenantId, List.of(order.getId())),
        proposals
            .findFirstByTenantIdAndSalesOrderIdOrderBySequenceDesc(tenantId, order.getId())
            .filter(value -> value.belongsToRound(order.getPlanningRound()))
            .orElse(null),
        clock.instant(),
        work.find(order.getId(), OrderWorkKind.PLANNING),
        work.actor(actor, false),
        work.viewContext(actor));
  }

  private OrderFlowDtos.ProposalView currentProposal(SalesOrder order) {
    return proposals
        .findFirstByTenantIdAndSalesOrderIdOrderBySequenceDesc(
            TenantContext.requireTenantId(), order.getId())
        .filter(proposal -> proposal.belongsToRound(order.getPlanningRound()))
        .map(proposal -> proposalView(proposal, order, clock.instant()))
        .orElse(null);
  }

  private static OrderFlowDtos.ProposalView proposalView(
      DeliveryProposal proposal, SalesOrder order, Instant now) {
    return new OrderFlowDtos.ProposalView(
        proposal.getId(),
        proposal.getSequence(),
        proposal.getProposedOn(),
        proposal.getValidUntil(),
        proposal.getDeliveryTerm(),
        proposal.getDeliveryPlace(),
        proposal.getIncotermsVersion(),
        proposal.getDeliveryEvent(),
        proposal.getNote(),
        proposal.getProposedBy(),
        proposal.getProposedAt(),
        proposal.appliesTo(order.getDeliveryTerms()),
        proposal.isExpiredAt(now),
        proposal.predatesEvaluation(order.getPlanningEvaluation()));
  }

  private static OrderFlowDtos.EventView eventView(OrderFlowEvent event) {
    return new OrderFlowDtos.EventView(
        event.getFromStage(),
        event.getToStage(),
        event.getReason(),
        event.getActorId(),
        event.getOccurredAt());
  }

  private SalesOrder salesReadable(UUID orderId, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    return orders
        .findByTenantIdAndId(tenantId, orderId)
        .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
        .filter(value -> accessPolicy.canRead(tenantId, actor, value))
        .orElseThrow(() -> new NotFoundException("Sales order not found: " + orderId));
  }

  private SalesOrder salesWritableLocked(UUID orderId, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrder order = salesReadable(orderId, actor);
    if (!accessPolicy.canWrite(tenantId, actor, order)) {
      throw new AccessDeniedException("You do not have access to update this sales order.");
    }
    return orders
        .lockByTenantIdAndId(tenantId, order.getId())
        .orElseThrow(() -> new NotFoundException("Sales order not found: " + orderId));
  }

  /**
   * The order locked for a planning step. The work permission is checked at the endpoint, the scope
   * over the order's planning by {@link OrderWorkService}, the stage by the step.
   */
  private SalesOrder planningWork(UUID orderId, UUID actor) {
    SalesOrder order = planningLocked(orderId);
    work.requireWork(order.getId(), OrderWorkKind.PLANNING, actor);
    if (OrderWorkService.closedFor(OrderWorkKind.PLANNING, order)) {
      // Cancelling keeps the flow stage for the record; nothing is planned on it any more.
      throw OrderDomainException.stage(
          "ORDER_CLOSED", "Order " + order.getOrderNumber() + " is " + order.getStatus());
    }
    return order;
  }

  private static void requireStage(SalesOrder order, OrderFlowStage expected) {
    if (order.getFlowStage() != expected) {
      throw OrderDomainException.stage(
          WRONG_STAGE,
          "Order " + order.getOrderNumber() + " is " + order.getFlowStage() + ", not " + expected);
    }
  }

  private SalesOrder planningLocked(UUID orderId) {
    return orders
        .lockByTenantIdAndId(TenantContext.requireTenantId(), orderId)
        .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
        .orElseThrow(() -> new NotFoundException("Sales order not found: " + orderId));
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
