package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.paymentplan.OrderPaymentPlanSettlementAck;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderPaymentPlanSettlementAckRepository
    extends JpaRepository<OrderPaymentPlanSettlementAck, UUID> {

  boolean existsByTenantIdAndRevisionId(UUID tenantId, UUID revisionId);

  Optional<OrderPaymentPlanSettlementAck> findByTenantIdAndRevisionId(
      UUID tenantId, UUID revisionId);

  List<OrderPaymentPlanSettlementAck> findByTenantIdAndSalesOrderIdOrderByAcknowledgedAtDesc(
      UUID tenantId, UUID salesOrderId);
}
