package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.paymentplan.OrderPaymentPlanRevision;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPaymentPlanRevisionRepository
    extends JpaRepository<OrderPaymentPlanRevision, UUID> {
  Optional<OrderPaymentPlanRevision> findByTenantIdAndId(UUID tenant, UUID id);

  Optional<OrderPaymentPlanRevision> findByTenantIdAndPlanIdAndOperationId(
      UUID tenant, UUID plan, UUID operation);

  Page<OrderPaymentPlanRevision> findByTenantIdAndPlanId(UUID tenant, UUID plan, Pageable pageable);
}
