package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.CommitmentChannel;
import com.fabricmanagement.sales.salesorder.domain.CustomerApproval;
import com.fabricmanagement.sales.salesorder.domain.CustomerApprovalChannel;
import com.fabricmanagement.sales.salesorder.domain.CustomerApprovalStatus;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.OrderVersion;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkKind;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.infra.repository.CustomerApprovalRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderVersionRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The customer's decision on a sent version. Today the only door is the e-mailed link verified with
 * the one-time code sent to the address the authority was granted for. A decision from a customer
 * account is refused until the signed-in person is matched to the approval authority the request
 * was sent under (ADR-0014 OD-13; customer portal, step 6). Approving re-checks, in one
 * transaction, that the approved terms can still be met, then confirms the order, holds its
 * accepted pieces, records the agreed date and marks the delivery term agreed. If the terms can no
 * longer be met, the approval is recorded but the order goes back to planning instead of being
 * confirmed. A decision taken twice gives the same result and creates nothing twice.
 *
 * <p>The caller has set the tenant; this service opens its own transactions.
 */
@Service
@RequiredArgsConstructor
public class CustomerApprovalDecisionService {

  private final CustomerApprovalRepository approvals;
  private final OrderVersionRepository versions;
  private final SalesOrderRepository orders;
  private final SalesOrderLineRepository lines;
  private final OrderIntakeHooks intake;
  private final DeliveryCommitmentService commitments;
  private final SalesOrderService salesOrders;
  private final OrderFlowRecorder flow;
  private final OrderWorkService work;
  private final TransactionTemplate transactions;
  private final EntityManager entityManager;
  private final Clock clock;
  private final ApproverAuthorities approvers;

  /** A decision from a customer account, refused until person and authority are matched. */
  public static final String ACCOUNT_CHANNEL_NOT_AVAILABLE = "ACCOUNT_CHANNEL_NOT_AVAILABLE";

  /** What the decision left: the request's state and its order. */
  public record Outcome(UUID approvalId, UUID orderId, CustomerApprovalStatus status) {}

  /**
   * Checks that the decider may decide on this request (a verified link session); it throws when
   * not. Before it, the representative's approval authority the request was sent under must still
   * be in force, checked under that authority's row lock (ADR-0014 D4, OD-13).
   */
  @FunctionalInterface
  public interface Authority {
    void check(CustomerApproval approval, Instant now);
  }

  /** The customer approves the version. */
  public Outcome approve(UUID approvalId, CustomerApproval.Decider decider, Authority authority) {
    requireLinkChannel(decider);
    try {
      return transactions.execute(status -> approveWithin(approvalId, decider, authority));
    } catch (NotFulfillable failure) {
      // Confirming failed and was rolled back; the customer's approval is still recorded.
      return transactions.execute(
          status -> notFulfillableWithin(approvalId, decider, authority, failure.getMessage()));
    }
  }

  /** The customer asks for changes with a note; the order goes back to sales. */
  public Outcome requestChanges(
      UUID approvalId, CustomerApproval.Decider decider, String note, Authority authority) {
    requireLinkChannel(decider);
    return transactions.execute(
        status -> {
          Instant now = clock.instant();
          Decision decision = load(approvalId);
          CustomerApproval approval = decision.approval();
          if (approval.getStatus() == CustomerApprovalStatus.CHANGES_REQUESTED) {
            return outcome(approval);
          }
          if (approval.getStatus().isDecidedByCustomer()) {
            throw OrderDomainException.stage(
                "APPROVAL_CLOSED", "This version was already approved");
          }
          SalesOrder order = decision.order();
          // A closed or expired request says so; only an open one asks for the session.
          approval.requireOpen(now);
          approvers.holdValid(approval, now);
          authority.check(approval, now);
          requireAwaiting(order);
          approval.changesRequested(decider, note, now);
          approvals.save(approval);
          UUID actor = actorOf(decider);
          flow.move(
              order,
              OrderFlowStage.DRAFT,
              OrderFlowRecorder.clip(
                  "The customer asked for changes to version "
                      + approval.getVersionNo()
                      + " ("
                      + who(decider)
                      + "): "
                      + approval.getCustomerNote()),
              actor);
          work.releaseForFlow(
              order.getId(), OrderWorkKind.PLANNING, "The customer asked for changes", actor);
          return outcome(approval);
        });
  }

  private Outcome approveWithin(
      UUID approvalId, CustomerApproval.Decider decider, Authority authority) {
    Instant now = clock.instant();
    Decision decision = load(approvalId);
    CustomerApproval approval = decision.approval();
    if (approval.getStatus() == CustomerApprovalStatus.APPROVED
        || approval.getStatus() == CustomerApprovalStatus.APPROVED_NOT_FULFILLABLE) {
      return outcome(approval);
    }
    if (approval.getStatus() == CustomerApprovalStatus.CHANGES_REQUESTED) {
      throw OrderDomainException.stage(
          "APPROVAL_CLOSED", "Changes were asked for on this version; it cannot be approved");
    }
    SalesOrder order = decision.order();
    approval.requireOpen(now);
    approvers.holdValid(approval, now);
    authority.check(approval, now);
    requireAwaiting(order);
    OrderVersion version = versionOf(approval);
    if (!version.restsOn(order)) {
      throw OrderDomainException.stage(
          "VERSION_STALE", "The order changed after this version was sent");
    }
    List<SalesOrderLine> orderLines =
        lines.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(order.getId());
    List<String> blockers = intake.confirmationBlockers(order, orderLines);
    if (!blockers.isEmpty()) {
      return notFulfillable(approval, order, decider, String.join("; ", blockers), now);
    }
    UUID actor = actorOf(decider);
    approval.approved(decider, now);
    approvals.save(approval);
    flow.move(
        order,
        OrderFlowStage.CUSTOMER_APPROVED,
        OrderFlowRecorder.clip(
            "The customer approved version " + approval.getVersionNo() + " (" + who(decider) + ")"),
        actor);
    commitments.recordCustomerApproval(
        order,
        version.getContent().proposal().proposedOn(),
        contactOf(decider),
        decider.channel() == CustomerApprovalChannel.CUSTOMER_ACCOUNT
            ? CommitmentChannel.CUSTOMER_ACCOUNT
            : CommitmentChannel.APPROVAL_LINK,
        approval.getVersionNo(),
        actor,
        now);
    try {
      salesOrders.confirmApprovedByCustomer(order);
    } catch (DomainException e) {
      // The pieces or the plan changed in the meantime: nothing of this transaction stays.
      throw new NotFulfillable(e.getMessage(), e);
    }
    return outcome(approval);
  }

  private Outcome notFulfillableWithin(
      UUID approvalId, CustomerApproval.Decider decider, Authority authority, String detail) {
    Instant now = clock.instant();
    Decision decision = load(approvalId);
    CustomerApproval approval = decision.approval();
    if (approval.getStatus().isDecidedByCustomer()) {
      return outcome(approval);
    }
    approval.requireOpen(now);
    approvers.holdValid(approval, now);
    authority.check(approval, now);
    requireAwaiting(decision.order());
    return notFulfillable(approval, decision.order(), decider, detail, now);
  }

  /**
   * The customer approved, but the re-check found the terms can no longer be met: the approval is
   * kept, the order is not confirmed, and planning evaluates it again.
   */
  private Outcome notFulfillable(
      CustomerApproval approval,
      SalesOrder order,
      CustomerApproval.Decider decider,
      String detail,
      Instant now) {
    approval.approvedNotFulfillable(decider, detail, now);
    approvals.save(approval);
    order.startNewEvaluation();
    flow.move(
        order,
        OrderFlowStage.IN_PLANNING,
        OrderFlowRecorder.clip(
            "The customer approved version "
                + approval.getVersionNo()
                + " ("
                + who(decider)
                + "), but its terms can no longer be met: "
                + detail),
        actorOf(decider));
    return outcome(approval);
  }

  /** The request and its order, the order locked and the request read after the lock. */
  private Decision load(UUID approvalId) {
    UUID tenantId = TenantContext.requireTenantId();
    CustomerApproval approval =
        approvals
            .findByTenantIdAndId(tenantId, approvalId)
            .orElseThrow(() -> new NotFoundException("Approval request not found"));
    SalesOrder order =
        orders
            .lockByTenantIdAndId(tenantId, approval.getSalesOrderId())
            .orElseThrow(() -> new NotFoundException("Sales order not found"));
    // Another decision may have committed while this one waited for the lock.
    entityManager.refresh(approval);
    return new Decision(approval, order);
  }

  /**
   * Only the e-mailed link decides for now. A customer account would let whoever is signed in for
   * the customer decide under the request's authority, without proving to be its representative;
   * that waits until the portal matches the person to the authority (ADR-0014 OD-13).
   */
  private static void requireLinkChannel(CustomerApproval.Decider decider) {
    if (decider == null || decider.channel() != CustomerApprovalChannel.EMAIL_LINK) {
      throw OrderDomainException.stage(
          ACCOUNT_CHANNEL_NOT_AVAILABLE,
          "Decisions from a customer account are not accepted yet; the representative decides"
              + " through the e-mailed link");
    }
  }

  private static void requireAwaiting(SalesOrder order) {
    if (order.getFlowStage() != OrderFlowStage.AWAITING_CUSTOMER_APPROVAL
        || order.getStatus() != OrderStatus.DRAFT
        || !Boolean.TRUE.equals(order.getIsActive())) {
      throw OrderDomainException.stage(
          "APPROVAL_CLOSED",
          "Order " + order.getOrderNumber() + " is no longer waiting for this approval");
    }
  }

  private OrderVersion versionOf(CustomerApproval approval) {
    return versions
        .findByTenantIdAndId(TenantContext.requireTenantId(), approval.getOrderVersionId())
        .orElseThrow(() -> new IllegalStateException("The approval's version is missing"));
  }

  private static UUID actorOf(CustomerApproval.Decider decider) {
    return decider.userId() == null ? SystemUser.ID : decider.userId();
  }

  /** Who agreed, as the commitment keeps it. */
  private static String contactOf(CustomerApproval.Decider decider) {
    String value = who(decider);
    return value.length()
            <= com.fabricmanagement.sales.salesorder.domain.DeliveryCommitment.MAX_CONTACT_LENGTH
        ? value
        : value.substring(
            0, com.fabricmanagement.sales.salesorder.domain.DeliveryCommitment.MAX_CONTACT_LENGTH);
  }

  /** "Jane Smith <jane@example.com> via the e-mailed link" */
  private static String who(CustomerApproval.Decider decider) {
    String name = decider.name() == null || decider.name().isBlank() ? null : decider.name();
    String email = decider.email();
    String identity =
        name == null ? String.valueOf(email) : email == null ? name : name + " <" + email + ">";
    return identity
        + (decider.channel() == CustomerApprovalChannel.CUSTOMER_ACCOUNT
            ? " from their customer account"
            : " via the e-mailed link");
  }

  private static Outcome outcome(CustomerApproval approval) {
    return new Outcome(approval.getId(), approval.getSalesOrderId(), approval.getStatus());
  }

  private record Decision(CustomerApproval approval, SalesOrder order) {}

  /** Confirming the approved order failed; its transaction is rolled back. */
  static final class NotFulfillable extends RuntimeException {
    NotFulfillable(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
