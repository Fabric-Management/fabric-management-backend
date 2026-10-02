package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.approval.ApprovalPort;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.domain.CustomerApproval;
import com.fabricmanagement.sales.salesorder.domain.CustomerApprovalStatus;
import com.fabricmanagement.sales.salesorder.infra.repository.CustomerApprovalRepository;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Takes back an order's open approval request when what it asked about no longer holds: sales
 * withdrew the order, planning reopened the evaluation, a planning input changed, or the order was
 * cancelled. The link stops working at once and a pending internal approval is cancelled, so an
 * approval of the earlier version never counts for the changed order.
 */
@Component
@RequiredArgsConstructor
public class OrderApprovalInvalidator {

  /** The approval policy's entity type for sales orders. */
  static final String APPROVAL_ENTITY = "SALES_ORDER";

  private static final EnumSet<CustomerApprovalStatus> OPEN =
      EnumSet.of(CustomerApprovalStatus.AWAITING_INTERNAL_APPROVAL, CustomerApprovalStatus.SENT);

  private final CustomerApprovalRepository approvals;
  private final ApprovalPort approvalPort;
  private final Clock clock;

  /** Withdraws the order's open request, if any, with the reason. */
  public void withdrawOpen(UUID orderId, String reason, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    List<CustomerApproval> open =
        approvals.findByTenantIdAndSalesOrderIdAndStatusIn(tenantId, orderId, OPEN);
    for (CustomerApproval approval : open) {
      if (approval.awaitsInternalApproval()) {
        approvalPort.cancelPending(tenantId, APPROVAL_ENTITY, orderId);
      }
      approval.withdraw(reason, actor, clock.instant());
    }
    approvals.saveAll(open);
  }

  /** Marks the customer's change requests on the order as followed up. */
  public void resolveChangeRequests(UUID orderId) {
    UUID tenantId = TenantContext.requireTenantId();
    List<CustomerApproval> requested =
        approvals.findByTenantIdAndSalesOrderIdAndStatusIn(
            tenantId, orderId, EnumSet.of(CustomerApprovalStatus.CHANGES_REQUESTED));
    requested.forEach(approval -> approval.changesResolved(clock.instant()));
    approvals.saveAll(requested);
  }
}
