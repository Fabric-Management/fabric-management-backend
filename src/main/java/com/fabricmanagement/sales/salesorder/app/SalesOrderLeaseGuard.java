package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService;
import com.fabricmanagement.platform.realtime.domain.LiveEditLease;
import com.fabricmanagement.platform.realtime.domain.LiveLeaseKey;
import com.fabricmanagement.platform.realtime.domain.LiveLeaseMode;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.OrderEditKey;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The lease policy of the order's writers other than the safe edit (CEDIT-07 §3.4). Each writer
 * calls it in its own transaction, right after it locked and reloaded the order row and before it
 * reads or writes lines: order row, then the per-order lease lock, then the line locks — the order
 * of every writer. While the writer's transaction lasts, no lease on the checked keys can be
 * granted.
 *
 * <ul>
 *   <li>A user command that changes a leasable key (requested date, quantity acceptance, product
 *       correction) is refused while any edit session holds an overlapping lease: another person's,
 *       and equally the same person's other tab. A user id is never proof of a lease. A lease on an
 *       unrelated key does not stop it.
 *   <li>The legacy full-replace update is no alternative edit path: with enforcement on it is
 *       refused outright; with enforcement off it is refused while any lease is held on the order.
 * </ul>
 *
 * A status or flow change that makes the order uneditable needs no check here: it moves the order's
 * edit epoch under the order lock, which voids every lease at once (a lease is not a veto on
 * process steps). A command that writes a field and also changes the status is a field writer.
 */
@Component
@RequiredArgsConstructor
public class SalesOrderLeaseGuard {

  static final String EDIT_LEASE_HELD = "EDIT_LEASE_HELD";
  static final String LEGACY_EDIT_DISABLED = "LEGACY_EDIT_DISABLED";

  private final LiveEditLeaseService leases;
  private final SalesOrderEditLeaseService leaseViews;

  /** Refuses when somebody holds a lease of a header key this writer changes. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void assertHeaderFree(SalesOrder order, OrderEditKey key, UUID actor) {
    assertFree(order, List.of(SalesOrderLeaseKeys.of(key, null)), actor);
  }

  /** Refuses when somebody holds a lease of this line key (or the whole line). */
  @Transactional(propagation = Propagation.MANDATORY)
  public void assertLineFieldFree(SalesOrder order, UUID lineId, OrderEditKey key, UUID actor) {
    assertFree(order, List.of(SalesOrderLeaseKeys.of(key, lineId)), actor);
  }

  /** Refuses when somebody holds any lease on these lines (a change of the whole line). */
  @Transactional(propagation = Propagation.MANDATORY)
  public void assertLinesFree(SalesOrder order, Collection<UUID> lineIds, UUID actor) {
    assertFree(order, lineIds.stream().map(SalesOrderLeaseKeys::wholeLine).toList(), actor);
  }

  /** The legacy full replace: refused with enforcement on, or while any lease is held. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void assertLegacyReplaceAllowed(SalesOrder order, UUID actor) {
    if (leases.mode(SalesOrderLiveRevisionSource.RESOURCE_TYPE) == LiveLeaseMode.ENFORCED) {
      throw OrderDomainException.conflict(
          LEGACY_EDIT_DISABLED,
          "This order is edited field by field now; reopen it and save your changes again");
    }
    refuseIfHeld(
        leases.heldAny(SalesOrderLiveRevisionSource.resource(order.getId()), order.getEditEpoch()),
        actor);
  }

  private void assertFree(SalesOrder order, Collection<LiveLeaseKey> keys, UUID actor) {
    refuseIfHeld(
        leases.heldOverlapping(
            SalesOrderLiveRevisionSource.resource(order.getId()), order.getEditEpoch(), keys),
        actor);
  }

  private void refuseIfHeld(List<LiveEditLease> held, UUID actor) {
    if (!held.isEmpty()) {
      throw OrderDomainException.conflict(
              EDIT_LEASE_HELD,
              "Someone is editing this in an open form; try again after they save or leave it")
          .withDetail("holders", leaseViews.holders(held, actor));
    }
  }
}
