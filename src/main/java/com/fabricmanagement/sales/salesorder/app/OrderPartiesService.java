package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.ApprovalAuthority;
import com.fabricmanagement.sales.salesorder.domain.BillTo;
import com.fabricmanagement.sales.salesorder.domain.PartyReference;
import com.fabricmanagement.sales.salesorder.domain.RequestedDate;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.OrderPartiesView;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.SetApproverRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.SetBillToRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.SetReleasePolicyRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.SetRequestedDateRequest;
import com.fabricmanagement.sales.salesorder.infra.repository.ApprovalAuthorityRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The order's parties and order-level defaults, written section by section (ADR-0014 D4, D7, D9,
 * D10): who is invoiced, the order-level requested date, whether production is released together,
 * and who approves for the customer.
 */
@Service
@RequiredArgsConstructor
public class OrderPartiesService {

  private final OrderDraftAccess access;
  private final ApprovalAuthorityRepository authorities;
  private final Clock clock;

  @Transactional(readOnly = true)
  public OrderPartiesView read(UUID orderId, UUID actor) {
    return OrderPartiesView.of(access.readable(orderId, actor));
  }

  @Transactional
  public OrderPartiesView setBillTo(UUID orderId, SetBillToRequest request, UUID actor) {
    SalesOrder order = access.writable(orderId, request.expectedVersion(), actor);
    PartyReference party =
        request.party() == null
            ? PartyReference.NONE
            : request.party().toReference(order.getTradingPartnerId(), "bill-to party");
    access.requireRegistered(party);
    order.applyBillTo(
        BillTo.of(
            party,
            request.address() == null ? null : request.address().toSnapshot(),
            request.relationship(),
            request.reason()));
    return saved(order);
  }

  @Transactional
  public OrderPartiesView setRequestedDate(
      UUID orderId, SetRequestedDateRequest request, UUID actor) {
    SalesOrder order = access.writable(orderId, request.expectedVersion(), actor);
    order.applyRequestedDate(
        request.requestedDate() == null
            ? RequestedDate.UNKNOWN
            : request.requestedDate().toRequestedDate());
    return saved(order);
  }

  @Transactional
  public OrderPartiesView setReleasePolicy(
      UUID orderId, SetReleasePolicyRequest request, UUID actor) {
    SalesOrder order = access.writable(orderId, request.expectedVersion(), actor);
    order.applyReleaseTogether(request.releaseTogether());
    return saved(order);
  }

  /**
   * Designates the customer's approver. Only a contact of the order's customer with an approval
   * authority valid today qualifies; the authority is looked up, never assumed from the contact.
   */
  @Transactional
  public OrderPartiesView setApprover(UUID orderId, SetApproverRequest request, UUID actor) {
    SalesOrder order = access.writable(orderId, request.expectedVersion(), actor);
    if (request.contactId() == null) {
      order.clearApprover();
      return saved(order);
    }
    ApprovalAuthority authority =
        authorities
            .findByTenantIdAndTradingPartnerIdAndContactIdAndRevokedAtIsNull(
                TenantContext.requireTenantId(), order.getTradingPartnerId(), request.contactId())
            .orElseThrow(
                () ->
                    OrderDomainException.rule(
                        "APPROVAL_AUTHORITY_MISSING",
                        "This contact holds no approval authority for the customer"));
    order.designateApprover(authority, LocalDate.now(clock));
    return saved(order);
  }

  private OrderPartiesView saved(SalesOrder order) {
    access.flush();
    return OrderPartiesView.of(order);
  }
}
