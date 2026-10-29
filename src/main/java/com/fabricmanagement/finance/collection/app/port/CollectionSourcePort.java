package com.fabricmanagement.finance.collection.app.port;

import com.fabricmanagement.finance.collection.domain.CollectionTypes.Kind;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Finance owns this narrow agreement query. No sales entity, repository or free-text
 * interpretation.
 *
 * <p><b>Lock order.</b> The finance tenant lock is always taken first, the order row second, and
 * never the other way round. {@link #find} holds a <em>share</em> lock on the order for the rest of
 * the caller's transaction so the plan it reads cannot change under it, and waits for a writer of
 * the order only for a bounded time: it holds the tenant-wide finance lock while it waits, so an
 * unbounded wait would stall every finance command of the tenant behind one order edit. Sales code
 * must therefore never call anything that takes the finance lock while it holds an order lock
 * (ArchUnit: {@code FinanceLockOrderArchTest}).
 */
public interface CollectionSourcePort {
  /**
   * The accepted agreement of the order, read consistently.
   *
   * @throws SourceBusyException when a writer of the order held it longer than finance may wait;
   *     nothing was read or changed and the caller may retry
   */
  Optional<Agreement> find(UUID tenantId, UUID orderId);

  /**
   * Share-locks the order for the rest of the caller's transaction <em>without waiting</em>.
   *
   * @return {@code true} when the lock is held (or there is no such order, so there is nothing to
   *     lock); {@code false} when a writer holds the order right now. Nothing is thrown and the
   *     transaction stays usable, so a reader can fall back to what finance last recorded. After a
   *     {@code true}, {@link #find} takes its lock instantly.
   */
  boolean tryHold(UUID tenantId, UUID orderId);

  /**
   * What the source looks like from the outside, for every order with an agreed plan, in order-id
   * order: a few numbers per order and nothing else, so a caller can tell which orders changed
   * since it last looked without reading any of them. The stamp moves whenever the plan or the
   * order (its lines included) moves, because both carry an optimistic-lock version.
   *
   * <p>Reading it takes no lock: it only says <em>whether</em> to look, and {@link #find} is what
   * reads. A change racing with the listing is seen on the next run.
   */
  List<SourceStamp> stamps(UUID tenantId, UUID afterOrderId, int limit);

  /** The cheap, lock-free fingerprint of one agreed order; see {@link #stamps}. */
  record SourceStamp(
      UUID orderId, long orderVersion, long planVersion, UUID revisionId, boolean cancelled) {}

  /**
   * The accepted agreement as the source shows it. {@code cancelledAt} is null while the agreement
   * is live; {@code orderVersion} is the version of the order the plan was read from.
   */
  record Agreement(
      UUID orderId,
      UUID partnerId,
      UUID planId,
      UUID revisionId,
      long planVersion,
      UUID approvalId,
      Instant agreedAt,
      Instant activatedAt,
      boolean cancelled,
      boolean stale,
      List<Expectation> rows,
      Instant cancelledAt,
      long orderVersion) {
    public Agreement {
      rows = List.copyOf(rows);
    }
  }

  /** The order is being changed by someone else right now; try again shortly. */
  final class SourceBusyException extends RuntimeException {
    public SourceBusyException(UUID orderId) {
      super("The source order " + orderId + " is being changed");
    }
  }

  record Expectation(
      UUID logicalId,
      String currency,
      BigDecimal amount,
      Kind kind,
      Method method,
      LocalDate expectedDate,
      boolean awaitsConfirmationDate,
      LocalDate chequePaymentDate) {}
}
