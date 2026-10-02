package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The order's version stands for its lines too. Order totals are derived from the lines and no
 * longer stored, so a change that touches only lines would leave the order row untouched and a
 * second save carrying the old version would still pass. Every path that changes lines calls {@link
 * #linesChanged}: the version moves at once (and a stale one fails here), and the row stays locked
 * until the transaction ends so concurrent line edits are serialised.
 */
@Component
@RequiredArgsConstructor
public class SalesOrderRevision {

  private final EntityManager entityManager;

  public void linesChanged(SalesOrder order) {
    entityManager.lock(order, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
  }
}
