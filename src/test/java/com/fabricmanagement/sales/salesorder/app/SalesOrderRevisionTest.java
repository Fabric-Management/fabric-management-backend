package com.fabricmanagement.sales.salesorder.app;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;

class SalesOrderRevisionTest {

  @Test
  void aLineChangeMovesTheOrderVersionAtOnceAndHoldsTheRow() {
    EntityManager entityManager = mock(EntityManager.class);
    SalesOrder order = SalesOrder.builder().orderNumber("SO-1").build();

    new SalesOrderRevision(entityManager).linesChanged(order);

    verify(entityManager).lock(order, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
  }
}
