package com.fabricmanagement.sales.orderintake.infra.repository;

import com.fabricmanagement.sales.orderintake.domain.OrderDeliveryPreference;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderDeliveryPreferenceRepository
    extends JpaRepository<OrderDeliveryPreference, UUID> {

  Optional<OrderDeliveryPreference> findByTenantIdAndSalesOrderId(UUID tenantId, UUID salesOrderId);
}
