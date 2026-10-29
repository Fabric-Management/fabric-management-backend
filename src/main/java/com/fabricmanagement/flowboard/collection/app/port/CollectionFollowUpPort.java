package com.fabricmanagement.flowboard.collection.app.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Consumer-owned boundary: FlowBoard never writes a payment or reads finance repositories. */
public interface CollectionFollowUpPort {
  record ReadScope(boolean all, java.util.Set<UUID> principals) {}

  /** Missing/failed finance source discovery cannot certify an empty workflow queue. */
  boolean upstreamStale();

  ReadScope readScope(UUID actor);

  String partnerName(UUID partnerId);

  void lock();

  Facts current(UUID obligationId);

  /**
   * The obligations that are open and due, in id order. Closed ones are left out on purpose: the
   * scan repairs what events missed, a closed obligation has nothing left to do, and the closing of
   * an open case is found through {@link #closed}. The scan therefore costs what is open, not
   * everything the tenant has ever agreed.
   */
  List<UUID> discover(UUID after, int limit);

  /** Which of these obligations the ledger has closed: settled in full, or cancelled. */
  Set<UUID> closed(List<UUID> obligationIds);

  boolean mayRead(UUID actor, UUID obligationId);

  boolean eligible(UUID actor, UUID obligationId);

  void assign(UUID obligationId, UUID owner);

  LocalDate today();

  LocalDate dayOf(java.time.Instant occurredAt);

  record Facts(
      UUID obligationId,
      long version,
      UUID partnerId,
      String currency,
      BigDecimal expected,
      BigDecimal outstanding,
      BigDecimal missingInstrument,
      LocalDate agreedDate,
      LocalDate chequeDueDate,
      boolean cheque,
      boolean cancelled,
      boolean sourceMissing,
      boolean stale,
      UUID owner) {
    public boolean closed() {
      return !sourceMissing && (cancelled || outstanding.signum() == 0);
    }

    public String phase() {
      return !cheque
          ? "COLLECT_PAYMENT"
          : missingInstrument.signum() > 0 ? "RECEIVE_CHEQUE" : "CLEAR_CHEQUE";
    }

    public LocalDate dueDate() {
      return cheque && missingInstrument.signum() == 0 ? chequeDueDate : agreedDate;
    }
  }
}
