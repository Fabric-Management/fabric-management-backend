package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.CommitmentChangeOrigin;
import com.fabricmanagement.sales.salesorder.domain.CommitmentChannel;
import com.fabricmanagement.sales.salesorder.domain.DeliveryCommitment;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerms;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.DeliveryCommitmentDtos.CommitmentHistory;
import com.fabricmanagement.sales.salesorder.dto.DeliveryCommitmentDtos.CommitmentView;
import com.fabricmanagement.sales.salesorder.dto.DeliveryCommitmentDtos.DeliveryTermOption;
import com.fabricmanagement.sales.salesorder.dto.DeliveryCommitmentDtos.RecordDeliveryCommitment;
import com.fabricmanagement.sales.salesorder.infra.repository.DeliveryCommitmentRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivery commitments agreed with the buyer: an append-only history per order. Recording one sets
 * the order's current committed date; nothing else writes that date, so a planning revision never
 * changes what was promised to the customer.
 */
@Service
@RequiredArgsConstructor
public class DeliveryCommitmentService {

  private final SalesOrderRepository orders;
  private final DeliveryCommitmentRepository commitments;
  private final SalesOrderAccessPolicy accessPolicy;
  private final Clock clock;

  /** Every Incoterms rule with the event its dates refer to. */
  public List<DeliveryTermOption> deliveryTerms() {
    return Arrays.stream(DeliveryTerm.values()).map(DeliveryTermOption::of).toList();
  }

  @Transactional(readOnly = true)
  public CommitmentHistory history(UUID orderId, UUID actor) {
    SalesOrder order = readable(orderId, actor);
    return view(order.getId(), historyOf(order.getId()));
  }

  /**
   * Records a commitment the customer accepted. It is called by the customer's approval of a sent
   * order version, which is the agreement evidence; a planner entering a date is never one.
   */
  @Transactional
  public CommitmentHistory record(UUID orderId, RecordDeliveryCommitment input, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrder order = readable(orderId, actor);
    if (!accessPolicy.canWrite(tenantId, actor, order)) {
      throw new AccessDeniedException("You do not have access to update this sales order.");
    }
    // Serialise recorders of one order: the history is a chain, never a fork.
    order =
        orders
            .lockByTenantIdAndId(tenantId, order.getId())
            .orElseThrow(() -> new NotFoundException("Sales order not found: " + orderId));
    if (order.getStatus().isTerminal()) {
      throw new OrderDomainException(
          "Order " + order.getOrderNumber() + " is " + order.getStatus() + ": no new commitment",
          409);
    }
    DeliveryCommitment previous =
        commitments
            .findFirstByTenantIdAndSalesOrderIdOrderBySequenceDesc(tenantId, order.getId())
            .orElse(null);
    UUID previousId = previous == null ? null : previous.getId();
    if (!Objects.equals(previousId, input.basedOnCommitmentId())) {
      throw new OrderDomainException(
          "The commitment was changed meanwhile; reload it and record the change again", 409);
    }
    boolean renegotiated =
        input.deliveryTerm() != null
            || (input.deliveryPlace() != null && !input.deliveryPlace().isBlank())
            || input.incotermsVersion() != null;
    DeliveryTerms terms =
        renegotiated
            ? DeliveryTerms.of(
                input.deliveryTerm(), input.deliveryPlace(), input.incotermsVersion())
            : order.getDeliveryTerms();
    DeliveryCommitment recorded =
        commitments.save(
            DeliveryCommitment.record(
                order.getId(),
                previous,
                new DeliveryCommitment.Agreement(
                    input.committedOn(),
                    terms,
                    input.origin(),
                    input.reason(),
                    input.customerContact(),
                    input.channel(),
                    input.agreedAt()),
                actor,
                clock.instant()));
    if (renegotiated) {
      order.applyDeliveryTerms(terms);
    }
    order.applyCommittedDate(recorded.getCommittedOn());
    List<DeliveryCommitment> history = new ArrayList<>(historyOf(order.getId()));
    if (history.stream().noneMatch(value -> value.getId().equals(recorded.getId()))) {
      history.add(recorded);
    }
    return view(order.getId(), history);
  }

  /**
   * Records the date the customer agreed by approving a sent version of {@code order} (locked by
   * the caller): the first promise, or a change to the current one when the date or term differs.
   * The approval is the agreement evidence: who approved, through which channel and when.
   */
  public DeliveryCommitment recordCustomerApproval(
      SalesOrder order,
      LocalDate committedOn,
      String customerContact,
      CommitmentChannel channel,
      int versionNo,
      UUID recordedBy,
      java.time.Instant agreedAt) {
    UUID tenantId = TenantContext.requireTenantId();
    DeliveryCommitment previous =
        commitments
            .findFirstByTenantIdAndSalesOrderIdOrderBySequenceDesc(tenantId, order.getId())
            .orElse(null);
    DeliveryTerms terms = order.getDeliveryTerms();
    if (previous != null
        && previous.getCommittedOn().equals(committedOn)
        && previous.termsOf().equals(terms)) {
      return previous;
    }
    DeliveryCommitment recorded =
        commitments.save(
            DeliveryCommitment.record(
                order.getId(),
                previous,
                new DeliveryCommitment.Agreement(
                    committedOn,
                    terms,
                    previous == null
                        ? CommitmentChangeOrigin.INITIAL
                        : CommitmentChangeOrigin.SELLER_REVISION,
                    previous == null ? null : "The customer approved order version " + versionNo,
                    customerContact,
                    channel,
                    agreedAt),
                recordedBy,
                clock.instant()));
    order.applyCommittedDate(recorded.getCommittedOn());
    return recorded;
  }

  /**
   * The delivery term an order's draft edit may set. Once a commitment exists the term is part of
   * the agreed promise: changing it is a renegotiation recorded as a new commitment.
   */
  public void assertTermsEditable(SalesOrder order, DeliveryTerms requested) {
    Optional<DeliveryCommitment> current =
        commitments.findFirstByTenantIdAndSalesOrderIdOrderBySequenceDesc(
            TenantContext.requireTenantId(), order.getId());
    if (current.isPresent() && !current.get().termsOf().equals(requested)) {
      throw new OrderDomainException(
          "The delivery term is part of the agreed commitment; record a new commitment to"
              + " change it",
          409);
    }
  }

  private List<DeliveryCommitment> historyOf(UUID orderId) {
    return commitments.findByTenantIdAndSalesOrderIdOrderBySequenceAsc(
        TenantContext.requireTenantId(), orderId);
  }

  private SalesOrder readable(UUID orderId, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    return orders
        .findByTenantIdAndId(tenantId, orderId)
        .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
        .filter(value -> accessPolicy.canRead(tenantId, actor, value))
        .orElseThrow(() -> new NotFoundException("Sales order not found: " + orderId));
  }

  static CommitmentHistory view(UUID orderId, List<DeliveryCommitment> history) {
    List<CommitmentView> views = new ArrayList<>();
    long buyer = 0;
    long seller = 0;
    DeliveryCommitment previous = null;
    for (DeliveryCommitment value : history) {
      Long shift =
          previous == null
              ? null
              : ChronoUnit.DAYS.between(previous.getCommittedOn(), value.getCommittedOn());
      if (shift != null && value.getOrigin() == CommitmentChangeOrigin.BUYER_REQUEST) {
        buyer += shift;
      }
      if (shift != null && value.getOrigin() == CommitmentChangeOrigin.SELLER_REVISION) {
        seller += shift;
      }
      views.add(toView(value, shift));
      previous = value;
    }
    CommitmentView initial = views.isEmpty() ? null : views.getFirst();
    CommitmentView current = views.isEmpty() ? null : views.getLast();
    boolean sameEvent =
        history.stream().map(DeliveryCommitment::getDeliveryEvent).distinct().count() <= 1;
    return new CommitmentHistory(
        orderId,
        current,
        initial,
        initial == null
            ? null
            : ChronoUnit.DAYS.between(initial.committedOn(), current.committedOn()),
        buyer,
        seller,
        sameEvent,
        List.copyOf(views));
  }

  private static CommitmentView toView(DeliveryCommitment value, Long shift) {
    return new CommitmentView(
        value.getId(),
        value.getSequence(),
        value.getCommittedOn(),
        value.getDeliveryTerm(),
        value.getDeliveryPlace(),
        value.getIncotermsVersion(),
        value.getDeliveryEvent(),
        value.getOrigin(),
        value.getReason(),
        value.getCustomerContact(),
        value.getChannel(),
        value.getAgreedAt(),
        shift,
        value.getRecordedBy(),
        value.getRecordedAt());
  }
}
