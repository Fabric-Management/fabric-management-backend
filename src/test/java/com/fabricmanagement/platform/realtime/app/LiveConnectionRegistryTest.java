package com.fabricmanagement.platform.realtime.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.platform.realtime.app.LiveTestDoubles.FakeChannel;
import com.fabricmanagement.platform.realtime.app.LiveTestDoubles.FakeSource;
import com.fabricmanagement.platform.realtime.domain.LiveActor;
import com.fabricmanagement.platform.realtime.domain.LiveResource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Capacity of one instance (CEDIT-05 §5, L16–L17): atomic, per user/tenant/instance, returned once.
 */
class LiveConnectionRegistryTest {

  private final UUID tenant = UUID.randomUUID();
  private final UUID otherTenant = UUID.randomUUID();
  private final UUID user = UUID.randomUUID();
  private final UUID otherUser = UUID.randomUUID();

  private LiveStreamProperties properties;
  private LiveConnectionRegistry registry;

  @BeforeEach
  void setUp() {
    properties = new LiveStreamProperties();
    properties.setMaxConnectionsPerInstance(5);
    properties.setMaxConnectionsPerTenantPerInstance(3);
    properties.setMaxConnectionsPerUserPerTenantPerInstance(2);
    registry = new LiveConnectionRegistry(properties);
  }

  @Test
  @DisplayName("L17: per user, per tenant and per instance limits each refuse on their own")
  void eachLimitRefusesOnItsOwn() {
    assertThat(registry.reserve(tenant, user)).isPresent();
    assertThat(registry.reserve(tenant, user)).isPresent();
    assertThat(registry.reserve(tenant, user)).as("third tab of one user").isEmpty();

    assertThat(registry.reserve(tenant, otherUser)).isPresent();
    assertThat(registry.reserve(tenant, UUID.randomUUID())).as("fourth in one tenant").isEmpty();

    assertThat(registry.reserve(otherTenant, user)).isPresent();
    assertThat(registry.reserve(otherTenant, otherUser)).isPresent();
    assertThat(registry.reserve(UUID.randomUUID(), UUID.randomUUID()))
        .as("sixth on the instance")
        .isEmpty();
    assertThat(registry.reservedCount()).isEqualTo(5);
  }

  @Test
  @DisplayName("L16: a slot is given back exactly once, whoever releases it")
  void releaseIsIdempotent() {
    LiveConnectionRegistry.Slot slot = registry.reserve(tenant, user).orElseThrow();
    registry.reserve(tenant, user).orElseThrow();

    slot.release();
    slot.release();

    assertThat(slot.isReleased()).isTrue();
    assertThat(registry.reservedCount()).isEqualTo(1);
    assertThat(registry.reserve(tenant, user)).isPresent();
    assertThat(registry.reserve(tenant, user)).isEmpty();
  }

  @Test
  @DisplayName("L17: concurrent openings never exceed a limit; concurrent releases never go below")
  void concurrentReservationsStayWithinLimits() throws Exception {
    properties.setMaxConnectionsPerInstance(200);
    properties.setMaxConnectionsPerTenantPerInstance(200);
    properties.setMaxConnectionsPerUserPerTenantPerInstance(7);
    ExecutorService pool = Executors.newFixedThreadPool(16);
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Optional<LiveConnectionRegistry.Slot>>> attempts = new ArrayList<>();
      for (int i = 0; i < 64; i++) {
        attempts.add(
            pool.submit(
                () -> {
                  start.await();
                  return registry.reserve(tenant, user);
                }));
      }
      start.countDown();
      List<LiveConnectionRegistry.Slot> granted = new ArrayList<>();
      for (Future<Optional<LiveConnectionRegistry.Slot>> attempt : attempts) {
        attempt.get(10, TimeUnit.SECONDS).ifPresent(granted::add);
      }
      assertThat(granted).hasSize(7);
      assertThat(registry.reservedCount()).isEqualTo(7);

      CountDownLatch release = new CountDownLatch(1);
      List<Future<?>> releases = new ArrayList<>();
      for (LiveConnectionRegistry.Slot slot : granted) {
        for (int twice = 0; twice < 2; twice++) {
          releases.add(
              pool.submit(
                  () -> {
                    release.await();
                    slot.release();
                    return null;
                  }));
        }
      }
      release.countDown();
      for (Future<?> done : releases) {
        done.get(10, TimeUnit.SECONDS);
      }
      assertThat(registry.reservedCount()).isZero();
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName("L16: removing an earlier connection never removes a later one")
  void removalIsBoundToTheConnection() {
    LiveConnection earlier = connection(UUID.randomUUID());
    LiveConnection later = connection(earlier.id());

    registry.register(later);

    assertThat(registry.remove(earlier)).isFalse();
    assertThat(registry.size()).isEqualTo(1);
    assertThat(registry.remove(later)).isTrue();
    assertThat(registry.size()).isZero();
  }

  private LiveConnection connection(UUID id) {
    LiveActor actor = new LiveActor(tenant, user, Instant.now().plusSeconds(600));
    return new LiveConnection(
        id,
        actor,
        new LiveResource("test-resource", UUID.randomUUID()),
        new FakeSource(),
        new FakeChannel(),
        registry.reserve(tenant, user).orElseThrow(),
        Instant.now().plusSeconds(60));
  }
}
