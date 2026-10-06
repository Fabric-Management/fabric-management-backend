package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.hibernate.engine.spi.EntityKey;
import org.springframework.stereotype.Component;

/**
 * The order's version stands for its lines too. Order totals are derived from the lines and no
 * longer stored, so a change that touches only lines would leave the order row untouched and a
 * second save carrying the old version would still pass. Every path that changes lines calls {@link
 * #linesChanged}: the version moves at once (and a stale one fails here), and the row stays locked
 * until the transaction ends so concurrent line edits are serialised.
 *
 * <p>Writers of the order's content take their locks in one order (CEDIT-02 §5.5): the order row
 * ({@link #lockFresh(SalesOrder)}), then any advisory line locks, then the line rows ({@link
 * #lockFreshLines}). A locked query does not refresh an entity already loaded in the persistence
 * context, so these refresh: decisions are made on what is current after the lock.
 */
@Component
@RequiredArgsConstructor
public class SalesOrderRevision {

  private final EntityManager entityManager;
  private final SalesOrderLineRepository lines;

  public void linesChanged(SalesOrder order) {
    entityManager.lock(order, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
  }

  /**
   * Locks the order row for this transaction and reloads the entity from it, so status, flow stage
   * and version are the committed ones even when the entity was read before the lock.
   */
  public void lockFresh(SalesOrder order) {
    entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
  }

  /** Locks one line row and reloads it; call it after the order row is locked. */
  public void lockFresh(SalesOrderLine line) {
    entityManager.refresh(line, LockModeType.PESSIMISTIC_WRITE);
  }

  /**
   * Locks the order's active line rows in id order and returns them current. Lines this persistence
   * context had already loaded before the lock are reloaded; lines loaded by the locked query are
   * current by construction. Call it after the order row is locked.
   */
  public List<SalesOrderLine> lockFreshLines(SalesOrder order) {
    Set<Object> loadedBefore = loadedLineIds();
    List<SalesOrderLine> locked =
        lines.lockAllForOrder(TenantContext.requireTenantId(), order.getId());
    locked.stream()
        .filter(line -> loadedBefore.contains(line.getId()))
        .forEach(entityManager::refresh);
    return locked;
  }

  /**
   * Moves the order version exactly once for a save that changed its content: flushes the writes,
   * and when the order row itself was not updated (only lines changed) increments it now. A header
   * change already moved the version in the flush and is not incremented again. Returns the version
   * the transaction will commit. Nothing may change the order row after this call.
   */
  public long advanceOnce(SalesOrder order, long versionBeforeWrites) {
    entityManager.flush();
    if (Objects.equals(order.getVersion(), versionBeforeWrites)) {
      linesChanged(order);
    }
    return order.getVersion();
  }

  private Set<Object> loadedLineIds() {
    Session session = entityManager.unwrap(Session.class);
    return session.getStatistics().getEntityKeys().stream()
        .filter(EntityKey.class::isInstance)
        .map(EntityKey.class::cast)
        .filter(key -> SalesOrderLine.class.getName().equals(key.getEntityName()))
        .map(EntityKey::getIdentifier)
        .collect(Collectors.toSet());
  }
}
