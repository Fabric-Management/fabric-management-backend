package com.fabricmanagement.logistics.shipment.infra.repository;

import com.fabricmanagement.logistics.shipment.domain.ShipmentLine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShipmentLineRepository extends JpaRepository<ShipmentLine, UUID> {
  Optional<ShipmentLine> findByIdAndTenantId(UUID id, UUID tenantId);

  List<ShipmentLine> findAllByShipmentIdAndTenantId(UUID shipmentId, UUID tenantId);
}
