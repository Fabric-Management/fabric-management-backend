package com.fabricmanagement.platform.realtime.app;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * This instance's live connections and the capacity they hold (CEDIT-05 §5). Capacity is reserved
 * atomically before a connection reads anything and is given back exactly once, whichever path ends
 * the connection. The limits are per instance: they are not a global quota and not single
 * ownership.
 */
@Component
public class LiveConnectionRegistry {

  private final LiveStreamProperties properties;
  private final Object capacityLock = new Object();
  private final Map<UUID, Integer> perTenant = new HashMap<>();
  private final Map<TenantUser, Integer> perUser = new HashMap<>();
  private final Map<UUID, LiveConnection> connections = new ConcurrentHashMap<>();
  private int reserved;

  public LiveConnectionRegistry(LiveStreamProperties properties) {
    this.properties = properties;
  }

  /** Reserves one slot for the user, or nothing when any of the three limits is reached. */
  public Optional<Slot> reserve(UUID tenantId, UUID userId) {
    TenantUser user = new TenantUser(tenantId, userId);
    synchronized (capacityLock) {
      if (reserved >= properties.getMaxConnectionsPerInstance()
          || perTenant.getOrDefault(tenantId, 0)
              >= properties.getMaxConnectionsPerTenantPerInstance()
          || perUser.getOrDefault(user, 0)
              >= properties.getMaxConnectionsPerUserPerTenantPerInstance()) {
        return Optional.empty();
      }
      reserved++;
      perTenant.merge(tenantId, 1, Integer::sum);
      perUser.merge(user, 1, Integer::sum);
      return Optional.of(new Slot(user));
    }
  }

  /** Slots currently held, open or still opening. */
  public int reservedCount() {
    synchronized (capacityLock) {
      return reserved;
    }
  }

  /** Connections currently registered (open, or opening after their first read). */
  public int size() {
    return connections.size();
  }

  void register(LiveConnection connection) {
    connections.put(connection.id(), connection);
  }

  /** Removes exactly this connection; a later connection is never removed by an earlier one. */
  boolean remove(LiveConnection connection) {
    return connections.remove(connection.id(), connection);
  }

  Collection<LiveConnection> snapshot() {
    return List.copyOf(connections.values());
  }

  private void giveBack(TenantUser user) {
    synchronized (capacityLock) {
      reserved--;
      perTenant.computeIfPresent(user.tenantId(), (key, count) -> count > 1 ? count - 1 : null);
      perUser.computeIfPresent(user, (key, count) -> count > 1 ? count - 1 : null);
    }
  }

  private record TenantUser(UUID tenantId, UUID userId) {}

  /** One reserved connection's capacity; giving it back is idempotent. */
  public final class Slot {

    private final TenantUser user;
    private final AtomicBoolean released = new AtomicBoolean();

    private Slot(TenantUser user) {
      this.user = user;
    }

    public void release() {
      if (released.compareAndSet(false, true)) {
        giveBack(user);
      }
    }

    public boolean isReleased() {
      return released.get();
    }
  }
}
