package com.fabricmanagement.sales.salesorder.domain.port;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Sales-owned translation of sales read scope into a projection-safe predicate. */
public interface SalesOrderReadScopePort {
  OrderReadScope readScope(UUID tenantId, UUID callerId);

  record OrderReadScope(Kind kind, Set<UUID> principalIds) {
    public OrderReadScope {
      kind = Objects.requireNonNull(kind, "kind");
      principalIds = Set.copyOf(principalIds == null ? Set.of() : principalIds);
      if (kind != Kind.PRINCIPALS && !principalIds.isEmpty()) {
        throw new IllegalArgumentException("Only PRINCIPALS scope may carry principal ids");
      }
    }

    public static OrderReadScope none() {
      return new OrderReadScope(Kind.NONE, Set.of());
    }

    public static OrderReadScope all() {
      return new OrderReadScope(Kind.ALL, Set.of());
    }

    public static OrderReadScope principals(Set<UUID> principalIds) {
      return principalIds == null || principalIds.isEmpty()
          ? none()
          : new OrderReadScope(Kind.PRINCIPALS, principalIds);
    }
  }

  enum Kind {
    NONE,
    ALL,
    PRINCIPALS
  }
}
