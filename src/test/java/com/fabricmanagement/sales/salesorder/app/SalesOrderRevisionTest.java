package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SalesOrderRevisionTest {

  private final EntityManager entityManager = mock(EntityManager.class);
  private final SalesOrderRevision revision =
      new SalesOrderRevision(entityManager, mock(SalesOrderLineRepository.class));

  @Test
  void aLineChangeMovesTheOrderVersionAtOnceAndHoldsTheRow() {
    SalesOrder order = SalesOrder.builder().orderNumber("SO-1").build();

    revision.linesChanged(order);

    verify(entityManager).lock(order, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
  }

  @Test
  void theOrderRowIsLockedAndReloadedTogether() {
    SalesOrder order = SalesOrder.builder().orderNumber("SO-1").build();

    revision.lockFresh(order);

    verify(entityManager).refresh(order, LockModeType.PESSIMISTIC_WRITE);
  }

  @Test
  void aHeaderChangeThatAlreadyMovedTheVersionIsNotIncrementedAgain() {
    SalesOrder order = SalesOrder.builder().orderNumber("SO-1").build();
    ReflectionTestUtils.setField(order, "version", 7L);
    // The flush writes the dirty order row and moves its version.
    doAnswer(
            invocation -> {
              ReflectionTestUtils.setField(order, "version", 8L);
              return null;
            })
        .when(entityManager)
        .flush();

    long result = revision.advanceOnce(order, 7L);

    assertThat(result).isEqualTo(8L);
    verify(entityManager, never()).lock(any(), any(LockModeType.class));
  }

  @Test
  void aLineOnlyChangeMovesTheVersionOnceAfterTheFlush() {
    SalesOrder order = SalesOrder.builder().orderNumber("SO-1").build();
    ReflectionTestUtils.setField(order, "version", 7L);
    doAnswer(
            invocation -> {
              ReflectionTestUtils.setField(order, "version", 8L);
              return null;
            })
        .when(entityManager)
        .lock(order, LockModeType.PESSIMISTIC_FORCE_INCREMENT);

    long result = revision.advanceOnce(order, 7L);

    assertThat(result).isEqualTo(8L);
    verify(entityManager).flush();
    verify(entityManager).lock(order, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
  }
}
