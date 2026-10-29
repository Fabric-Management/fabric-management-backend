package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Real PostgreSQL: the lease is exclusive per tenant, never makes a second taker wait, and is given
 * back when it is closed.
 */
class CollectionReconciliationLeaseIT extends AbstractIntegrationTest {
  @Autowired CollectionReconciliationLease lease;

  @Test
  void aSecondTakerIsRefusedAtOnceWhileTheFirstHoldsTheLease() {
    UUID tenant = UUID.randomUUID();
    try (var held = lease.tryAcquire(tenant).orElseThrow()) {
      assertThat(held).isNotNull();
      assertThat(lease.tryAcquire(tenant)).isEmpty();
    }
  }

  @Test
  void closingTheLeaseGivesItBack() {
    UUID tenant = UUID.randomUUID();
    lease.tryAcquire(tenant).orElseThrow().close();
    try (var again = lease.tryAcquire(tenant).orElseThrow()) {
      assertThat(again).isNotNull();
    }
  }

  @Test
  void tenantsDoNotBlockEachOther() {
    try (var first = lease.tryAcquire(UUID.randomUUID()).orElseThrow();
        var second = lease.tryAcquire(UUID.randomUUID()).orElseThrow()) {
      assertThat(first).isNotSameAs(second);
    }
  }

  @Test
  void closingTwiceIsHarmless() {
    UUID tenant = UUID.randomUUID();
    var held = lease.tryAcquire(tenant).orElseThrow();
    held.close();
    held.close();
    try (var again = lease.tryAcquire(tenant).orElseThrow()) {
      assertThat(again).isNotNull();
    }
  }
}
