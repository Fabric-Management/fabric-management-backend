package com.fabricmanagement.procurement.subcontract.infra.repository;

import com.fabricmanagement.procurement.subcontract.domain.SubcontractOrder;
import com.fabricmanagement.procurement.subcontract.domain.SubcontractOrderStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SubcontractOrderRepository
    extends JpaRepository<SubcontractOrder, UUID>, JpaSpecificationExecutor<SubcontractOrder> {

  java.util.Optional<SubcontractOrder> findByIdAndTenantIdAndIsActiveTrue(UUID id, UUID tenantId);

  List<SubcontractOrder> findByWorkOrderIdAndIsActiveTrue(UUID workOrderId);

  List<SubcontractOrder> findByTradingPartnerIdAndIsActiveTrue(UUID tradingPartnerId);

  List<SubcontractOrder> findByStatusAndIsActiveTrue(SubcontractOrderStatus status);

  boolean existsByScNumberAndIsActiveTrue(String scNumber);
}
