package com.fabricmanagement.finance.collection.app.port;

import com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerType;
import java.util.List;
import java.util.UUID;

/**
 * Finance's narrow view of a customer's cheque drawers (PAYMENT-PLAN-7 R15). The list belongs to
 * the trading-partner module; finance reads and extends it only through this port, in the current
 * tenant. A partner that is not this tenant's is not found.
 */
public interface CollectionChequeDrawerPort {

  /** The customer's display name, its default own-cheque share and its active drawers. */
  Customer customer(UUID partnerId);

  /** Adds a drawer; an existing name comes back instead of being added twice. */
  Added add(UUID partnerId, String name, DrawerType type);

  /** Returns the same deactivated row to the picker. */
  Drawer reactivate(UUID partnerId, UUID drawerId);

  /**
   * Where the partner stands as a customer of this tenant (PAYMENT-PLAN-9 C1): its type, then
   * whether it was removed. An inactive status is not removal; a customer on hold may still pay.
   */
  CustomerStanding standing(UUID partnerId);

  /** The customer's current display name; empty when the partner is not this tenant's. */
  java.util.Optional<String> customerName(UUID partnerId);

  /** One drawer of this customer, active or not; empty when it is not this customer's. */
  java.util.Optional<Drawer> drawer(UUID partnerId, UUID drawerId);

  record Drawer(UUID id, String name, DrawerType type, boolean active) {}

  enum CustomerStanding {
    /** Not a partner of this tenant. */
    NOT_FOUND,
    /** A partner whose type is neither customer nor both (a supplier, for example). */
    NOT_CUSTOMER,
    /** A customer that was removed: its history stays readable, it takes no new receipts. */
    REMOVED,
    /** A customer (type customer or both) that was not removed. */
    CUSTOMER
  }

  /**
   * @param defaultOwnChequePercent the customer's default, 100 when never set
   */
  record Customer(String name, int defaultOwnChequePercent, List<Drawer> activeDrawers) {}

  enum AddOutcome {
    CREATED,
    EXISTING,
    INACTIVE_DUPLICATE
  }

  record Added(AddOutcome outcome, Drawer drawer) {}
}
