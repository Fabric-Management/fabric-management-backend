package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.approval.ApprovalPort;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.orderintake.domain.CustomerProductRequest;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerProductRequestRepository;
import com.fabricmanagement.sales.salesorder.domain.CustomerApproval;
import com.fabricmanagement.sales.salesorder.domain.CustomerApprovalStatus;
import com.fabricmanagement.sales.salesorder.domain.DeliveryProposal;
import com.fabricmanagement.sales.salesorder.domain.OrderCurrencyTotal;
import com.fabricmanagement.sales.salesorder.domain.OrderCurrencyTotals;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.OrderVersion;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionContent;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionKind;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.CustomerApprovalDtos;
import com.fabricmanagement.sales.salesorder.infra.repository.CustomerApprovalRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.DeliveryProposalRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderVersionRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sales sends the order to the customer: the draft's details for information, or the evaluated
 * order for approval. A version for approval is frozen first; if the tenant's approval policy asks
 * for it, a manager or finance approves that exact version before anything reaches the customer.
 * The link then goes to the order's contact, valid for a set time but never beyond planning's
 * proposal. Every step is checked here: the permission at the endpoint, access to the order, the
 * flow stage and what the version rests on.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerApprovalService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final SalesOrderRepository orders;
  private final SalesOrderLineRepository lines;
  private final DeliveryProposalRepository proposals;
  private final OrderVersionRepository versions;
  private final CustomerApprovalRepository approvals;
  private final CustomerProductRequestRepository requests;
  private final SalesOrderAccessPolicy accessPolicy;
  private final SpELPermissionEvaluator permissions;
  private final TradingPartnerService partners;
  private final OrderVersionSnapshotter snapshotter;
  private final CustomerApprovalMailer mailer;
  private final ApprovalPort approvalPort;
  private final OrderIntakeHooks intake;
  private final OrderApprovalInvalidator invalidator;
  private final OrderFlowRecorder flow;
  private final Clock clock;

  // ── Sales ──────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public CustomerApprovalDtos.State state(UUID orderId, UUID actor) {
    return state(readable(orderId, actor), actor);
  }

  /**
   * Sends the draft's details to the order's contact for information. Nothing can be approved from
   * it; it is frozen as a version so what the customer was told is kept.
   */
  @Transactional
  public CustomerApprovalDtos.State sendInformation(UUID orderId, UUID actor) {
    SalesOrder order = writableLocked(orderId, actor);
    String reason = informationBlock(order);
    if (reason != null) {
      throw refusal(reason, order);
    }
    UUID tenantId = TenantContext.requireTenantId();
    OrderVersionSnapshotter.Snapshot snapshot = snapshotter.snapshot(tenantId, order, null);
    versions.save(
        OrderVersion.freeze(
            order.getId(),
            latestVersion(tenantId, order.getId()),
            OrderVersionKind.INFORMATION,
            order.getPlanningRound(),
            order.getPlanningEvaluation(),
            null,
            snapshot.content(),
            snapshot.hash(),
            actor,
            clock.instant()));
    mailer.sendInformation(
        tenantId, order.getContactEmail(), order.getContactName(), snapshot.content());
    return state(order, actor);
  }

  /**
   * Freezes the evaluated order with planning's proposal and asks the customer to approve it. The
   * order must still be confirmable: nothing blocks it, no custom request is open and the proposal
   * is current. Under the tenant's approval policy the version first waits for a manager or
   * finance; otherwise the link goes out now.
   */
  @Transactional
  public CustomerApprovalDtos.State sendForApproval(
      UUID orderId, CustomerApprovalDtos.SendForApproval input, UUID actor) {
    SalesOrder order = writableLocked(orderId, actor);
    UUID tenantId = TenantContext.requireTenantId();
    Instant now = clock.instant();
    Optional<DeliveryProposal> proposal = currentProposal(tenantId, order);
    List<SalesOrderLine> orderLines =
        lines.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(order.getId());
    String reason = approvalBlock(tenantId, order, proposal.orElse(null), orderLines, now);
    if (reason != null) {
      throw refusal(reason, order);
    }
    DeliveryProposal offered = proposal.orElseThrow();
    OrderVersionSnapshotter.Snapshot snapshot = snapshotter.snapshot(tenantId, order, offered);
    OrderVersion version =
        versions.save(
            OrderVersion.freeze(
                order.getId(),
                latestVersion(tenantId, order.getId()),
                OrderVersionKind.APPROVAL,
                order.getPlanningRound(),
                order.getPlanningEvaluation(),
                offered.getId(),
                snapshot.content(),
                snapshot.hash(),
                actor,
                now));
    // A new version answers the customer's earlier change request.
    invalidator.resolveChangeRequests(order.getId());
    CustomerApproval approval =
        CustomerApproval.request(
            version,
            order.getContactName(),
            order.getContactEmail(),
            offered.getValidUntil(),
            input == null ? null : input.linkValidHours(),
            actor,
            now);
    // An earlier version's approval never carries over to this one.
    approvalPort.cancelPending(tenantId, OrderApprovalInvalidator.APPROVAL_ENTITY, order.getId());
    boolean internal =
        approvalPort.requiresApproval(
            tenantId,
            actor,
            OrderApprovalInvalidator.APPROVAL_ENTITY,
            order.getId(),
            OrderCurrencyTotals.of(orderLines).totals().stream()
                .map(OrderCurrencyTotal::grandTotalMoney)
                .toList());
    if (internal) {
      approval.awaitInternalApproval(
          approvalPort
              .pendingRequestId(tenantId, OrderApprovalInvalidator.APPROVAL_ENTITY, order.getId())
              .orElseThrow(
                  () -> new IllegalStateException("The approval policy created no request")));
      approvals.save(approval);
      flow.move(
          order,
          OrderFlowStage.AWAITING_INTERNAL_APPROVAL,
          "Version " + version.getVersionNo() + " waits for the internal approval",
          actor);
    } else {
      sendLink(tenantId, approval, snapshot.content(), null, null, actor);
      flow.move(
          order,
          OrderFlowStage.AWAITING_CUSTOMER_APPROVAL,
          "Version " + version.getVersionNo() + " sent to " + approval.getRecipientEmail(),
          actor);
    }
    return state(order, actor);
  }

  /**
   * Sends a new link for the same version, to the same or a corrected address. The earlier link,
   * code and session stop working; the version and its validity do not change.
   */
  @Transactional
  public CustomerApprovalDtos.State resend(
      UUID orderId, CustomerApprovalDtos.Resend input, UUID actor) {
    SalesOrder order = writableLocked(orderId, actor);
    UUID tenantId = TenantContext.requireTenantId();
    CustomerApproval approval = openApproval(tenantId, order.getId()).orElse(null);
    String reason = resendBlock(order, approval, clock.instant());
    if (reason != null) {
      throw refusal(reason, order);
    }
    OrderVersion version = versionOf(tenantId, approval);
    sendLink(
        tenantId,
        approval,
        version.getContent(),
        input == null ? null : input.recipientName(),
        input == null ? null : input.recipientEmail(),
        actor);
    return state(order, actor);
  }

  /** The customer's change requests sales has not followed up, on orders the user may read. */
  @Transactional(readOnly = true)
  public List<CustomerApprovalDtos.ChangeRequestItem> changeRequests(UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    List<CustomerApprovalDtos.ChangeRequestItem> items = new ArrayList<>();
    for (CustomerApproval approval :
        approvals.findByTenantIdAndStatusAndChangesResolvedAtIsNullOrderByDecidedAtAsc(
            tenantId, CustomerApprovalStatus.CHANGES_REQUESTED)) {
      orders
          .findByTenantIdAndId(tenantId, approval.getSalesOrderId())
          .filter(order -> Boolean.TRUE.equals(order.getIsActive()))
          .filter(order -> accessPolicy.canRead(tenantId, actor, order))
          .ifPresent(
              order ->
                  items.add(
                      new CustomerApprovalDtos.ChangeRequestItem(
                          approval.getId(),
                          order.getId(),
                          order.getOrderNumber(),
                          partners
                              .findById(tenantId, order.getTradingPartnerId())
                              .map(TradingPartnerDto::getDisplayName)
                              .orElse(null),
                          approval.getVersionNo(),
                          approval.getCustomerNote(),
                          approval.getDecidedByName(),
                          approval.getDecidedByEmail(),
                          approval.getDecidedAt())));
    }
    return items;
  }

  // ── The internal approval ──────────────────────────────────────────────

  /**
   * A manager or finance approved the version's approval request. The link goes to the customer
   * now, unless planning's proposal expired meanwhile: then planning has to confirm the date again
   * and the approved version is never sent.
   */
  @Transactional
  public void onInternalApproval(UUID approvalRequestId) {
    UUID tenantId = TenantContext.requireTenantId();
    Optional<CustomerApproval> found =
        approvals.findByTenantIdAndInternalApprovalRequestId(tenantId, approvalRequestId);
    if (found.isEmpty()) {
      log.info("Approval request {} belongs to no order version; ignored", approvalRequestId);
      return;
    }
    SalesOrder order = locked(found.get().getSalesOrderId());
    CustomerApproval approval = found.get();
    if (!approval.awaitsInternalApproval()
        || order.getFlowStage() != OrderFlowStage.AWAITING_INTERNAL_APPROVAL) {
      log.info(
          "Order {} no longer waits for approval request {}; ignored",
          order.getOrderNumber(),
          approvalRequestId);
      return;
    }
    Instant now = clock.instant();
    if (!approval.getProposalValidUntil().isAfter(now)) {
      String reason =
          "Planning's proposal expired while version "
              + approval.getVersionNo()
              + " waited for the internal approval";
      approval.withdraw(reason, SystemUser.ID, now);
      approvals.save(approval);
      order.startNewEvaluation();
      flow.move(order, OrderFlowStage.IN_PLANNING, reason, SystemUser.ID);
      return;
    }
    approval.internallyApproved(now);
    sendLink(
        tenantId, approval, versionOf(tenantId, approval).getContent(), null, null, SystemUser.ID);
    flow.move(
        order,
        OrderFlowStage.AWAITING_CUSTOMER_APPROVAL,
        "Approved internally; version "
            + approval.getVersionNo()
            + " sent to "
            + approval.getRecipientEmail(),
        SystemUser.ID);
  }

  /** A manager or finance declined the version: it is never sent and the order is planned again. */
  @Transactional
  public void onInternalRejection(UUID approvalRequestId, String reason) {
    UUID tenantId = TenantContext.requireTenantId();
    Optional<CustomerApproval> found =
        approvals.findByTenantIdAndInternalApprovalRequestId(tenantId, approvalRequestId);
    if (found.isEmpty()) {
      return;
    }
    SalesOrder order = locked(found.get().getSalesOrderId());
    CustomerApproval approval = found.get();
    if (!approval.awaitsInternalApproval()
        || order.getFlowStage() != OrderFlowStage.AWAITING_INTERNAL_APPROVAL) {
      return;
    }
    approval.internallyRejected(reason, clock.instant());
    approvals.save(approval);
    flow.move(
        order,
        OrderFlowStage.PLANNED,
        OrderFlowRecorder.clip(
            "The internal approval was declined"
                + (reason == null || reason.isBlank() ? "" : ": " + reason.trim())),
        SystemUser.ID);
  }

  // ── Internals ──────────────────────────────────────────────────────────

  private void sendLink(
      UUID tenantId,
      CustomerApproval approval,
      OrderVersionContent content,
      String recipientName,
      String recipientEmail,
      UUID actor) {
    String token = randomHex();
    approval.issueLink(
        OrderVersionSnapshotter.sha256(token),
        recipientName,
        recipientEmail,
        actor,
        clock.instant());
    approvals.save(approval);
    mailer.sendApprovalRequest(
        tenantId,
        approval.getRecipientEmail(),
        approval.getRecipientName(),
        content,
        token,
        approval.getLinkExpiresAt());
  }

  private CustomerApprovalDtos.State state(SalesOrder order, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    Instant now = clock.instant();
    List<CustomerApproval> all =
        approvals.findByTenantIdAndSalesOrderIdOrderByRequestedAtDesc(tenantId, order.getId());
    CustomerApproval open =
        all.stream().filter(value -> !value.getStatus().isClosed()).findFirst().orElse(null);
    String access = accessBlock(tenantId, order, actor);
    List<CustomerApprovalDtos.Capability> actions =
        List.of(
            CustomerApprovalDtos.Capability.of(
                CustomerApprovalDtos.Action.SEND_INFORMATION,
                access != null ? access : informationBlock(order)),
            CustomerApprovalDtos.Capability.of(
                CustomerApprovalDtos.Action.SEND_FOR_APPROVAL,
                access != null
                    ? access
                    : order.getFlowStage() != OrderFlowStage.PLANNED
                        ? OrderFlowService.WRONG_STAGE
                        : approvalBlock(
                            tenantId,
                            order,
                            currentProposal(tenantId, order).orElse(null),
                            lines.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(
                                order.getId()),
                            now)),
            CustomerApprovalDtos.Capability.of(
                CustomerApprovalDtos.Action.RESEND_LINK,
                access != null ? access : resendBlock(order, open, now)));
    return new CustomerApprovalDtos.State(
        order.getId(),
        order.getFlowStage(),
        order.getContactName(),
        order.getContactEmail(),
        all.stream().map(value -> CustomerApprovalDtos.ApprovalView.of(value, now)).toList(),
        versions.findByTenantIdAndSalesOrderIdOrderByVersionNoDesc(tenantId, order.getId()).stream()
            .map(CustomerApprovalDtos.VersionView::of)
            .toList(),
        actions);
  }

  /** Why the user may not send anything about this order, or null. */
  private String accessBlock(UUID tenantId, SalesOrder order, UUID actor) {
    if (!accessPolicy.canWrite(tenantId, actor, order)) {
      return "NO_OBJECT_ACCESS";
    }
    return permissions.can(SecurityContextHolder.getContext().getAuthentication(), "sales", "write")
        ? null
        : "PERMISSION_DENIED";
  }

  /** Why the draft's details cannot be sent now, or null. */
  private static String informationBlock(SalesOrder order) {
    if (order.getStatus() != OrderStatus.DRAFT) {
      return "ORDER_NOT_DRAFT";
    }
    if (order.getFlowStage().awaitsApproval()
        || order.getFlowStage() == OrderFlowStage.CUSTOMER_APPROVED) {
      return OrderFlowService.WRONG_STAGE;
    }
    return hasEmail(order) ? null : "CONTACT_EMAIL_REQUIRED";
  }

  /** Why the evaluated order cannot be sent for approval now, or null. */
  private String approvalBlock(
      UUID tenantId,
      SalesOrder order,
      DeliveryProposal proposal,
      List<SalesOrderLine> orderLines,
      Instant now) {
    if (order.getStatus() != OrderStatus.DRAFT) {
      return "ORDER_NOT_DRAFT";
    }
    if (order.getFlowStage() != OrderFlowStage.PLANNED) {
      return OrderFlowService.WRONG_STAGE;
    }
    if (!hasEmail(order)) {
      return "CONTACT_EMAIL_REQUIRED";
    }
    if (proposal == null) {
      return "PROPOSAL_MISSING";
    }
    if (proposal.predatesEvaluation(order.getPlanningEvaluation())
        || !proposal.appliesTo(order.getDeliveryTerms())) {
      return "PROPOSAL_STALE";
    }
    if (proposal.isExpiredAt(now)) {
      return "PROPOSAL_EXPIRED";
    }
    if (orderLines.isEmpty()) {
      return "NO_LINES";
    }
    boolean openRequests =
        requests
            .findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByRecordedAtAscIdAsc(
                tenantId, order.getId())
            .stream()
            .map(CustomerProductRequest::getStatus)
            .anyMatch(status -> !status.isFinished());
    if (openRequests) {
      return "OPEN_CUSTOM_REQUESTS";
    }
    return intake.confirmationBlockers(order, orderLines).isEmpty() ? null : "NOT_CONFIRMABLE";
  }

  /** Why a new link cannot be sent now, or null. */
  private static String resendBlock(SalesOrder order, CustomerApproval open, Instant now) {
    if (order.getFlowStage() != OrderFlowStage.AWAITING_CUSTOMER_APPROVAL
        || open == null
        || open.getStatus() != CustomerApprovalStatus.SENT) {
      return OrderFlowService.WRONG_STAGE;
    }
    return open.getProposalValidUntil().isAfter(now) ? null : "PROPOSAL_EXPIRED";
  }

  private OrderDomainException refusal(String reason, SalesOrder order) {
    String message =
        switch (reason) {
          case "ORDER_NOT_DRAFT" -> "Order " + order.getOrderNumber() + " is " + order.getStatus();
          case OrderFlowService.WRONG_STAGE ->
              "Order " + order.getOrderNumber() + " is " + order.getFlowStage();
          case "CONTACT_EMAIL_REQUIRED" -> "The order's contact needs an e-mail address";
          case "PROPOSAL_MISSING" -> "Planning has not proposed a date";
          case "PROPOSAL_STALE" ->
              "Planning's proposal no longer applies to the order; planning proposes again";
          case "PROPOSAL_EXPIRED" ->
              "Planning's proposal is no longer valid; planning has to confirm the date again";
          case "NO_LINES" -> "The order has no product lines";
          case "OPEN_CUSTOM_REQUESTS" ->
              "Resolve or close the custom requests before asking the customer to approve";
          case "NOT_CONFIRMABLE" ->
              "The order cannot be confirmed as it stands: "
                  + String.join(
                      "; ",
                      intake.confirmationBlockers(
                          order,
                          lines.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(
                              order.getId())));
          default -> reason;
        };
    return OrderDomainException.stage(reason, message);
  }

  private Optional<DeliveryProposal> currentProposal(UUID tenantId, SalesOrder order) {
    return proposals
        .findFirstByTenantIdAndSalesOrderIdOrderBySequenceDesc(tenantId, order.getId())
        .filter(proposal -> proposal.belongsToRound(order.getPlanningRound()));
  }

  private Optional<CustomerApproval> openApproval(UUID tenantId, UUID orderId) {
    return approvals
        .findByTenantIdAndSalesOrderIdAndStatusIn(
            tenantId,
            orderId,
            java.util.EnumSet.of(
                CustomerApprovalStatus.AWAITING_INTERNAL_APPROVAL, CustomerApprovalStatus.SENT))
        .stream()
        .findFirst();
  }

  private OrderVersion latestVersion(UUID tenantId, UUID orderId) {
    return versions
        .findFirstByTenantIdAndSalesOrderIdOrderByVersionNoDesc(tenantId, orderId)
        .orElse(null);
  }

  private OrderVersion versionOf(UUID tenantId, CustomerApproval approval) {
    return versions
        .findByTenantIdAndId(tenantId, approval.getOrderVersionId())
        .orElseThrow(() -> new IllegalStateException("The approval's version is missing"));
  }

  private SalesOrder readable(UUID orderId, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    return orders
        .findByTenantIdAndId(tenantId, orderId)
        .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
        .filter(value -> accessPolicy.canRead(tenantId, actor, value))
        .orElseThrow(() -> new NotFoundException("Sales order not found: " + orderId));
  }

  private SalesOrder writableLocked(UUID orderId, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrder order = readable(orderId, actor);
    if (!accessPolicy.canWrite(tenantId, actor, order)) {
      throw new AccessDeniedException("You do not have access to update this sales order.");
    }
    return locked(order.getId());
  }

  private SalesOrder locked(UUID orderId) {
    return orders
        .lockByTenantIdAndId(TenantContext.requireTenantId(), orderId)
        .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
        .orElseThrow(() -> new NotFoundException("Sales order not found: " + orderId));
  }

  private static boolean hasEmail(SalesOrder order) {
    return order.getContactEmail() != null && !order.getContactEmail().isBlank();
  }

  static String randomHex() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
