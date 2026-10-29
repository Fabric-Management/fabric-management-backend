package com.fabricmanagement.finance.collection.infra.repository;

import com.fabricmanagement.finance.collection.domain.CollectionProjection;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectionProjectionRepository extends JpaRepository<CollectionProjection, UUID> {
  Optional<CollectionProjection> findByTenantIdAndOrderId(UUID tenant, UUID order);

  List<CollectionProjection> findByTenantIdAndOrderIdIn(UUID tenant, List<UUID> orders);

  /**
   * Records that these orders were found exactly as projected when the source was listed at {@code
   * at}. Moves the check time forward only and changes nothing else (not even the row version), so
   * a rebuild running at the same moment neither fails nor has its newer check time overwritten.
   */
  @Modifying
  @Query(
      "update CollectionProjection p set p.checkedAt = :at where p.tenantId = :tenant"
          + " and p.orderId in :orders and (p.checkedAt is null or p.checkedAt < :at)")
  int confirm(
      @Param("tenant") UUID tenant,
      @Param("orders") Collection<UUID> orders,
      @Param("at") Instant at);

  /** The projections whose order id lies in {@code (after, upTo]}, in order-id order. */
  List<CollectionProjection>
      findByTenantIdAndOrderIdGreaterThanAndOrderIdLessThanEqualOrderByOrderId(
          UUID tenant, UUID after, UUID upTo);

  List<CollectionProjection> findByTenantIdAndOrderIdGreaterThanOrderByOrderId(
      UUID tenant, UUID after, org.springframework.data.domain.Pageable page);
}
