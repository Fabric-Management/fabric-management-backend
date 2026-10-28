package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.paymentplan.OrderPaymentPlanLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPaymentPlanLineRepository extends JpaRepository<OrderPaymentPlanLine, UUID> {
  List<OrderPaymentPlanLine> findByTenantIdAndCurrencyGroupIdInOrderByOrdinalAsc(
      UUID tenant, List<UUID> groups);

  List<OrderPaymentPlanLine> findByTenantIdAndCurrencyGroupIdOrderByOrdinalAsc(
      UUID tenant, UUID group);
}
