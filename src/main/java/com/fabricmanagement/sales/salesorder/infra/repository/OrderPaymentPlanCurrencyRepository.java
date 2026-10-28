package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.paymentplan.OrderPaymentPlanCurrency;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPaymentPlanCurrencyRepository
    extends JpaRepository<OrderPaymentPlanCurrency, UUID> {
  List<OrderPaymentPlanCurrency> findByTenantIdAndRevisionIdInOrderByCurrencyAsc(
      UUID tenant, List<UUID> revisions);

  List<OrderPaymentPlanCurrency> findByTenantIdAndRevisionIdOrderByCurrencyAsc(
      UUID tenant, UUID revision);
}
