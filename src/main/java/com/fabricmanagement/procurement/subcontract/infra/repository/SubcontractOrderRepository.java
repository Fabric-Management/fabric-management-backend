package com.fabricmanagement.procurement.subcontract.infra.repository;

import com.fabricmanagement.procurement.subcontract.domain.SubcontractOrder;
import com.fabricmanagement.procurement.subcontract.domain.SubcontractOrderStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubcontractOrderRepository extends JpaRepository<SubcontractOrder, UUID> {

  List<SubcontractOrder> findByWorkOrderIdAndIsActiveTrue(UUID workOrderId);

  List<SubcontractOrder> findByTradingPartnerIdAndIsActiveTrue(UUID tradingPartnerId);

  List<SubcontractOrder> findByStatusAndIsActiveTrue(SubcontractOrderStatus status);

  boolean existsByScNumberAndIsActiveTrue(String scNumber);
}
