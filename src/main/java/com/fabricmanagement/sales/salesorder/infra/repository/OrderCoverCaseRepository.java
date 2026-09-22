package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.OrderCoverCase;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderCoverCaseRepository extends JpaRepository<OrderCoverCase, UUID> {
  Optional<OrderCoverCase> findByTenantIdAndSalesOrderId(UUID tenantId, UUID salesOrderId);

  Optional<OrderCoverCase> findByTenantIdAndId(UUID tenantId, UUID id);

  List<OrderCoverCase> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

  @Query(
      value =
          "select id from sales_ord.order_cover_case where tenant_id=:tenantId "
              + "and (cast(:afterId as uuid) is null or id > cast(:afterId as uuid)) "
              + "order by id limit :limit",
      nativeQuery = true)
  List<UUID> findIdsAfter(
      @Param("tenantId") UUID tenantId, @Param("afterId") UUID afterId, @Param("limit") int limit);

  Optional<OrderCoverCase> findByTenantIdAndTaskId(UUID tenantId, UUID taskId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from OrderCoverCase c where c.tenantId=:tenantId and c.id=:id")
  Optional<OrderCoverCase> lock(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from OrderCoverCase c where c.tenantId=:tenantId and c.salesOrderId=:orderId")
  Optional<OrderCoverCase> lockByOrder(
      @Param("tenantId") UUID tenantId, @Param("orderId") UUID orderId);
}
