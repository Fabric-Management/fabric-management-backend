package com.fabricmanagement.common.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real transaction locking without the order-cover business fixture. */
class SalesOrderLineFulfilmentLockIT extends AbstractIntegrationTest {
  @Autowired SalesOrderLineFulfilmentLock fulfilmentLock;
  @Autowired PlatformTransactionManager transactions;

  @Test
  void lineFulfilmentLockSerializesCompetingWriters() throws Exception {
    UUID tenantId = UUID.randomUUID(), lineId = UUID.randomUUID();
    CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<Boolean> settlement =
          executor.submit(
              () ->
                  new TransactionTemplate(transactions)
                      .execute(
                          status -> {
                            fulfilmentLock.lock(tenantId, lineId);
                            held.countDown();
                            try {
                              if (!release.await(10, TimeUnit.SECONDS))
                                throw new AssertionError("release timeout");
                            } catch (InterruptedException error) {
                              Thread.currentThread().interrupt();
                              throw new AssertionError(error);
                            }
                            return true;
                          }));
      assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
      Future<Boolean> competing =
          executor.submit(
              () ->
                  new TransactionTemplate(transactions)
                      .execute(
                          status -> {
                            fulfilmentLock.lock(tenantId, lineId);
                            return true;
                          }));
      Thread.sleep(150);
      assertThat(competing).isNotDone();
      release.countDown();
      assertThat(settlement.get(10, TimeUnit.SECONDS)).isTrue();
      assertThat(competing.get(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      release.countDown();
    }
  }
}
