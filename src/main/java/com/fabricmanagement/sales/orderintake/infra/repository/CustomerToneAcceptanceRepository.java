package com.fabricmanagement.sales.orderintake.infra.repository;

import com.fabricmanagement.sales.orderintake.domain.CustomerToneAcceptance;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerToneAcceptanceRepository
    extends JpaRepository<CustomerToneAcceptance, UUID> {

  List<CustomerToneAcceptance> findByTenantIdAndCustomerIdAndIsActiveTrue(
      UUID tenantId, UUID customerId);
}
