package com.fabricmanagement.flowboard.routing.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Configuration header: business revision and inherited JPA optimistic version are distinct. */
@Entity
@Table(
    schema = "flowboard",
    name = "routing_pool",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "pool_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoutingPool extends BaseEntity {
  @Enumerated(EnumType.STRING)
  @Column(name = "pool_key", nullable = false, updatable = false, length = 30)
  private RoutingPoolKey poolKey;

  @Column(nullable = false)
  private long revision;

  public static RoutingPool create(RoutingPoolKey key) {
    var pool = new RoutingPool();
    pool.poolKey = key;
    pool.revision = 1;
    return pool;
  }

  public void advanceRevision() {
    revision++;
  }

  @Override
  protected String getModuleCode() {
    return "RPOOL";
  }
}
