package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.DeliveryCommitment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliveryCommitmentRepository extends JpaRepository<DeliveryCommitment, UUID> {

  /** The order's commitment history, first promise first. */
  List<DeliveryCommitment> findByTenantIdAndSalesOrderIdOrderBySequenceAsc(
      UUID tenantId, UUID salesOrderId);

  /** The current agreed commitment: the latest record. */
  Optional<DeliveryCommitment> findFirstByTenantIdAndSalesOrderIdOrderBySequenceDesc(
      UUID tenantId, UUID salesOrderId);
}
