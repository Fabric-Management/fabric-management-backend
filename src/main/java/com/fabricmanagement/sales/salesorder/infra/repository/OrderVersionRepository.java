package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.OrderVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderVersionRepository extends JpaRepository<OrderVersion, UUID> {

  Optional<OrderVersion> findFirstByTenantIdAndSalesOrderIdOrderByVersionNoDesc(
      UUID tenantId, UUID salesOrderId);

  Optional<OrderVersion> findByTenantIdAndId(UUID tenantId, UUID id);

  List<OrderVersion> findByTenantIdAndSalesOrderIdOrderByVersionNoDesc(
      UUID tenantId, UUID salesOrderId);
}
